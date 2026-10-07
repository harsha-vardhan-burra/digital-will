package com.digitalwill.security;

import com.digitalwill.auth.model.UserAuthToken;
import com.digitalwill.auth.repository.UserAuthTokenRepository;
import com.digitalwill.auth.service.AuthService;
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

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestTimeConfig.class)
public class AuthSecurityAbuseTest {

    @Autowired MockMvc mockMvc;
    @Autowired AuthService authService;
    @Autowired UserAuthTokenRepository tokenRepository;
    @Autowired TestTimeProvider timeProvider;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final Instant baseTime = Instant.parse("2026-10-01T10:00:00Z");

    @BeforeEach
    void setUp() {
        timeProvider.setNow(baseTime);
    }

    @Test
    @DisplayName("ATK-01: Non-existent user login fails closed with generic error message")
    void login_nonExistentUser_failsClosed() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "email", "nonexistent_" + UUID.randomUUID() + "@example.com",
                "password", "AnyPassword123!"
        ));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").value("Access is denied"));
    }

    @Test
    @DisplayName("ATK-01: Wrong password login fails closed with identical generic message (no user enumeration)")
    void login_wrongPassword_failsClosedIdentically() throws Exception {
        String email = "target_" + UUID.randomUUID() + "@example.com";
        authService.register(email, "CorrectPassword123!", "Target User");

        String body = objectMapper.writeValueAsString(Map.of(
                "email", email,
                "password", "WrongGuess123!"
        ));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").value("Access is denied"));
    }

    @Test
    @DisplayName("ATK-02: Duplicate registration fails closed and does not disclose sensitive internal info")
    void register_duplicate_failsClosed() throws Exception {
        String email = "dup_" + UUID.randomUUID() + "@example.com";
        authService.register(email, "InitialPassword123!", "First User");

        String body = objectMapper.writeValueAsString(Map.of(
                "email", email,
                "password", "SecondPassword123!",
                "fullName", "Second User"
        ));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"))
                .andExpect(jsonPath("$.message").value("Email is already registered"));
    }

    @Test
    @DisplayName("ATK-03: Logout strictly invalidates session token against replay")
    void logout_invalidatesToken_cannotBeReplayed() throws Exception {
        String email = "logout_" + UUID.randomUUID() + "@example.com";
        AuthService.AuthResponse auth = authService.register(email, "Password123!", "Logout User");

        // Verify valid access initially
        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + auth.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email));

        // Logout
        mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", "Bearer " + auth.token()))
                .andExpect(status().isOk());

        // Replay attempt after logout must return 401 Unauthorized
        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + auth.token()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("ATK-04: Expired session token is rejected with 401 Unauthorized")
    void expiredToken_rejectedWith401() throws Exception {
        String email = "expire_" + UUID.randomUUID() + "@example.com";
        AuthService.AuthResponse auth = authService.register(email, "Password123!", "Expiring User");

        // Advance time past 30 days AUTH_TOKEN_TTL
        timeProvider.advance(Duration.ofDays(31));

        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + auth.token()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("ATK-04: Malformed and corrupted Authorization headers fail closed with 401")
    void malformedAuthHeaders_failClosed() throws Exception {
        // Missing token after Bearer
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer   "))
                .andExpect(status().isUnauthorized());

        // Wrong scheme (Basic instead of Bearer)
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Basic dXNlcjpwYXNz"))
                .andExpect(status().isUnauthorized());

        // Garbage token
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer random-fake-token-value"))
                .andExpect(status().isUnauthorized());

        // Overly long token flood
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + "A".repeat(2048)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("ATK-04: Raw bearer tokens are never stored plaintext in the database")
    void tokenStorage_neverStoresPlaintextRawToken() {
        String email = "storage_" + UUID.randomUUID() + "@example.com";
        AuthService.AuthResponse auth = authService.register(email, "Password123!", "Storage Check");

        // Raw token must have high entropy (at least 32 bytes hex/base64 => length >= 32)
        assertThat(auth.token().length()).isGreaterThanOrEqualTo(32);

        // Search DB tokens by raw token value -> must NOT exist
        Optional<UserAuthToken> rawMatch = tokenRepository.findByTokenHash(auth.token());
        assertThat(rawMatch).isEmpty();
    }
}
