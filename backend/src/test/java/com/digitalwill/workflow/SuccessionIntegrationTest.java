package com.digitalwill.workflow;

import com.digitalwill.audit.model.AuditAction;
import com.digitalwill.audit.model.AuditLogEntry;
import com.digitalwill.audit.repository.AuditLogRepository;
import com.digitalwill.common.TestTimeProvider;
import com.digitalwill.config.TestTimeConfig;
import com.digitalwill.estate.model.AssetCategory;
import com.digitalwill.estate.service.EstateService;
import com.digitalwill.release.service.ReleaseExecutionService;
import com.digitalwill.state.model.WillState;
import com.digitalwill.state.model.WillStateEntity;
import com.digitalwill.state.repository.WillStateRepository;
import com.digitalwill.state.service.WillStateService;
import com.digitalwill.verification.service.VerificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 3 Workflow 3: Full Inactivity-to-Succession Progression
 *
 * Verifies end-to-end progression through the authoritative state machine:
 * ACTIVE
 *   ↓ (time advances past inactivity threshold)
 * INACTIVITY_WARNING
 *   ↓ (time advances past warning window)
 * FINAL_WARNING
 *   ↓ (time advances past final warning window)
 * VERIFICATION_PENDING
 *   ↓ (2-of-3 distinct trusted contact confirmations)
 * VERIFIED
 *   ↓ (release scheduled with delay)
 * RELEASE_PENDING
 *   ↓ (time advances past release delay)
 * EXECUTING
 *   ↓
 * EXECUTED (terminal state)
 *
 * Confirms hash-chained audit logging and invariant preservation at every stage.
 */
@SpringBootTest
@Import(TestTimeConfig.class)
@Transactional
class SuccessionIntegrationTest {

    @Autowired
    private WillStateService willStateService;

    @Autowired
    private WillStateRepository willStateRepository;

    @Autowired
    private VerificationService verificationService;

    @Autowired
    private EstateService estateService;

    @Autowired
    private ReleaseExecutionService releaseExecutionService;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private TestTimeProvider timeProvider;

    private final Instant baseTime = Instant.parse("2026-04-01T10:00:00Z");
    private UUID ownerId;
    private UUID willId;

    @BeforeEach
    void setUp() {
        timeProvider.setNow(baseTime);
        auditLogRepository.deleteAll();

        ownerId = UUID.randomUUID();
        WillStateEntity will = willStateService.createWill(ownerId, "Master Succession Plan");
        willId = will.getId();

        // Setup estate items
        var asset = estateService.addAsset(willId, "Primary Residence", AssetCategory.REAL_ESTATE, "Family home", null, "Key in lockbox");
        var ben = estateService.addBeneficiary(willId, "Sarah Doe", "sarah@example.com", "Daughter");
        estateService.allocateAsset(asset.getId(), ben.getId(), 100, "Full transfer");

        // Add 3 trusted contacts (satisfying 2-of-3 quorum requirement)
        verificationService.addTrustedContactToWill(willId, "Contact One", "contact1_" + UUID.randomUUID() + "@example.com");
        verificationService.addTrustedContactToWill(willId, "Contact Two", "contact2_" + UUID.randomUUID() + "@example.com");
        verificationService.addTrustedContactToWill(willId, "Contact Three", "contact3_" + UUID.randomUUID() + "@example.com");
    }

