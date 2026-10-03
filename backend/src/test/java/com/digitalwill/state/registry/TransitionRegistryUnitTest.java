package com.digitalwill.state.registry;

import com.digitalwill.common.TestTimeProvider;
import com.digitalwill.state.exception.IllegalStateTransitionException;
import com.digitalwill.state.exception.TransitionGuardFailedException;
import com.digitalwill.state.model.TransitionGuardContext;
import com.digitalwill.state.model.WillEvent;
import com.digitalwill.state.model.WillState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransitionRegistryUnitTest {

    private TransitionRegistry registry;
    private TestTimeProvider timeProvider;
    private Instant baseTime;

    @BeforeEach
    void setUp() {
        registry = new TransitionRegistry();
        baseTime = Instant.parse("2026-10-01T12:00:00Z");
        timeProvider = new TestTimeProvider(baseTime);
    }

    @Nested
    @DisplayName("Happy Path End-to-End Transitions")
    class HappyPathTransitions {

        @Test
        @DisplayName("1. ACTIVE -> INACTIVITY_WARNING when threshold is reached")
        void activeToInactivityWarning() {
            Instant lastActivity = baseTime.minus(Duration.ofDays(31));
            TransitionGuardContext ctx = TransitionGuardContext.builder(timeProvider.now())
                    .lastVerifiedActivityAt(lastActivity)
                    .inactivityThreshold(Duration.ofDays(30))
                    .build();

            WillState next = registry.evaluateTransition(WillState.ACTIVE, WillEvent.INACTIVITY_THRESHOLD_REACHED, ctx);
            assertThat(next).isEqualTo(WillState.INACTIVITY_WARNING);
        }

        @Test
        @DisplayName("2. INACTIVITY_WARNING -> FINAL_WARNING when warning elapsed with no activity")
        void warningToFinalWarning() {
            Instant warningSent = baseTime.minus(Duration.ofDays(8));
            Instant lastActivity = baseTime.minus(Duration.ofDays(40));
            TransitionGuardContext ctx = TransitionGuardContext.builder(timeProvider.now())
                    .warningSentAt(warningSent)
                    .warningDelay(Duration.ofDays(7))
                    .lastVerifiedActivityAt(lastActivity)
                    .build();

            WillState next = registry.evaluateTransition(WillState.INACTIVITY_WARNING, WillEvent.WARNING_ELAPSED, ctx);
            assertThat(next).isEqualTo(WillState.FINAL_WARNING);
        }

        @Test
        @DisplayName("3. FINAL_WARNING -> VERIFICATION_PENDING when final elapsed with no activity")
        void finalWarningToVerificationPending() {
            Instant finalSent = baseTime.minus(Duration.ofDays(8));
            Instant lastActivity = baseTime.minus(Duration.ofDays(50));
            TransitionGuardContext ctx = TransitionGuardContext.builder(timeProvider.now())
                    .finalWarningSentAt(finalSent)
                    .finalWarningDelay(Duration.ofDays(7))
                    .lastVerifiedActivityAt(lastActivity)
                    .build();

            WillState next = registry.evaluateTransition(WillState.FINAL_WARNING, WillEvent.FINAL_WARNING_ELAPSED, ctx);
            assertThat(next).isEqualTo(WillState.VERIFICATION_PENDING);
        }

        @Test
        @DisplayName("4. VERIFICATION_PENDING -> VERIFIED when distinct confirmations >= required count")
        void verificationPendingToVerified() {
            TransitionGuardContext ctx = TransitionGuardContext.builder(timeProvider.now())
                    .distinctConfirmations(2)
                    .requiredConfirmations(2)
                    .build();

            WillState next = registry.evaluateTransition(WillState.VERIFICATION_PENDING, WillEvent.CONFIRMATIONS_SATISFIED, ctx);
            assertThat(next).isEqualTo(WillState.VERIFIED);
        }

        @Test
        @DisplayName("5. VERIFIED -> RELEASE_PENDING when release is scheduled in future")
        void verifiedToReleasePending() {
            Instant releaseAfter = timeProvider.now().plus(Duration.ofDays(3));
            TransitionGuardContext ctx = TransitionGuardContext.builder(timeProvider.now())
                    .releaseAfter(releaseAfter)
                    .build();

            WillState next = registry.evaluateTransition(WillState.VERIFIED, WillEvent.RELEASE_SCHEDULED, ctx);
            assertThat(next).isEqualTo(WillState.RELEASE_PENDING);
        }

        @Test
        @DisplayName("6. RELEASE_PENDING -> EXECUTING when release delay has elapsed and not cancelled")
        void releasePendingToExecuting() {
            Instant releaseAfter = timeProvider.now().minusSeconds(10);
            TransitionGuardContext ctx = TransitionGuardContext.builder(timeProvider.now())
                    .releaseAfter(releaseAfter)
                    .cancelledAt(null)
                    .build();

            WillState next = registry.evaluateTransition(WillState.RELEASE_PENDING, WillEvent.RELEASE_DELAY_ELAPSED, ctx);
            assertThat(next).isEqualTo(WillState.EXECUTING);
        }

        @Test
        @DisplayName("7. EXECUTING -> EXECUTED when disclosure is completed")
        void executingToExecuted() {
            TransitionGuardContext ctx = TransitionGuardContext.builder(timeProvider.now())
                    .disclosureCompleted(true)
                    .build();

            WillState next = registry.evaluateTransition(WillState.EXECUTING, WillEvent.DISCLOSURE_COMPLETED, ctx);
            assertThat(next).isEqualTo(WillState.EXECUTED);
            assertThat(next.isTerminal()).isTrue();
        }
    }

    @Nested
    @DisplayName("Stage Skipping Prevention (Principle 5)")
    class StageSkippingTests {

        @Test
        @DisplayName("ACTIVE cannot jump directly to VERIFICATION_PENDING")
        void activeCannotJumpToVerificationPending() {
            TransitionGuardContext ctx = TransitionGuardContext.builder(timeProvider.now()).build();
            assertThatThrownBy(() -> registry.evaluateTransition(WillState.ACTIVE, WillEvent.FINAL_WARNING_ELAPSED, ctx))
                    .isInstanceOf(IllegalStateTransitionException.class);
        }

        @Test
        @DisplayName("ACTIVE cannot jump directly to EXECUTED")
        void activeCannotJumpToExecuted() {
            TransitionGuardContext ctx = TransitionGuardContext.builder(timeProvider.now()).build();
            assertThatThrownBy(() -> registry.evaluateTransition(WillState.ACTIVE, WillEvent.DISCLOSURE_COMPLETED, ctx))
                    .isInstanceOf(IllegalStateTransitionException.class);
        }

        @Test
        @DisplayName("ACTIVE cannot jump directly to RELEASE_PENDING")
        void activeCannotJumpToReleasePending() {
            TransitionGuardContext ctx = TransitionGuardContext.builder(timeProvider.now()).build();
            assertThatThrownBy(() -> registry.evaluateTransition(WillState.ACTIVE, WillEvent.RELEASE_SCHEDULED, ctx))
                    .isInstanceOf(IllegalStateTransitionException.class);
        }
    }

    @Nested
    @DisplayName("Guard Condition Failures")
    class GuardConditionViolations {

        @Test
        @DisplayName("ACTIVE -> INACTIVITY_WARNING fails if inactivity threshold not reached")
        void inactivityThresholdNotReached() {
            Instant recentActivity = baseTime.minus(Duration.ofDays(10));
            TransitionGuardContext ctx = TransitionGuardContext.builder(timeProvider.now())
                    .lastVerifiedActivityAt(recentActivity)
                    .inactivityThreshold(Duration.ofDays(30))
                    .build();

            assertThatThrownBy(() -> registry.evaluateTransition(WillState.ACTIVE, WillEvent.INACTIVITY_THRESHOLD_REACHED, ctx))
                    .isInstanceOf(TransitionGuardFailedException.class)
                    .hasMessageContaining("Inactivity threshold not yet reached");
        }

        @Test
        @DisplayName("INACTIVITY_WARNING -> FINAL_WARNING fails if warning notification was never sent")
        void warningNotSent() {
            TransitionGuardContext ctx = TransitionGuardContext.builder(timeProvider.now())
                    .warningSentAt(null)
                    .warningDelay(Duration.ofDays(7))
                    .build();

            assertThatThrownBy(() -> registry.evaluateTransition(WillState.INACTIVITY_WARNING, WillEvent.WARNING_ELAPSED, ctx))
                    .isInstanceOf(TransitionGuardFailedException.class)
                    .hasMessageContaining("Warning notification has not reached SENT status");
        }

        @Test
        @DisplayName("INACTIVITY_WARNING -> FINAL_WARNING fails if owner activity occurred after warning")
        void activityAfterWarning() {
            Instant warningSent = baseTime.minus(Duration.ofDays(8));
            Instant activityAfter = baseTime.minus(Duration.ofDays(2)); // Activity happened 2 days ago!
            TransitionGuardContext ctx = TransitionGuardContext.builder(timeProvider.now())
                    .warningSentAt(warningSent)
                    .warningDelay(Duration.ofDays(7))
                    .lastVerifiedActivityAt(activityAfter)
                    .build();

            assertThatThrownBy(() -> registry.evaluateTransition(WillState.INACTIVITY_WARNING, WillEvent.WARNING_ELAPSED, ctx))
                    .isInstanceOf(TransitionGuardFailedException.class)
                    .hasMessageContaining("Verified owner activity occurred after warning");
        }

        @Test
        @DisplayName("FINAL_WARNING -> VERIFICATION_PENDING fails if final warning delay not elapsed")
        void finalWarningDelayNotElapsed() {
            Instant finalSent = baseTime.minus(Duration.ofDays(3)); // Only 3 days, needs 7
            TransitionGuardContext ctx = TransitionGuardContext.builder(timeProvider.now())
                    .finalWarningSentAt(finalSent)
                    .finalWarningDelay(Duration.ofDays(7))
                    .build();

            assertThatThrownBy(() -> registry.evaluateTransition(WillState.FINAL_WARNING, WillEvent.FINAL_WARNING_ELAPSED, ctx))
                    .isInstanceOf(TransitionGuardFailedException.class)
                    .hasMessageContaining("Final warning delay period has not elapsed");
        }

        @Test
        @DisplayName("VERIFICATION_PENDING -> VERIFIED fails with only 1 confirmation (2 required)")
        void insufficientConfirmations() {
            TransitionGuardContext ctx = TransitionGuardContext.builder(timeProvider.now())
                    .distinctConfirmations(1)
                    .requiredConfirmations(2)
                    .build();

            assertThatThrownBy(() -> registry.evaluateTransition(WillState.VERIFICATION_PENDING, WillEvent.CONFIRMATIONS_SATISFIED, ctx))
                    .isInstanceOf(TransitionGuardFailedException.class)
                    .hasMessageContaining("Insufficient distinct trusted contact confirmations: received 1, required 2");
        }

        @Test
        @DisplayName("RELEASE_PENDING -> EXECUTING fails if release delay has not elapsed")
        void releaseDelayNotElapsed() {
            Instant futureRelease = timeProvider.now().plus(Duration.ofDays(2));
            TransitionGuardContext ctx = TransitionGuardContext.builder(timeProvider.now())
                    .releaseAfter(futureRelease)
                    .build();

            assertThatThrownBy(() -> registry.evaluateTransition(WillState.RELEASE_PENDING, WillEvent.RELEASE_DELAY_ELAPSED, ctx))
                    .isInstanceOf(TransitionGuardFailedException.class)
                    .hasMessageContaining("Release delay has not yet elapsed");
        }

        @Test
        @DisplayName("RELEASE_PENDING -> EXECUTING fails if succession was cancelled by owner")
        void cancelledCannotExecute() {
            Instant pastRelease = timeProvider.now().minusSeconds(10);
            TransitionGuardContext ctx = TransitionGuardContext.builder(timeProvider.now())
                    .releaseAfter(pastRelease)
                    .cancelledAt(baseTime.minusSeconds(5))
                    .build();

            assertThatThrownBy(() -> registry.evaluateTransition(WillState.RELEASE_PENDING, WillEvent.RELEASE_DELAY_ELAPSED, ctx))
                    .isInstanceOf(TransitionGuardFailedException.class)
                    .hasMessageContaining("Digital Will succession has been cancelled");
        }

        @Test
        @DisplayName("EXECUTING -> EXECUTED fails if disclosure is not completed")
        void disclosureNotCompletedFails() {
            TransitionGuardContext ctx = TransitionGuardContext.builder(timeProvider.now())
                    .disclosureCompleted(false)
                    .build();

            assertThatThrownBy(() -> registry.evaluateTransition(WillState.EXECUTING, WillEvent.DISCLOSURE_COMPLETED, ctx))
                    .isInstanceOf(TransitionGuardFailedException.class)
                    .hasMessageContaining("Estate disclosure and document release operations are not completed");
        }
    }

    @Nested
    @DisplayName("Owner Reset and Cancellation (Section 17)")
    class OwnerResetTests {

        @Test
        @DisplayName("Owner activity resets INACTIVITY_WARNING to ACTIVE")
        void resetFromInactivityWarning() {
            TransitionGuardContext ctx = TransitionGuardContext.builder(timeProvider.now()).build();
            WillState next = registry.evaluateTransition(WillState.INACTIVITY_WARNING, WillEvent.OWNER_ACTIVITY_DETECTED, ctx);
            assertThat(next).isEqualTo(WillState.ACTIVE);
        }

        @Test
        @DisplayName("Owner activity resets FINAL_WARNING to ACTIVE")
        void resetFromFinalWarning() {
            TransitionGuardContext ctx = TransitionGuardContext.builder(timeProvider.now()).build();
            WillState next = registry.evaluateTransition(WillState.FINAL_WARNING, WillEvent.OWNER_ACTIVITY_DETECTED, ctx);
            assertThat(next).isEqualTo(WillState.ACTIVE);
        }

        @Test
        @DisplayName("Owner activity resets VERIFICATION_PENDING to ACTIVE")
        void resetFromVerificationPending() {
            TransitionGuardContext ctx = TransitionGuardContext.builder(timeProvider.now()).build();
            WillState next = registry.evaluateTransition(WillState.VERIFICATION_PENDING, WillEvent.OWNER_ACTIVITY_DETECTED, ctx);
            assertThat(next).isEqualTo(WillState.ACTIVE);
        }

        @Test
        @DisplayName("Owner activity resets RELEASE_PENDING to ACTIVE")
        void resetFromReleasePending() {
            TransitionGuardContext ctx = TransitionGuardContext.builder(timeProvider.now()).build();
            WillState next = registry.evaluateTransition(WillState.RELEASE_PENDING, WillEvent.OWNER_ACTIVITY_DETECTED, ctx);
            assertThat(next).isEqualTo(WillState.ACTIVE);
        }

        @Test
        @DisplayName("Owner cancellation resets RELEASE_PENDING to ACTIVE")
        void cancelFromReleasePending() {
            TransitionGuardContext ctx = TransitionGuardContext.builder(timeProvider.now()).build();
            WillState next = registry.evaluateTransition(WillState.RELEASE_PENDING, WillEvent.OWNER_CANCELLED, ctx);
            assertThat(next).isEqualTo(WillState.ACTIVE);
        }
    }

    @Nested
    @DisplayName("Terminal EXECUTED Invariant")
    class TerminalExecutedTests {

        @Test
        @DisplayName("EXECUTED is terminal: cannot reset to ACTIVE")
        void cannotResetFromExecuted() {
            TransitionGuardContext ctx = TransitionGuardContext.builder(timeProvider.now()).build();
            assertThatThrownBy(() -> registry.evaluateTransition(WillState.EXECUTED, WillEvent.OWNER_ACTIVITY_DETECTED, ctx))
                    .isInstanceOf(IllegalStateTransitionException.class);
        }

        @Test
        @DisplayName("EXECUTED is terminal: cannot re-execute")
        void cannotReexecuteFromExecuted() {
            TransitionGuardContext ctx = TransitionGuardContext.builder(timeProvider.now()).build();
            assertThatThrownBy(() -> registry.evaluateTransition(WillState.EXECUTED, WillEvent.DISCLOSURE_COMPLETED, ctx))
                    .isInstanceOf(IllegalStateTransitionException.class);
        }
    }

    @Nested
    @DisplayName("Crash Recovery (Section 22)")
    class CrashRecoveryTests {

        @Test
        @DisplayName("EXECUTING recovers to RELEASE_PENDING when recovery timeout elapsed")
        void executingRecoversAfterTimeout() {
            Instant executingAt = baseTime.minus(Duration.ofMinutes(45)); // Started 45m ago, timeout 30m
            TransitionGuardContext ctx = TransitionGuardContext.builder(timeProvider.now())
                    .executingAt(executingAt)
                    .executionRecoveryTimeout(Duration.ofMinutes(30))
                    .disclosureCompleted(false)
                    .build();

            WillState next = registry.evaluateTransition(WillState.EXECUTING, WillEvent.EXECUTION_RECOVERY_TRIGGERED, ctx);
            assertThat(next).isEqualTo(WillState.RELEASE_PENDING);
        }

        @Test
        @DisplayName("EXECUTING recovery fails if timeout has not yet elapsed")
        void executingRecoveryFailsBeforeTimeout() {
            Instant executingAt = baseTime.minus(Duration.ofMinutes(10)); // Only 10m ago, timeout 30m
            TransitionGuardContext ctx = TransitionGuardContext.builder(timeProvider.now())
                    .executingAt(executingAt)
                    .executionRecoveryTimeout(Duration.ofMinutes(30))
                    .disclosureCompleted(false)
                    .build();

            assertThatThrownBy(() -> registry.evaluateTransition(WillState.EXECUTING, WillEvent.EXECUTION_RECOVERY_TRIGGERED, ctx))
                    .isInstanceOf(TransitionGuardFailedException.class)
                    .hasMessageContaining("Execution recovery timeout has not elapsed yet");
        }
    }
}
