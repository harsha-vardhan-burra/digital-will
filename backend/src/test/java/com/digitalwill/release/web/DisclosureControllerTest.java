package com.digitalwill.release.web;

import com.digitalwill.common.TestTimeProvider;
import com.digitalwill.config.TestTimeConfig;
import com.digitalwill.estate.model.Asset;
import com.digitalwill.estate.model.AssetCategory;
import com.digitalwill.estate.model.Beneficiary;
import com.digitalwill.estate.service.EstateService;
import com.digitalwill.release.model.DisclosureToken;
import com.digitalwill.release.repository.DisclosureTokenRepository;
import com.digitalwill.release.service.ReleaseExecutionService;
import com.digitalwill.state.model.WillStateEntity;
import com.digitalwill.state.service.WillStateService;
import com.digitalwill.verification.service.VerificationTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestTimeConfig.class)
class DisclosureControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired WillStateService willStateService;
    @Autowired EstateService estateService;
    @Autowired ReleaseExecutionService releaseExecutionService;
    @Autowired DisclosureTokenRepository disclosureTokenRepository;
    @Autowired VerificationTokenService tokenService;
    @Autowired com.digitalwill.document.repository.EncryptedDocumentRepository documentRepository;
    @Autowired TestTimeProvider timeProvider;

    private UUID willId;
    private Beneficiary beneficiary;
    private final Instant base = Instant.parse("2026-09-01T10:00:00Z");

    @BeforeEach
    void setUp() {
        timeProvider.setNow(base);
        WillStateEntity will = willStateService.createWill(UUID.randomUUID(), "Disclosure Controller Will");
        willId = will.getId();

        Asset asset = estateService.addAsset(willId, "Retirement Fund", AssetCategory.INVESTMENT, "401k", "enc-data", "Transfer funds");
        beneficiary = estateService.addBeneficiary(willId, "Bob Doe", "bob@example.com", "Son");
        estateService.allocateAsset(asset.getId(), beneficiary.getId(), 100, "100% allocation");

        // Advance to RELEASE_PENDING
        timeProvider.setNow(base.plus(Duration.ofDays(30)).plus(Duration.ofHours(1)));
        willStateService.triggerInactivityWarning(willId, Duration.ofDays(30));
        timeProvider.setNow(base.plus(Duration.ofDays(30)).plus(Duration.ofDays(7)).plus(Duration.ofHours(1)));
        willStateService.triggerFinalWarning(willId, Duration.ofDays(7));
        timeProvider.setNow(base.plus(Duration.ofDays(30)).plus(Duration.ofDays(7)).plus(Duration.ofDays(3)).plus(Duration.ofHours(1)));
        willStateService.triggerVerificationPending(willId, Duration.ofDays(3));
        willStateService.triggerVerified(willId, 2, 2);
        Instant releaseAfter = timeProvider.now().plus(Duration.ofDays(7));
        willStateService.scheduleRelease(willId, releaseAfter);
        timeProvider.setNow(releaseAfter.plus(Duration.ofHours(1)));
    }

    @Test
    void getDisclosure_success() throws Exception {
        // Execute release
        releaseExecutionService.claimAndExecuteRelease(willId);

        // Update token hash to known raw token
        String rawToken = "raw-disclosure-token-test-12345";
        String tokenHash = tokenService.hashToken(rawToken);

        DisclosureToken token = disclosureTokenRepository.findByWillIdAndBeneficiaryId(willId, beneficiary.getId()).orElseThrow();
        token.setTokenHash(tokenHash);
        disclosureTokenRepository.save(token);

        mockMvc.perform(get("/api/disclosure/" + rawToken).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.willId").value(willId.toString()))
                .andExpect(jsonPath("$.beneficiaryId").value(beneficiary.getId().toString()))
                .andExpect(jsonPath("$.packagePayloadJson").isNotEmpty());
    }

    @Test
    void getDisclosure_invalidToken_returns400() throws Exception {
        mockMvc.perform(get("/api/disclosure/bogus-invalid-token").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_DISCLOSURE_TOKEN"));
    }

    @Test
    void getDisclosure_expiredToken_returns410() throws Exception {
        releaseExecutionService.claimAndExecuteRelease(willId);

        String rawToken = "raw-expired-token-99999";
        DisclosureToken token = disclosureTokenRepository.findByWillIdAndBeneficiaryId(willId, beneficiary.getId()).orElseThrow();
        token.setTokenHash(tokenService.hashToken(rawToken));
        token.setExpiresAt(timeProvider.now().minus(Duration.ofDays(1)));
        disclosureTokenRepository.save(token);

        mockMvc.perform(get("/api/disclosure/" + rawToken).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("DISCLOSURE_TOKEN_EXPIRED"));
    }

    @Test
    void getDisclosure_revokedToken_returns410() throws Exception {
        releaseExecutionService.claimAndExecuteRelease(willId);

        String rawToken = "raw-revoked-token-88888";
        DisclosureToken token = disclosureTokenRepository.findByWillIdAndBeneficiaryId(willId, beneficiary.getId()).orElseThrow();
        token.setTokenHash(tokenService.hashToken(rawToken));
        token.setStatus(com.digitalwill.release.model.DisclosureTokenStatus.REVOKED);
        disclosureTokenRepository.save(token);

        mockMvc.perform(get("/api/disclosure/" + rawToken).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("DISCLOSURE_TOKEN_REVOKED"));
    }

    @Test
    void downloadDocument_scopeViolation_returns403() throws Exception {
        releaseExecutionService.claimAndExecuteRelease(willId);

        String rawToken = "raw-scope-test-token-77777";
        DisclosureToken token = disclosureTokenRepository.findByWillIdAndBeneficiaryId(willId, beneficiary.getId()).orElseThrow();
        token.setTokenHash(tokenService.hashToken(rawToken));
        disclosureTokenRepository.save(token);

        // An unauthorized document that exists for this Will but is NOT in Bob's disclosure scope
        UUID unauthorizedDocId = UUID.randomUUID();
        com.digitalwill.document.model.EncryptedDocument doc = new com.digitalwill.document.model.EncryptedDocument(
                unauthorizedDocId, willId, "confidential_other.pdf", "application/pdf", 100L,
                "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                willId + "/" + unauthorizedDocId + ".enc", "dek", "iv", "AES/GCM/NoPadding", "AESWrap", 1,
                timeProvider.now(), UUID.randomUUID()
        );
        documentRepository.save(doc);

        mockMvc.perform(get("/api/disclosure/" + rawToken + "/document/" + unauthorizedDocId))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void downloadDocument_wrongWillOrNotFound_returns404() throws Exception {
        releaseExecutionService.claimAndExecuteRelease(willId);

        String rawToken = "raw-scope-test-token-66666";
        DisclosureToken token = disclosureTokenRepository.findByWillIdAndBeneficiaryId(willId, beneficiary.getId()).orElseThrow();
        token.setTokenHash(tokenService.hashToken(rawToken));
        disclosureTokenRepository.save(token);

        UUID nonExistentDocId = UUID.randomUUID();

        mockMvc.perform(get("/api/disclosure/" + rawToken + "/document/" + nonExistentDocId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DOCUMENT_NOT_FOUND"));
    }
}