    @Test
    @DisplayName("Workflow 3: Full progression from ACTIVE to terminal EXECUTED state with 2-of-3 quorum")
    void fullInactivityToSuccessionProgression() {
        // 1. Initial State is ACTIVE
        WillStateEntity initialWill = willStateService.getWillOrThrow(willId);
        assertThat(initialWill.getState()).isEqualTo(WillState.ACTIVE);

        // 2. Advance time past inactivity threshold (30 days) -> trigger INACTIVITY_WARNING
        timeProvider.advanceDays(31);
        boolean warningTriggered = willStateService.triggerInactivityWarning(willId, Duration.ofDays(30));
        assertThat(warningTriggered).isTrue();

        WillStateEntity warningWill = willStateService.getWillOrThrow(willId);
        assertThat(warningWill.getState()).isEqualTo(WillState.INACTIVITY_WARNING);
        assertThat(warningWill.getWarningSentAt()).isNotNull();

        // 3. Advance time past warning delay (7 days) -> trigger FINAL_WARNING
        timeProvider.advanceDays(8);
        boolean finalWarningTriggered = willStateService.triggerFinalWarning(willId, Duration.ofDays(7));
        assertThat(finalWarningTriggered).isTrue();

        WillStateEntity finalWarningWill = willStateService.getWillOrThrow(willId);
        assertThat(finalWarningWill.getState()).isEqualTo(WillState.FINAL_WARNING);
        assertThat(finalWarningWill.getFinalWarningSentAt()).isNotNull();

        // 4. Advance time past final warning delay (7 days) -> trigger VERIFICATION_PENDING
        timeProvider.advanceDays(8);
        boolean verificationStarted = willStateService.triggerVerificationPending(willId, Duration.ofDays(7));
        assertThat(verificationStarted).isTrue();

        WillStateEntity pendingWill = willStateService.getWillOrThrow(willId);
        assertThat(pendingWill.getState()).isEqualTo(WillState.VERIFICATION_PENDING);

        // 5. Generate verification tokens for contacts
        var contacts = verificationService.listContactsForWillDetailed(willId);
        assertThat(contacts).hasSize(3);

        String tokenContact1 = verificationService.createVerificationRequest(willId, contacts.get(0).contactId(), Duration.ofDays(7));
        String tokenContact2 = verificationService.createVerificationRequest(willId, contacts.get(1).contactId(), Duration.ofDays(7));

        // Contact 1 confirms -> distinctCount = 1 (Quorum NOT reached yet)
        var confirmResult1 = verificationService.confirm(tokenContact1);
        assertThat(confirmResult1.confirmed()).isTrue();
        assertThat(confirmResult1.quorumReached()).isFalse();
        assertThat(willStateService.getWillOrThrow(willId).getState()).isEqualTo(WillState.VERIFICATION_PENDING);

        // Contact 2 confirms -> distinctCount = 2 (2-of-3 Quorum REACHED -> transitions to VERIFIED)
        var confirmResult2 = verificationService.confirm(tokenContact2);
        assertThat(confirmResult2.confirmed()).isTrue();
        assertThat(confirmResult2.quorumReached()).isTrue();

        WillStateEntity verifiedWill = willStateService.getWillOrThrow(willId);
        assertThat(verifiedWill.getState()).isEqualTo(WillState.VERIFIED);
        assertThat(verifiedWill.getVerifiedAt()).isNotNull();

        // 6. Schedule release with 3-day safety delay -> transitions to RELEASE_PENDING
        Instant releaseAfter = timeProvider.now().plus(Duration.ofDays(3));
        boolean releaseScheduled = willStateService.scheduleRelease(willId, releaseAfter);
        assertThat(releaseScheduled).isTrue();

        WillStateEntity releasePendingWill = willStateService.getWillOrThrow(willId);
        assertThat(releasePendingWill.getState()).isEqualTo(WillState.RELEASE_PENDING);
        assertThat(releasePendingWill.getReleaseAfter()).isEqualTo(releaseAfter);

        // 7. Attempting execution before releaseAfter has elapsed fails closed
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> releaseExecutionService.claimAndExecuteRelease(willId))
                .isInstanceOf(com.digitalwill.state.exception.TransitionGuardFailedException.class);
        assertThat(willStateService.getWillOrThrow(willId).getState()).isEqualTo(WillState.RELEASE_PENDING);

        // 8. Advance time past release delay (4 days) -> execute release
        timeProvider.advanceDays(4);
        ReleaseExecutionService.ExecutionResult executedResult = releaseExecutionService.claimAndExecuteRelease(willId);
        assertThat(executedResult.executed()).isTrue();

        // 9. Will is now in terminal EXECUTED state
        WillStateEntity terminalWill = willStateService.getWillOrThrow(willId);
        assertThat(terminalWill.getState()).isEqualTo(WillState.EXECUTED);

        // 10. Verify audit log integrity across the full succession lifecycle
        List<AuditLogEntry> auditTrail = auditLogRepository.findByWillIdOrderBySequenceNumberAsc(willId);
        assertThat(auditTrail).isNotEmpty();

        // Verify hash chaining integrity across all audit entries
        for (int i = 1; i < auditTrail.size(); i++) {
            assertThat(auditTrail.get(i).getPrevHash()).isEqualTo(auditTrail.get(i - 1).getEntryHash());
        }

        // Verify critical lifecycle audit milestones exist
        assertThat(auditTrail).anyMatch(e -> e.getAction() == AuditAction.OWNER_ACTIVITY);
        assertThat(auditTrail).anyMatch(e -> e.getAction() == AuditAction.RELEASE_CLAIMED);
        assertThat(auditTrail).anyMatch(e -> e.getAction() == AuditAction.DISCLOSURE_GENERATED);
        assertThat(auditTrail).anyMatch(e -> e.getAction() == AuditAction.RELEASE_EXECUTED);
    }
}
