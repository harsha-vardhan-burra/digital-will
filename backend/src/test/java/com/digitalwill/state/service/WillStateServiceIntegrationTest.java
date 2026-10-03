package com.digitalwill.state.service;

import com.digitalwill.common.TestTimeProvider;
import com.digitalwill.config.TestTimeConfig;
import com.digitalwill.state.model.WillState;
import com.digitalwill.state.model.WillStateEntity;
import com.digitalwill.state.repository.WillStateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestTimeConfig.class)
class WillStateServiceIntegrationTest {

    @Autowired
    private WillStateRepository willStateRepository;

    @Autowired
    private WillStateService willStateService;

    @Autowired
    private TestTimeProvider timeProvider;

    private final UUID ownerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        timeProvider.setNow(Instant.parse("2026-10-01T12:00:00Z"));
    }

    @Test
    @DisplayName("Complete end-to-end state lifecycle through database")
    void completeLifecycle() {
        // 1. Create Will in ACTIVE state
        WillStateEntity will = willStateService.createWill(ownerId, "Personal Estate Will");
        assertThat(will.getState()).isEqualTo(WillState.ACTIVE);
        UUID willId = will.getId();

        // 2. Advance time past inactivity threshold (30 days) and trigger warning
        timeProvider.advanceDays(31);
        boolean warningClaimed = willStateService.triggerInactivityWarning(willId, Duration.ofDays(30));
        assertThat(warningClaimed).isTrue();
        WillStateEntity stateWarning = willStateService.getWillOrThrow(willId);
        assertThat(stateWarning.getState()).isEqualTo(WillState.INACTIVITY_WARNING);
        assertThat(stateWarning.getWarningSentAt()).isNotNull();

        // 3. Advance time past warning delay (7 days) and trigger final warning
        timeProvider.advanceDays(8);
        boolean finalClaimed = willStateService.triggerFinalWarning(willId, Duration.ofDays(7));
        assertThat(finalClaimed).isTrue();
        WillStateEntity stateFinal = willStateService.getWillOrThrow(willId);
        assertThat(stateFinal.getState()).isEqualTo(WillState.FINAL_WARNING);
        assertThat(stateFinal.getFinalWarningSentAt()).isNotNull();

        // 4. Advance time past final warning delay (7 days) and initiate contact verification
        timeProvider.advanceDays(8);
        boolean verificationClaimed = willStateService.triggerVerificationPending(willId, Duration.ofDays(7));
        assertThat(verificationClaimed).isTrue();
        WillStateEntity stateVerification = willStateService.getWillOrThrow(willId);
        assertThat(stateVerification.getState()).isEqualTo(WillState.VERIFICATION_PENDING);
        assertThat(stateVerification.getVerificationStartedAt()).isNotNull();

        // 5. Provide 2 distinct confirmations to satisfy 2-of-3 threshold
        boolean verifiedClaimed = willStateService.triggerVerified(willId, 2, 2);
        assertThat(verifiedClaimed).isTrue();
        WillStateEntity stateVerified = willStateService.getWillOrThrow(willId);
        assertThat(stateVerified.getState()).isEqualTo(WillState.VERIFIED);
        assertThat(stateVerified.getVerifiedAt()).isNotNull();

        // 6. Schedule release with 3-day safety delay
        Instant releaseAfter = timeProvider.now().plus(Duration.ofDays(3));
        boolean releaseScheduled = willStateService.scheduleRelease(willId, releaseAfter);
        assertThat(releaseScheduled).isTrue();
        WillStateEntity stateReleasePending = willStateService.getWillOrThrow(willId);
        assertThat(stateReleasePending.getState()).isEqualTo(WillState.RELEASE_PENDING);
        assertThat(stateReleasePending.getReleaseAfter()).isEqualTo(releaseAfter);

        // 7. Advance time past safety delay (4 days) and atomically claim execution
        timeProvider.advanceDays(4);
        boolean executionClaimed = willStateService.claimExecution(willId);
        assertThat(executionClaimed).isTrue();
        WillStateEntity stateExecuting = willStateService.getWillOrThrow(willId);
        assertThat(stateExecuting.getState()).isEqualTo(WillState.EXECUTING);
        assertThat(stateExecuting.getExecutingAt()).isNotNull();

        // 8. Complete execution and reach terminal EXECUTED state
        boolean completed = willStateService.completeExecution(willId);
        assertThat(completed).isTrue();
        WillStateEntity stateExecuted = willStateService.getWillOrThrow(willId);
        assertThat(stateExecuted.getState()).isEqualTo(WillState.EXECUTED);
        assertThat(stateExecuted.getExecutedAt()).isNotNull();
        assertThat(stateExecuted.getState().isTerminal()).isTrue();
    }

    @Test
    @DisplayName("Owner activity resets pending succession flow back to ACTIVE")
    void ownerActivityReset() {
        WillStateEntity will = willStateService.createWill(ownerId, "Reset Test Will");
        UUID willId = will.getId();

        // Advance to INACTIVITY_WARNING
        timeProvider.advanceDays(31);
        willStateService.triggerInactivityWarning(willId, Duration.ofDays(30));
        assertThat(willStateService.getWillOrThrow(willId).getState()).isEqualTo(WillState.INACTIVITY_WARNING);

        // Owner logs in / checks in
        Instant loginTime = timeProvider.now();
        boolean resetSuccess = willStateService.recordVerifiedActivity(willId, loginTime);
        assertThat(resetSuccess).isTrue();

        WillStateEntity resetWill = willStateService.getWillOrThrow(willId);
        assertThat(resetWill.getState()).isEqualTo(WillState.ACTIVE);
        assertThat(resetWill.getLastVerifiedActivityAt()).isEqualTo(loginTime);
        assertThat(resetWill.getWarningSentAt()).isNull();
    }

    @Test
    @DisplayName("Owner explicit cancellation resets RELEASE_PENDING back to ACTIVE")
    void ownerCancellationReset() {
        WillStateEntity will = willStateService.createWill(ownerId, "Cancellation Test Will");
        UUID willId = will.getId();

        // Manually setup Will in RELEASE_PENDING for targeted reset testing
        will.setState(WillState.RELEASE_PENDING);
        will.setReleaseAfter(timeProvider.now().plus(Duration.ofDays(3)));
        willStateRepository.save(will);

        boolean cancelled = willStateService.cancelSuccession(willId);
        assertThat(cancelled).isTrue();

        WillStateEntity afterCancel = willStateService.getWillOrThrow(willId);
        assertThat(afterCancel.getState()).isEqualTo(WillState.ACTIVE);
        assertThat(afterCancel.getCancelledAt()).isNotNull();
        assertThat(afterCancel.getReleaseAfter()).isNull();
    }

    @Test
    @DisplayName("Crash recovery restores stalled EXECUTING state back to RELEASE_PENDING")
    void crashRecovery() {
        WillStateEntity will = willStateService.createWill(ownerId, "Crash Recovery Will");
        UUID willId = will.getId();

        // Simulate crash: Will in EXECUTING with executing_at 45 minutes ago
        will.setState(WillState.EXECUTING);
        will.setExecutingAt(timeProvider.now().minus(Duration.ofMinutes(45)));
        willStateRepository.save(will);

        // Trigger recovery with 30-minute timeout
        boolean recovered = willStateService.recoverStaleExecution(willId, Duration.ofMinutes(30));
        assertThat(recovered).isTrue();

        WillStateEntity afterRecovery = willStateService.getWillOrThrow(willId);
        assertThat(afterRecovery.getState()).isEqualTo(WillState.RELEASE_PENDING);
        assertThat(afterRecovery.getExecutingAt()).isNull();
    }
}
