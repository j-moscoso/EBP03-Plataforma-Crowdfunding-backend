package com.ebp03.plataforma_crowdfunding_backend.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.containsString;

import jakarta.servlet.http.Cookie;
import java.sql.Timestamp;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserProfileControllerIntegrationTests {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void currentUserIsLoadedFromAuthenticatedSessionAndDoesNotExposeSecrets() throws Exception {
        Cookie session = register("profile-get@test.local", "creator");

        mockMvc.perform(get("/api/auth/me").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Nombre actual"))
                .andExpect(jsonPath("$.email").value("profile-get@test.local"))
                .andExpect(jsonPath("$.role").value("creator"))
                .andExpect(jsonPath("$.verificationStatus").value("pending"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.tokenHash").doesNotExist())
                .andExpect(jsonPath("$.status").doesNotExist())
                .andExpect(jsonPath("$.sessions").doesNotExist());
    }

    @Test
    void profileEndpointsRequireAUsableSession() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(patch("/api/auth/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Cambio no autorizado\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/auth/me").cookie(new Cookie("AUTH_SESSION", "invalid-token")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void creatorCanUpdateNameWithoutReplacingTheActiveSession() throws Exception {
        Cookie session = register("profile-creator@test.local", "creator");

        mockMvc.perform(patch("/api/auth/me")
                        .cookie(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"  Ana Pérez  \"}"))
                .andExpect(status().isOk())
                .andExpect(cookie().doesNotExist("AUTH_SESSION"))
                .andExpect(jsonPath("$.message").value("Datos actualizados"))
                .andExpect(jsonPath("$.user.name").value("Ana Pérez"))
                .andExpect(jsonPath("$.user.role").value("creator"))
                .andExpect(jsonPath("$.user.passwordHash").doesNotExist());

        mockMvc.perform(get("/api/auth/me").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Ana Pérez"));
    }

    @Test
    void corsPreflightAllowsProfilePatchForConfiguredOriginWithCredentials() throws Exception {
        mockMvc.perform(options("/api/auth/me")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "PATCH")
                        .header("Access-Control-Request-Headers", "content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"))
                .andExpect(header().string("Access-Control-Allow-Methods", containsString("PATCH")))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
    }

    @Test
    void sponsorCanUpdateName() throws Exception {
        Cookie session = register("profile-sponsor@test.local", "sponsor");

        mockMvc.perform(patch("/api/auth/me")
                        .cookie(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Patrocinador Visible\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Datos actualizados"))
                .andExpect(jsonPath("$.user.name").value("Patrocinador Visible"))
                .andExpect(jsonPath("$.user.role").value("sponsor"));
    }

    @Test
    void invalidNamesReportFieldErrorsAndDoNotChangeNameOrUpdatedAt() throws Exception {
        String email = "profile-invalid@test.local";
        Cookie session = register(email, "creator");
        UUID userId = UUID.fromString(mockMvc.perform(get("/api/auth/me").cookie(session))
                .andReturn().getResponse().getContentAsString().replaceAll(".*\"id\":\"([^\"]+)\".*", "$1"));
        Timestamp originalUpdatedAt = updatedAt(userId);

        for (String invalidName : new String[]{"", "   ", "x".repeat(121), "<script>alert(1)</script>", "Nombre" + (char) 1}) {
            MvcResult invalidResult = mockMvc.perform(patch("/api/auth/me")
                            .cookie(session)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(nameBody(invalidName)))
                    .andReturn();
            assertEquals(400, invalidResult.getResponse().getStatus(),
                    "Invalid name length " + invalidName.length() + ": " + invalidResult.getResponse().getContentAsString());
            assertTrue(invalidResult.getResponse().getContentAsString().contains("\"field\":\"name\""));
            assertEquals(originalUpdatedAt, updatedAt(userId));
            mockMvc.perform(get("/api/auth/me").cookie(session))
                    .andExpect(jsonPath("$.name").value("Nombre actual"));
        }
    }

    @Test
    void unknownProtectedFieldsAreRejectedWithoutModifyingAnyAccount() throws Exception {
        Cookie session = register("profile-protected@test.local", "creator");
        register("profile-other@test.local", "sponsor");

        mockMvc.perform(patch("/api/auth/me")
                        .cookie(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Intento\",\"userId\":\"00000000-0000-0000-0000-000000000000\",\"role\":\"sponsor\",\"status\":\"DISABLED\",\"email\":\"new@test.local\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details[0].field").value("userId"))
                .andExpect(jsonPath("$.details[0].code").value("UNKNOWN_FIELD"));

        mockMvc.perform(get("/api/auth/me").cookie(session))
                .andExpect(jsonPath("$.name").value("Nombre actual"))
                .andExpect(jsonPath("$.email").value("profile-protected@test.local"));
        assertEquals("Nombre actual", jdbcTemplate.queryForObject(
                "select name from users where email = ?", String.class, "profile-other@test.local"));
    }

    @Test
    void invalidNameTypesDoNotPersist() throws Exception {
        Cookie session = register("profile-type@test.local", "creator");
        for (String body : new String[]{"{\"name\":123}", "{\"name\":true}", "null"}) {
            mockMvc.perform(patch("/api/auth/me")
                            .cookie(session)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
            mockMvc.perform(get("/api/auth/me").cookie(session))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("Nombre actual"));
        }
    }

    @Test
    void concurrentValidUpdatesAreSerializedAsCompleteLastWrites() throws Exception {
        Cookie session = register("profile-concurrent@test.local", "creator");
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> sendConcurrentUpdate(session, "First Valid Name", start));
            var second = executor.submit(() -> sendConcurrentUpdate(session, "Second Valid Name", start));
            start.countDown();
            assertEquals(200, first.get().getResponse().getStatus());
            assertEquals(200, second.get().getResponse().getStatus());
        } finally {
            executor.shutdownNow();
        }

        String persistedName = jdbcTemplate.queryForObject(
                "select name from users where email = ?", String.class, "profile-concurrent@test.local");
        assertTrue(persistedName.equals("First Valid Name") || persistedName.equals("Second Valid Name"));
        mockMvc.perform(get("/api/auth/me").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(persistedName));
    }

    private MvcResult sendConcurrentUpdate(Cookie session, String name, CountDownLatch start) throws Exception {
        start.await();
        return mockMvc.perform(patch("/api/auth/me")
                        .cookie(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(nameBody(name)))
                .andReturn();
    }

    private String nameBody(String name) {
        String escaped = name.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace(Character.toString((char) 1), "\\u0001");
        return "{\"name\":\"" + escaped + "\"}";
    }

    private Cookie register(String email, String role) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Nombre actual\",\"email\":\"" + email
                                + "\",\"password\":\"Secure1!\",\"role\":\"" + role + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return result.getResponse().getCookie("AUTH_SESSION");
    }

    private Timestamp updatedAt(UUID userId) {
        return jdbcTemplate.queryForObject("select updated_at from users where id = ?", Timestamp.class, userId);
    }
}
