package com.digitalwill.verification.web;

import com.digitalwill.common.TestTimeProvider;
import com.digitalwill.state.model.WillState;
import com.digitalwill.state.model.WillStateEntity;
import com.digitalwill.state.repository.WillStateRepository;
import com.digitalwill.state.service.WillStateService;
import com.digitalwill.verification.model.TrustedContact;
import com.digitalwill.verification.service.VerificationService;
import com.digitalwill.config.TestTimeConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import tools.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestTimeConfig.class)
class VerificationControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired VerificationService verificationService;
    @Autowired WillStateService willStateService;
    @Autowired WillStateRepository willStateRepository;
    @Autowired TestTimeProvider timeProvider;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private UUID willId;
    private TrustedContact contactA, contactB;
    private final Instant base = Instant.parse("2026-03-10T10:00:00Z");

    @BeforeEach
    void setUp() {
        timeProvider.setNow(base);
        WillStateEntity will = willStateService.createWill(UUID.randomUUID(), "Ctrl Will");
        willId = will.getId();
        timeProvider.setNow(base.plus(Duration.ofDays(30)).plus(Duration.ofHours(1)));
        willStateService.triggerInactivityWarning(willId, Duration.ofDays(30));
        timeProvider.setNow(base.plus(Duration.ofDays(30)).plus(Duration.ofDays(7)).plus(Duration.ofHours(1)));
        willStateService.triggerFinalWarning(willId, Duration.ofDays(7));
        timeProvider.setNow(base.plus(Duration.ofDays(30)).plus(Duration.ofDays(7)).plus(Duration.ofDays(3)).plus(Duration.ofHours(1)));
        willStateService.triggerVerificationPending(willId, Duration.ofDays(3));
        contactA = verificationService.createTrustedContact("Alice", "ctrl-a-" + UUID.randomUUID() + "@example.com");
        contactB = verificationService.createTrustedContact("Bob", "ctrl-b-" + UUID.randomUUID() + "@example.com");
        verificationService.associateContactWithWill(willId, contactA.getId());
        verificationService.associateContactWithWill(willId, contactB.getId());
    }

    @Test
    void confirm_viaApi_success() throws Exception {
        String token = verificationService.createVerificationRequest(willId, contactA.getId());
        mockMvc.perform(post("/api/verification/confirm")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("token", token))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.confirmed").value(true))
                .andExpect(jsonPath("$.quorumReached").value(false));
    }

    @Test
    void confirm_invalidToken_returns400() throws Exception {
        mockMvc.perform(post("/api/verification/confirm")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("token", "invalid-xyz"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void confirm_quorum_viaApi() throws Exception {
        String tA = verificationService.createVerificationRequest(willId, contactA.getId());
        String tB = verificationService.createVerificationRequest(willId, contactB.getId());
        mockMvc.perform(post("/api/verification/confirm")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(Map.of("token", tA)))).andExpect(status().isOk());
        mockMvc.perform(post("/api/verification/confirm")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("token", tB))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quorumReached").value(true));
    }
}