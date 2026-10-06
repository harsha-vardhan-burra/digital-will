package com.digitalwill.auth.web;

import com.digitalwill.common.TestTimeProvider;
import com.digitalwill.config.TestTimeConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestTimeConfig.class)
class AuthControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired TestTimeProvider timeProvider;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        timeProvider.setNow(Instant.parse("2026-09-01T10:00:00Z"));
    }

    @Test
    @DisplayName("Register new user returns 201 with auth token and profile")
    void register_success() throws Exception {
        String email = "alice_" + UUID.randomUUID() + "@example.com";
        String body = objectMapper.writeValueAsString(Map.of(
                "email", email,
                "password", "SecureP@ss123",
                "fullName", "Alice Walker"
        ));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").isString())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.fullName").value("Alice Walker"))
                .andExpect(jsonPath("$.userId").isString());
    }

    @Test
    @DisplayName("Duplicate email registration returns 400 Bad Request")
    void register_duplicateEmail_fails() throws Exception {
        String email = "dup_" + UUID.randomUUID() + "@example.com";
        String body = objectMapper.writeValueAsString(Map.of(
                "email", email,
                "password", "SecureP@ss123",
                "fullName", "Original User"
        ));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"));
    }

    @Test
    @DisplayName("Login with valid credentials returns 200 with new token")
    void login_success() throws Exception {
        String email = "bob_" + UUID.randomUUID() + "@example.com";
        String regBody = objectMapper.writeValueAsString(Map.of(
                "email", email,
                "password", "CorrectPassword123",
                "fullName", "Bob Dylan"
        ));
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(regBody))
                .andExpect(status().isCreated());

        String loginBody = objectMapper.writeValueAsString(Map.of(
                "email", email,
                "password", "CorrectPassword123"
        ));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isString())
                .andExpect(jsonPath("$.email").value(email));
    }

    @Test
    @DisplayName("Login with invalid password returns 403 Forbidden")
    void login_wrongPassword_fails() throws Exception {
        String email = "carol_" + UUID.randomUUID() + "@example.com";
        String regBody = objectMapper.writeValueAsString(Map.of(
                "email", email,
                "password", "RightPassword123",
                "fullName", "Carol King"
        ));
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(regBody))
                .andExpect(status().isCreated());

        String loginBody = objectMapper.writeValueAsString(Map.of(
                "email", email,
                "password", "WrongPassword999"
        ));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("GET /api/auth/me returns profile for authenticated user")
    void getMe_authenticated_success() throws Exception {
        String email = "david_" + UUID.randomUUID() + "@example.com";
        String regBody = objectMapper.writeValueAsString(Map.of(
                "email", email,
                "password", "StrongPass456!",
                "fullName", "David Bowie"
        ));
        String regRes = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(regBody))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Map<?, ?> map = objectMapper.readValue(regRes, Map.class);
        String token = (String) map.get("token");

        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.fullName").value("David Bowie"));
    }

    @Test
    @DisplayName("GET /api/auth/me without token returns 401 Unauthorized")
    void getMe_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }
}
