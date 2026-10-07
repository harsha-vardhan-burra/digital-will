package com.digitalwill.security;

import com.digitalwill.common.TestTimeProvider;
import com.digitalwill.config.TestTimeConfig;
import com.digitalwill.state.exception.IllegalStateTransitionException;
import com.digitalwill.state.model.WillEvent;
import com.digitalwill.state.model.WillState;
import com.digitalwill.state.model.WillStateEntity;
import com.digitalwill.state.registry.TransitionRegistry;
import com.digitalwill.state.repository.WillStateRepository;
import com.digitalwill.state.service.WillStateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@Import(TestTimeConfig.class)
public class StateMachineAbuseSecurityTest {

    @Autowired WillStateService willStateService;
    @Autowired WillStateRepository willStateRepository;
    @Autowired TransitionRegistry transitionRegistry;
    @Autowired TestTimeProvider timeProvider;

    private final Instant baseTime = Instant.parse("2026-10-01T10:00:00Z");
    private UUID willId;
    private UUID ownerId;

    @BeforeEach
    void setUp() {
        timeProvider.setNow(baseTime);
        ownerId = UUID.randomUUID();
        WillStateEntity will = willStateService.createWill(ownerId, "State Machine Security Will");
        willId = will.getId();
    }

    @Test
    @DisplayName("ATK-09: TransitionRegistry rejects illegal transition skipping ACTIVE -> VERIFIED")
    void illegalTransition_activeToVerified_rejected() {
        assertThrows(IllegalStateTransitionException.class, () -> {
            willStateService.triggerVerified(willId, 2, 2);
        });

        WillStateEntity will = willStateService.getWillOrThrow(willId);
        assertThat(will.getState()).isEqualTo(WillState.ACTIVE);
    }

    @Test
    @DisplayName("ATK-09: TransitionRegistry rejects illegal transition skipping ACTIVE -> EXECUTED")
    void illegalTransition_activeToExecuted_rejected() {
        assertThrows(IllegalStateTransitionException.class, () -> {
            willStateService.completeExecution(willId);
        });

        WillStateEntity will = willStateService.getWillOrThrow(willId);
        assertThat(will.getState()).isEqualTo(WillState.ACTIVE);
    }

    @Test
    @DisplayName("ATK-09: TransitionRegistry rejects illegal transition skipping ACTIVE -> RELEASE_PENDING")
    void illegalTransition_activeToReleasePending_rejected() {
        assertThrows(IllegalStateTransitionException.class, () -> {
            willStateService.scheduleRelease(willId, baseTime.plus(Duration.ofDays(7)));
        });

        WillStateEntity will = willStateService.getWillOrThrow(willId);
        assertThat(will.getState()).isEqualTo(WillState.ACTIVE);
    }

    @Test
    @DisplayName("ATK-09: TransitionRegistry rejects illegal transition skipping ACTIVE -> EXECUTING")
    void illegalTransition_activeToExecuting_rejected() {
        assertThrows(IllegalStateTransitionException.class, () -> {
            willStateService.claimExecution(willId);
        });

        WillStateEntity will = willStateService.getWillOrThrow(willId);
        assertThat(will.getState()).isEqualTo(WillState.ACTIVE);
    }

