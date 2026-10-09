package com.digitalwill.security;

import com.digitalwill.common.TestTimeProvider;
import com.digitalwill.config.TestTimeConfig;
import com.digitalwill.security.ratelimit.RateLimitProperties;
import com.digitalwill.security.ratelimit.RateLimitService;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestTimeConfig.class)
public class RateLimitingSecurityTest {

    @Autowired MockMvc mockMvc;
    @Autowired RateLimitService rateLimitService;
    @Autowired RateLimitProperties rateLimitProperties;
    @Autowired TestTimeProvider timeProvider;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Instant baseTime = Instant.parse("2026-10-01T10:00:00Z");

    @BeforeEach
    void setUp() {
        timeProvider.setNow(baseTime);
        rateLimitService.reset();
    }

    @Test
    @DisplayName("ATK-10: Burst login attempts beyond threshold trigger HTTP 429 Too Many Requests with Retry-After")
    void burstLogin_triggers429_withRetryAfter() throws Exception {
        int limit = rateLimitProperties.getAuthLimit();
        String testEmail = "brute_user_" + UUID.randomUUID() + "@example.com";
        String body = objectMapper.writeValueAsString(Map.of("email", testEmail, "password", "BadPass123!"));

        // Consume up to configured limit
        for (int i = 0; i < limit; i++) {
            mockMvc.perform(post("/api/auth/login")
                            .with(req -> { req.setRemoteAddr("198.51.100.1"); return req; })
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isForbidden()); // Valid attempt rejected due to bad credentials
        }

        // Limit+1 attempt from same IP must be rate limited
        mockMvc.perform(post("/api/auth/login")
                        .with(req -> { req.setRemoteAddr("198.51.100.1"); return req; })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.error").value("TOO_MANY_REQUESTS"))
                .andExpect(jsonPath("$.message").value("Rate limit exceeded. Please try again later."));

        // Different IP address should NOT be blocked (isolation by client IP)
        mockMvc.perform(post("/api/auth/login")
                        .with(req -> { req.setRemoteAddr("198.51.100.2"); return req; })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden()); // Allowed through to auth logic
    }

    @Test
    @DisplayName("ATK-10: Burst registration attempts beyond threshold trigger HTTP 429")
    void burstRegister_triggers429() throws Exception {
        int limit = rateLimitProperties.getAuthLimit();

        for (int i = 0; i < limit; i++) {
            String regBody = objectMapper.writeValueAsString(Map.of(
                    "email", "reg_" + i + "_" + UUID.randomUUID() + "@example.com",
                    "password", "ValidPass123!",
                    "fullName", "Burst User " + i
            ));
            mockMvc.perform(post("/api/auth/register")
                            .with(req -> { req.setRemoteAddr("203.0.113.10"); return req; })
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(regBody))
                    .andExpect(status().isCreated());
        }

        // Limit+1 must be rate limited
        String extraBody = objectMapper.writeValueAsString(Map.of(
                "email", "extra_" + UUID.randomUUID() + "@example.com",
                "password", "ValidPass123!",
                "fullName", "Extra User"
        ));
        mockMvc.perform(post("/api/auth/register")
                        .with(req -> { req.setRemoteAddr("203.0.113.10"); return req; })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(extraBody))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.status").value(429));
    }

    @Test
    @DisplayName("ATK-10: Burst verification confirm attempts trigger HTTP 429")
    void burstVerificationConfirm_triggers429() throws Exception {
        int limit = rateLimitProperties.getVerificationLimit();
        String confirmBody = objectMapper.writeValueAsString(Map.of(
                "token", "guess-token-" + UUID.randomUUID(),
                "notes", "Brute force guess"
        ));

        for (int i = 0; i < limit; i++) {
            mockMvc.perform(post("/api/verification/confirm")
                            .with(req -> { req.setRemoteAddr("192.0.2.55"); return req; })
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(confirmBody))
                    .andExpect(status().isBadRequest()); // Unknown token returns 400
        }

        // Exceeded limit
        mockMvc.perform(post("/api/verification/confirm")
                        .with(req -> { req.setRemoteAddr("192.0.2.55"); return req; })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(confirmBody))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
    }

    @Test
    @DisplayName("ATK-10: Burst disclosure access attempts trigger HTTP 429")
    void burstDisclosureAccess_triggers429() throws Exception {
        int limit = rateLimitProperties.getDisclosureLimit();

        for (int i = 0; i < limit; i++) {
            mockMvc.perform(get("/api/disclosure/guess-token-" + i)
                            .with(req -> { req.setRemoteAddr("192.0.2.77"); return req; }))
                    .andExpect(status().isBadRequest()); // Invalid token returns 400
        }

        // Exceeded limit
        mockMvc.perform(get("/api/disclosure/guess-token-extra")
                        .with(req -> { req.setRemoteAddr("192.0.2.77"); return req; }))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
    }

    @Test
    @DisplayName("ATK-10: Rate limit sliding window cooldown resets limits after window expires")
    void rateLimit_cooldown_resetsAndAllowsRequests() throws Exception {
        int limit = rateLimitProperties.getAuthLimit();
        String body = objectMapper.writeValueAsString(Map.of("email", "cooldown@example.com", "password", "BadPass123!"));

        // Exhaust limit
        for (int i = 0; i < limit; i++) {
            mockMvc.perform(post("/api/auth/login")
                            .with(req -> { req.setRemoteAddr("198.51.100.99"); return req; })
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isForbidden());
        }

        // Blocked
        mockMvc.perform(post("/api/auth/login")
                        .with(req -> { req.setRemoteAddr("198.51.100.99"); return req; })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isTooManyRequests());

        // Advance time past the sliding window duration (60 seconds)
        timeProvider.advance(Duration.ofSeconds(rateLimitProperties.getWindowSeconds() + 1));

        // Now requests must be allowed through again
        mockMvc.perform(post("/api/auth/login")
                        .with(req -> { req.setRemoteAddr("198.51.100.99"); return req; })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden()); // Succeeded past rate limiter, evaluated by auth
    }
}
