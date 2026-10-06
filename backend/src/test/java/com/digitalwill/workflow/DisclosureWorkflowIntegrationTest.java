package com.digitalwill.workflow;

import com.digitalwill.common.TestTimeProvider;
import com.digitalwill.config.TestTimeConfig;
import com.digitalwill.document.service.DocumentService;
import com.digitalwill.estate.model.Asset;
import com.digitalwill.estate.model.AssetCategory;
import com.digitalwill.estate.model.Beneficiary;
import com.digitalwill.estate.service.EstateService;
import com.digitalwill.release.exception.DisclosureTokenConsumedException;
import com.digitalwill.release.model.DisclosureToken;
import com.digitalwill.release.model.DisclosureTokenStatus;
import com.digitalwill.release.repository.DisclosureTokenRepository;
import com.digitalwill.release.service.DisclosureService;
import com.digitalwill.release.service.ReleaseExecutionService;
import com.digitalwill.state.model.WillStateEntity;
import com.digitalwill.state.service.WillStateService;
import com.digitalwill.verification.service.VerificationTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.springframework.transaction.annotation.Transactional;

/**
 * Phase 3 Workflow 5: Controlled Disclosure Access & Document Download
 *
 * Verifies that:
 * 1. Beneficiary presents valid token and receives strictly scoped package (only their allocated assets).
 * 2. Unallocated assets and other beneficiaries' assets are never leaked.
 * 3. Beneficiary downloads supporting document linked to their allocation (200 OK, envelope decrypted).
 * 4. Cross-beneficiary document download is strictly rejected with 403 Forbidden.
 * 5. Expired token returns 410 Gone.
 * 6. Revoked token returns 410 Gone.
 * 7. Single-use token consumption prevents replay (409 Conflict on re-access).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestTimeConfig.class)
@Transactional
class DisclosureWorkflowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private WillStateService willStateService;

    @Autowired
    private EstateService estateService;

    @Autowired
    private DocumentService documentService;

    @Autowired
    private ReleaseExecutionService releaseExecutionService;

    @Autowired
    private DisclosureService disclosureService;

    @Autowired
    private DisclosureTokenRepository disclosureTokenRepository;

    @Autowired
    private VerificationTokenService tokenService;

    @Autowired
    private TestTimeProvider timeProvider;

    private final Instant baseTime = Instant.parse("2026-06-01T10:00:00Z");
    private UUID ownerId;
    private UUID willId;
    private Beneficiary benAlice;
    private Beneficiary benBob;
    private UUID aliceDocId;
    private UUID bobDocId;
    private String rawTokenAlice;
    private String rawTokenBob;

    @BeforeEach
    void setUp() {
        timeProvider.setNow(baseTime);

        ownerId = UUID.randomUUID();
        WillStateEntity will = willStateService.createWill(ownerId, "Disclosure Flow Will");
        willId = will.getId();

        // Upload documents for Will
        byte[] aliceDocBytes = "CONFIDENTIAL: Alice Title Deed".getBytes(StandardCharsets.UTF_8);
        var aliceDoc = documentService.uploadDocument(willId, ownerId, "alice_deed.pdf", "application/pdf", aliceDocBytes);
        aliceDocId = aliceDoc.getId();

        byte[] bobDocBytes = "CONFIDENTIAL: Bob Bank Statements".getBytes(StandardCharsets.UTF_8);
        var bobDoc = documentService.uploadDocument(willId, ownerId, "bob_bank.pdf", "application/pdf", bobDocBytes);
        bobDocId = bobDoc.getId();

        // Create assets referencing documents in instructions
        Asset assetAlice = estateService.addAsset(
                willId, "Apartment Deed", AssetCategory.REAL_ESTATE, "Downtown Flat",
                null, "Attached document: " + aliceDocId
        );
        Asset assetBob = estateService.addAsset(
                willId, "Brokerage Portfolio", AssetCategory.INVESTMENT, "Index Funds",
                null, "Attached document: " + bobDocId
        );

        benAlice = estateService.addBeneficiary(willId, "Alice Beneficiary", "alice@example.com", "Daughter");
        benBob = estateService.addBeneficiary(willId, "Bob Beneficiary", "bob@example.com", "Son");

        estateService.allocateAsset(assetAlice.getId(), benAlice.getId(), 100, "100% to Alice. Attached document: " + aliceDocId);
        estateService.allocateAsset(assetBob.getId(), benBob.getId(), 100, "100% to Bob. Attached document: " + bobDocId);

        // Fast-forward will to RELEASE_PENDING
        timeProvider.advanceDays(31);
        willStateService.triggerInactivityWarning(willId, Duration.ofDays(30));
        timeProvider.advanceDays(8);
        willStateService.triggerFinalWarning(willId, Duration.ofDays(7));
        timeProvider.advanceDays(8);
        willStateService.triggerVerificationPending(willId, Duration.ofDays(7));
        willStateService.triggerVerified(willId, 2, 2);

        Instant releaseAfter = timeProvider.now().plus(Duration.ofDays(3));
        willStateService.scheduleRelease(willId, releaseAfter);

        // Advance past release delay and execute release
        timeProvider.advanceDays(4);
        var execResult = releaseExecutionService.claimAndExecuteRelease(willId);
        assertThat(execResult.executed()).isTrue();

        // Set known raw tokens for test assertions
        rawTokenAlice = "raw-alice-" + UUID.randomUUID();
        DisclosureToken tokenA = disclosureTokenRepository.findByWillIdAndBeneficiaryId(willId, benAlice.getId()).orElseThrow();
        tokenA.setTokenHash(tokenService.hashToken(rawTokenAlice));
        disclosureTokenRepository.save(tokenA);

        rawTokenBob = "raw-bob-" + UUID.randomUUID();
        DisclosureToken tokenB = disclosureTokenRepository.findByWillIdAndBeneficiaryId(willId, benBob.getId()).orElseThrow();
        tokenB.setTokenHash(tokenService.hashToken(rawTokenBob));
        disclosureTokenRepository.save(tokenB);
    }

    @Test
    @DisplayName("Workflow 5.1: Alice accesses strictly scoped package containing only her allocated assets")
    void aliceGetsScopedPackageWithoutLeakingBobAssets() throws Exception {
        mockMvc.perform(get("/api/disclosure/" + rawTokenAlice).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.willId").value(willId.toString()))
                .andExpect(jsonPath("$.beneficiaryId").value(benAlice.getId().toString()))
                .andExpect(jsonPath("$.packagePayloadJson").value(org.hamcrest.Matchers.containsString("Apartment Deed")))
                .andExpect(jsonPath("$.packagePayloadJson").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Brokerage Portfolio"))));
    }

    @Test
    @DisplayName("Workflow 5.2: Alice downloads her scoped supporting document successfully (200 OK, decrypted)")
    void aliceDownloadsHerScopedDocument() throws Exception {
        mockMvc.perform(get("/api/disclosure/" + rawTokenAlice + "/document/" + aliceDocId))
                .andExpect(status().isOk())
                .andExpect(content().string("CONFIDENTIAL: Alice Title Deed"));
    }

    @Test
    @DisplayName("Workflow 5.3: Alice cannot download Bob's document (403 Forbidden)")
    void aliceCannotDownloadBobDocument() throws Exception {
        mockMvc.perform(get("/api/disclosure/" + rawTokenAlice + "/document/" + bobDocId))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("Workflow 5.4: Expired token returns 410 Gone")
    void expiredTokenReturns410() throws Exception {
        DisclosureToken tokenA = disclosureTokenRepository.findByWillIdAndBeneficiaryId(willId, benAlice.getId()).orElseThrow();
        tokenA.setExpiresAt(timeProvider.now().minus(Duration.ofDays(1)));
        disclosureTokenRepository.save(tokenA);

        mockMvc.perform(get("/api/disclosure/" + rawTokenAlice).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("DISCLOSURE_TOKEN_EXPIRED"));
    }

    @Test
    @DisplayName("Workflow 5.5: Revoked token returns 410 Gone")
    void revokedTokenReturns410() throws Exception {
        DisclosureToken tokenA = disclosureTokenRepository.findByWillIdAndBeneficiaryId(willId, benAlice.getId()).orElseThrow();
        tokenA.setStatus(DisclosureTokenStatus.REVOKED);
        disclosureTokenRepository.save(tokenA);

        mockMvc.perform(get("/api/disclosure/" + rawTokenAlice).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("DISCLOSURE_TOKEN_REVOKED"));
    }

    @Test
    @DisplayName("Workflow 5.6: Single-use token consumption prevents replay")
    void singleUseTokenConsumptionPreventsReplay() {
        // First consumption succeeds
        var firstAccess = disclosureService.consumeDisclosure(rawTokenBob);
        assertThat(firstAccess).isNotNull();
        assertThat(firstAccess.beneficiaryId()).isEqualTo(benBob.getId());

        // Second consumption fails closed with 409 Conflict
        assertThatThrownBy(() -> disclosureService.consumeDisclosure(rawTokenBob))
                .isInstanceOf(DisclosureTokenConsumedException.class);
    }
}
