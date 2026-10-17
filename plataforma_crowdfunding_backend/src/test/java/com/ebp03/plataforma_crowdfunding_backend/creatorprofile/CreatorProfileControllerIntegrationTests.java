package com.ebp03.plataforma_crowdfunding_backend.creatorprofile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CreatorProfileControllerIntegrationTests {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void creatorCanLoadInitialProfileCreateUpdateAndReadEncryptedBankData() throws Exception {
        Cookie session = register("creator-profile@test.local", "creator");

        mockMvc.perform(get("/api/creators/me/profile").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").doesNotExist())
                .andExpect(jsonPath("$.userId").exists())
                .andExpect(jsonPath("$.displayName").value("Nombre del usuario"))
                .andExpect(jsonPath("$.links.length()").value(0));

        mockMvc.perform(put("/api/creators/me/profile")
                        .cookie(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody("Ana Pérez", "1234567890",
                                "[{\"linkType\":\"PROFESSIONAL_NETWORK\",\"label\":\"Red\",\"url\":\"https://social.example/ana\",\"sortOrder\":1},"
                                        + "{\"linkType\":\"PORTFOLIO\",\"label\":\"Portafolio\",\"url\":\"https://portfolio.example/ana\",\"sortOrder\":0}]")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Perfil guardado"))
                .andExpect(jsonPath("$.profile.displayName").value("Ana Pérez"))
                .andExpect(jsonPath("$.profile.bankAccountType").value("SAVINGS"))
                .andExpect(jsonPath("$.profile.bankAccountNumberMasked").value("******7890"))
                .andExpect(jsonPath("$.profile.bankAccountDocumentMasked").value("*******89"))
                .andExpect(jsonPath("$.profile.bankAccountNumber").doesNotExist())
                .andExpect(jsonPath("$.profile.bankAccountDocument").doesNotExist())
                .andExpect(jsonPath("$.profile.links.length()").value(2))
                .andExpect(jsonPath("$.profile.links[0].linkType").value("PORTFOLIO"))
                .andExpect(jsonPath("$.profile.links[0].sortOrder").value(0))
                .andExpect(jsonPath("$.profile.links[1].linkType").value("PROFESSIONAL_NETWORK"))
                .andExpect(jsonPath("$.profile.createdAt").exists())
                .andExpect(jsonPath("$.profile.updatedAt").exists());

        String persistedCiphertext = jdbcTemplate.queryForObject(
                "select bank_account_number from creator_profiles where user_id = "
                        + "(select id from users where email = ?)",
                String.class, "creator-profile@test.local");
        assertTrue(persistedCiphertext.startsWith("v1:"));
        assertNotEquals("1234567890", persistedCiphertext);

        mockMvc.perform(get("/api/creators/me/profile").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Ana Pérez"))
                .andExpect(jsonPath("$.bankAccountNumberMasked").value("******7890"))
                .andExpect(jsonPath("$.bankAccountNumber").doesNotExist())
                .andExpect(jsonPath("$.bankAccountDocument").doesNotExist())
                .andExpect(jsonPath("$.links[0].url").value("https://portfolio.example/ana"));

        mockMvc.perform(put("/api/creators/me/profile")
                        .cookie(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody("Ana Actualizada", "9876543210",
                                "[{\"linkType\":\"OTHER_PROJECT\",\"label\":\"Proyecto\",\"url\":\"https://project.example/ana\"}]")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Perfil guardado"))
                .andExpect(jsonPath("$.profile.displayName").value("Ana Actualizada"))
                .andExpect(jsonPath("$.profile.bankAccountNumberMasked").value("******3210"))
                .andExpect(jsonPath("$.profile.links.length()").value(1))
                .andExpect(jsonPath("$.profile.links[0].sortOrder").value(0));

        assertEquals(1, countLinks("creator-profile@test.local"));
    }

    @Test
    void sponsorAndAnonymousCannotReadOrWriteCreatorProfile() throws Exception {
        Cookie sponsor = register("sponsor-profile@test.local", "sponsor");

        mockMvc.perform(get("/api/creators/me/profile"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/creators/me/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody("Nombre", "1234567890", "[]")))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/creators/me/profile").cookie(sponsor))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CREATOR_ROLE_REQUIRED"));
        mockMvc.perform(put("/api/creators/me/profile")
                        .cookie(sponsor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody("Nombre", "1234567890", "[]")))
                .andExpect(status().isForbidden());
    }

    @Test
    void invalidRequiredFieldsAndBankFormatsDoNotPersistAnyRows() throws Exception {
        Cookie session = register("creator-invalid@test.local", "creator");
        String invalid = validBody("", "12ABC", "[{\"linkType\":\"PORTFOLIO\",\"url\":\"javascript:alert(1)\"}]")
                .replace("\"bankName\":\"Banco de Prueba\"", "\"bankName\":\"\"")
                .replace("\"bankAccountDocument\":\"123456789\"", "\"bankAccountDocument\":\"1234A6789\"")
                .replace("\"phone\":\"+573001112233\"", "\"phone\":\"3001112233\"");

        MvcResult result = mockMvc.perform(put("/api/creators/me/profile")
                        .cookie(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalid))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("Revisa los campos indicados."))
                .andExpect(jsonPath("$.details[?(@.field == 'profile.displayName')]").exists())
                .andExpect(jsonPath("$.details[?(@.field == 'profile.bankName')]").exists())
                .andExpect(jsonPath("$.details[?(@.field == 'profile.bankAccountNumber')]").exists())
                .andExpect(jsonPath("$.details[?(@.field == 'profile.bankAccountDocument')]").exists())
                .andExpect(jsonPath("$.details[?(@.field == 'profile.phone')]").exists())
                .andExpect(jsonPath("$.details[?(@.field == 'links[0].url' && @.code == 'INVALID_URL')]").exists())
                .andReturn();

        assertTrue(result.getResponse().getContentAsString().contains("\"field\":\"links[0].url\""));
        assertEquals(0, count("creator_profiles", "creator-invalid@test.local"));
        assertEquals(0, countLinks("creator-invalid@test.local"));
    }

    @Test
    void invalidLinksAndProtectedOwnerFieldsAreRejectedWithoutPartialUpdates() throws Exception {
        Cookie session = register("creator-links@test.local", "creator");
        save(session, validBody("Perfil Inicial", "1234567890",
                "[{\"linkType\":\"PORTFOLIO\",\"url\":\"https://portfolio.example\"}]"));
        String originalUrl = jdbcTemplate.queryForObject(
                "select url from creator_profile_links where creator_profile_id = "
                        + "(select id from creator_profiles where user_id = "
                        + "(select id from users where email = ?))",
                String.class, "creator-links@test.local");

        String invalidType = validBody("No Guardar", "9988776655",
                "[{\"linkType\":\"UNSUPPORTED\",\"url\":\"https://other.example\"}]");
        mockMvc.perform(put("/api/creators/me/profile")
                        .cookie(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidType))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].field").value("links[0].linkType"));

        mockMvc.perform(put("/api/creators/me/profile")
                        .cookie(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody("No Guardar", "9988776655", "[]")
                                .replace("\"links\":[]", "\"links\":[{\"linkType\":\"PORTFOLIO\",\"url\":\"https://new.example\",\"creatorProfileId\":\"other\"}]")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details[0].code").value("UNKNOWN_FIELD"));

        mockMvc.perform(put("/api/creators/me/profile")
                        .cookie(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody("No Guardar", "9988776655", "[]")
                                .replace("\"displayName\":\"No Guardar\"", "\"displayName\":\"No Guardar\",\"userId\":\"00000000-0000-0000-0000-000000000000\",\"id\":\"00000000-0000-0000-0000-000000000000\"")))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/creators/me/profile").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Perfil Inicial"))
                .andExpect(jsonPath("$.bankAccountNumberMasked").value("******7890"))
                .andExpect(jsonPath("$.links[0].url").value(originalUrl));
        assertEquals(1, countLinks("creator-links@test.local"));
    }

    @Test
    void persistenceFailureRollsBackProfileAndLinkReplacement() throws Exception {
        Cookie session = register("creator-rollback@test.local", "creator");
        save(session, validBody("Perfil Persistido", "1234567890",
                "[{\"linkType\":\"PORTFOLIO\",\"url\":\"https://old.example\"}]"));
        jdbcTemplate.execute("alter table creator_profile_links add constraint ck_reject_profile_url check (url <> 'https://fail.example')");

        mockMvc.perform(put("/api/creators/me/profile")
                        .cookie(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody("Cambio que revierte", "9876543210",
                                "[{\"linkType\":\"OTHER_PROJECT\",\"url\":\"https://fail.example\"}]")))
                .andExpect(status().isConflict());

        mockMvc.perform(get("/api/creators/me/profile").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Perfil Persistido"))
                .andExpect(jsonPath("$.bankAccountNumberMasked").value("******7890"))
                .andExpect(jsonPath("$.links[0].url").value("https://old.example"));
        assertEquals(1, countLinks("creator-rollback@test.local"));
    }

    @Test
    void emptyBankFieldsAreRejectedAndUnknownRootFieldsAreNotIgnored() throws Exception {
        Cookie session = register("creator-required@test.local", "creator");
        String emptyRequired = validBody("Nombre", "1234567890", "[]")
                .replace("\"bankAccountHolder\":\"Ana Pérez\"", "\"bankAccountHolder\":\"\"")
                .replace("\"bankAccountType\":\"SAVINGS\"", "\"bankAccountType\":\"\"")
                .replace("\"bankAccountDocument\":\"123456789\"", "\"bankAccountDocument\":\"\"");
        mockMvc.perform(put("/api/creators/me/profile")
                        .cookie(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(emptyRequired))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[?(@.field == 'profile.bankAccountHolder')]").exists())
                .andExpect(jsonPath("$.details[?(@.field == 'profile.bankAccountType')]").exists())
                .andExpect(jsonPath("$.details[?(@.field == 'profile.bankAccountDocument')]").exists());
        assertEquals(0, count("creator_profiles", "creator-required@test.local"));

        mockMvc.perform(put("/api/creators/me/profile")
                        .cookie(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody("Nombre", "1234567890", "[]")
                                .replace("\"links\":[]", "\"links\":[],\"status\":\"DISABLED\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].code").value("UNKNOWN_FIELD"));
        assertEquals(0, count("creator_profiles", "creator-required@test.local"));
    }

    private void save(Cookie session, String body) throws Exception {
        mockMvc.perform(put("/api/creators/me/profile")
                        .cookie(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Perfil guardado"));
    }

    private Cookie register(String email, String role) throws Exception {
        MockHttpServletRequestBuilder request = org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Nombre del usuario\",\"email\":\"" + email
                        + "\",\"password\":\"Secure1!\",\"role\":\"" + role + "\"}");
        return mockMvc.perform(request).andExpect(status().isCreated())
                .andReturn().getResponse().getCookie("AUTH_SESSION");
    }

    private String validBody(String displayName, String accountNumber, String links) {
        return "{\"displayName\":\"" + displayName
                + "\",\"bio\":\"Diseñadora social\",\"professionalTitle\":\"Diseñadora\","
                + "\"phone\":\"+573001112233\",\"bankAccountType\":\"SAVINGS\","
                + "\"bankName\":\"Banco de Prueba\",\"bankAccountNumber\":\"" + accountNumber
                + "\",\"bankAccountHolder\":\"Ana Pérez\",\"bankAccountDocument\":\"123456789\","
                + "\"links\":" + links + "}";
    }

    private int count(String table, String email) {
        return jdbcTemplate.queryForObject(
                "select count(*) from " + table + " where user_id = (select id from users where email = ?)",
                Integer.class, email);
    }

    private int countLinks(String email) {
        return jdbcTemplate.queryForObject(
                "select count(*) from creator_profile_links where creator_profile_id = "
                        + "(select id from creator_profiles where user_id = "
                        + "(select id from users where email = ?))",
                Integer.class, email);
    }
}
