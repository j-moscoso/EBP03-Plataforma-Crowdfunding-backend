package com.ebp03.plataforma_crowdfunding_backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PlataformaCrowdfundingBackendApplicationTests {
	@Autowired MockMvc mockMvc;

	@Test
	void validRegistrationCreatesUserAndSessionWithoutSecrets() throws Exception {
		MvcResult result = mockMvc.perform(post("/api/auth/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"Creator\",\"email\":\" creator@test.local \",\"password\":\"Secure1!\",\"role\":\"creator\"}"))
			.andExpect(status().isCreated())
			.andExpect(cookie().exists("AUTH_SESSION"))
			.andExpect(jsonPath("$.user.email").value("creator@test.local"))
			.andExpect(jsonPath("$.user.role").value("creator"))
			.andExpect(jsonPath("$.user.passwordHash").doesNotExist())
			.andExpect(jsonPath("$.session.expiresAt").exists())
			.andReturn();
		assertNotEquals("", result.getResponse().getCookie("AUTH_SESSION").getValue());
	}

	@Test
	void browserGetShowsHowToUseAuthenticationEndpoints() throws Exception {
		for (String endpoint : List.of("register", "login", "social")) {
			mockMvc.perform(get("/api/auth/" + endpoint))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.method").value("POST"))
					.andExpect(jsonPath("$.endpoint").value("/api/auth/" + endpoint));
		}
	}

	@Test
	void duplicateRegistrationReturnsConflict() throws Exception {
		String body = "{\"name\":\"Sponsor\",\"email\":\"duplicate@test.local\",\"password\":\"Secure1!\",\"role\":\"sponsor\"}";
		mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated());
		mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("EMAIL_ALREADY_IN_USE"));
	}

	@Test
	void everyPasswordRuleIsReported() {
		var policy = new com.ebp03.plataforma_crowdfunding_backend.auth.service.PasswordPolicy();
		List<String> missing = policy.missingRequirements("abc");
		assertEquals(List.of("MINIMUM_LENGTH_8", "ONE_UPPERCASE_LETTER", "ONE_NUMBER", "ONE_SYMBOL"), missing);
	}

	@Test
	void nonexistentAndWrongPasswordHaveExactlySamePublicError() throws Exception {
		String register = "{\"name\":\"Login\",\"email\":\"login@test.local\",\"password\":\"Secure1!\",\"role\":\"sponsor\"}";
		mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(register)).andExpect(status().isCreated());
		String wrongPassword = postLogin("login@test.local", "Wrong1!");
		String missingUser = postLogin("missing@test.local", "Wrong1!");
		assertEquals(wrongPassword, missingUser);
	}

	@Test
	void validLoginWorksForCreatorAndSponsor() throws Exception {
		register("creator-login@test.local", "creator");
		register("sponsor-login@test.local", "sponsor");
		mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"creator-login@test.local\",\"password\":\"Secure1!\"}"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.user.role").value("creator"));
		mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"sponsor-login@test.local\",\"password\":\"Secure1!\"}"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.user.role").value("sponsor"));
	}

	@Test
	void logoutRevokesSessionAndIsIdempotent() throws Exception {
		MvcResult registration = mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"Logout\",\"email\":\"logout@test.local\",\"password\":\"Secure1!\",\"role\":\"creator\"}"))
			.andExpect(status().isCreated()).andReturn();
		var cookie = registration.getResponse().getCookie("AUTH_SESSION");
		mockMvc.perform(get("/api/auth/me").cookie(cookie)).andExpect(status().isOk()).andExpect(jsonPath("$.email").value("logout@test.local"));
		mockMvc.perform(post("/api/auth/logout").cookie(cookie)).andExpect(status().isNoContent());
		mockMvc.perform(get("/api/auth/me").cookie(cookie)).andExpect(status().isUnauthorized());
		mockMvc.perform(post("/api/auth/logout").cookie(cookie)).andExpect(status().isNoContent());
	}

	@Test
	void invalidOAuthCodeIsRejected() throws Exception {
		mockMvc.perform(post("/api/auth/social").contentType(MediaType.APPLICATION_JSON)
				.content("{\"provider\":\"google\",\"authorizationCode\":\"invalid\"}"))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_OAUTH_CODE"));
	}

	private String postLogin(String email, String password) throws Exception {
		return mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
				.andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
				.andReturn().getResponse().getContentAsString();
	}

	private void register(String email, String role) throws Exception {
		mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"User\",\"email\":\"" + email + "\",\"password\":\"Secure1!\",\"role\":\"" + role + "\"}"))
				.andExpect(status().isCreated());
	}
}