    @Test
    @DisplayName("ATK-10: Terminal EXECUTED state is immutable and rejects any transition")
    void terminalExecuted_isStrictlyImmutable() {
        // Fast-forward will sequentially to EXECUTED
        // 1. ACTIVE -> INACTIVITY_WARNING
        timeProvider.advance(Duration.ofDays(181));
        assertThat(willStateService.triggerInactivityWarning(willId, Duration.ofDays(180))).isTrue();

        // 2. INACTIVITY_WARNING -> FINAL_WARNING
        timeProvider.advance(Duration.ofDays(8));
        assertThat(willStateService.triggerFinalWarning(willId, Duration.ofDays(7))).isTrue();

        // 3. FINAL_WARNING -> VERIFICATION_PENDING
        timeProvider.advance(Duration.ofDays(8));
        assertThat(willStateService.triggerVerificationPending(willId, Duration.ofDays(7))).isTrue();

        // 4. VERIFICATION_PENDING -> VERIFIED
        assertThat(willStateService.triggerVerified(willId, 2, 2)).isTrue();

        // 5. VERIFIED -> RELEASE_PENDING
        Instant releaseAfter = timeProvider.now().plus(Duration.ofDays(3));
        assertThat(willStateService.scheduleRelease(willId, releaseAfter)).isTrue();

        // 6. RELEASE_PENDING -> EXECUTING
        timeProvider.setNow(releaseAfter.plusSeconds(60));
        assertThat(willStateService.claimExecution(willId)).isTrue();

        // 7. EXECUTING -> EXECUTED (Terminal)
        assertThat(willStateService.completeExecution(willId)).isTrue();

        WillStateEntity will = willStateService.getWillOrThrow(willId);
        assertThat(will.getState()).isEqualTo(WillState.EXECUTED);

        // Attempt reset to ACTIVE via owner activity -> must return false
        boolean resetResult = willStateService.recordVerifiedActivity(willId, timeProvider.now());
        assertThat(resetResult).isFalse();

        // Attempt cancel -> must throw IllegalStateTransitionException
        assertThrows(IllegalStateTransitionException.class, () -> {
            willStateService.cancelSuccession(willId);
        });

        // Attempt scheduleRelease again -> must throw IllegalStateTransitionException
        assertThrows(IllegalStateTransitionException.class, () -> {
            willStateService.scheduleRelease(willId, timeProvider.now().plus(Duration.ofDays(1)));
        });

        // Attempt claimExecution again -> must throw IllegalStateTransitionException
        assertThrows(IllegalStateTransitionException.class, () -> {
            willStateService.claimExecution(willId);
        });

        // Final state must remain EXECUTED
        WillStateEntity finalWill = willStateService.getWillOrThrow(willId);
        assertThat(finalWill.getState()).isEqualTo(WillState.EXECUTED);
    }

    @Test
    @DisplayName("ATK-11: Concurrent worker execution claims result in exactly one winner")
    void concurrentExecutionClaims_exactlyOneWinner() throws Exception {
        // Progress will to RELEASE_PENDING
        timeProvider.advance(Duration.ofDays(181));
        willStateService.triggerInactivityWarning(willId, Duration.ofDays(180));
        timeProvider.advance(Duration.ofDays(8));
        willStateService.triggerFinalWarning(willId, Duration.ofDays(7));
        timeProvider.advance(Duration.ofDays(8));
        willStateService.triggerVerificationPending(willId, Duration.ofDays(7));
        willStateService.triggerVerified(willId, 2, 2);

        Instant releaseAfter = timeProvider.now().plus(Duration.ofDays(3));
        willStateService.scheduleRelease(willId, releaseAfter);

        // Advance time past the release delay
        timeProvider.setNow(releaseAfter.plusSeconds(60));

        // Concurrently attempt claimExecution across 8 threads
        int threads = 8;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CyclicBarrier barrier = new CyclicBarrier(threads);
        AtomicInteger successCount = new AtomicInteger(0);

        List<Future<?>> futures = new CopyOnWriteArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(executor.submit(() -> {
                try {
                    barrier.await();
                    boolean claimed = willStateService.claimExecution(willId);
                    if (claimed) {
                        successCount.incrementAndGet();
                    }
                } catch (Exception ignored) {
                }
            }));
        }

        for (Future<?> f : futures) {
            f.get(5, TimeUnit.SECONDS);
        }
        executor.shutdown();

        // Exactly one worker must succeed in claiming EXECUTING
        assertThat(successCount.get()).isEqualTo(1);

        WillStateEntity will = willStateService.getWillOrThrow(willId);
        assertThat(will.getState()).isEqualTo(WillState.EXECUTING);
    }

    @Test
    @DisplayName("ATK-11: Owner activity during warning sequence atomically resets state back to ACTIVE")
    void ownerActivityDuringWarning_resetsToActive() {
        timeProvider.advance(Duration.ofDays(181));
        willStateService.triggerInactivityWarning(willId, Duration.ofDays(180));

        WillStateEntity will = willStateService.getWillOrThrow(willId);
        assertThat(will.getState()).isEqualTo(WillState.INACTIVITY_WARNING);

        // Owner logs in / checks in
        Instant activityTime = timeProvider.now();
        boolean reset = willStateService.recordVerifiedActivity(willId, activityTime);
        assertThat(reset).isTrue();

        WillStateEntity resetWill = willStateService.getWillOrThrow(willId);
        assertThat(resetWill.getState()).isEqualTo(WillState.ACTIVE);
        assertThat(resetWill.getLastVerifiedActivityAt()).isEqualTo(activityTime);
        assertThat(resetWill.getWarningSentAt()).isNull();
    }
}
