package com.digitalwill.state.service;

import com.digitalwill.common.TimeProvider;
import com.digitalwill.state.exception.IllegalStateTransitionException;
import com.digitalwill.state.exception.TransitionGuardFailedException;
import com.digitalwill.state.model.TransitionGuardContext;
import com.digitalwill.state.model.WillEvent;
import com.digitalwill.state.model.WillState;
import com.digitalwill.state.model.WillStateEntity;
import com.digitalwill.state.registry.TransitionRegistry;
import com.digitalwill.state.repository.AtomicWillTransitionRepository;
import com.digitalwill.state.repository.WillStateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Public service interface for the State Engine.
 * Single entry point for inspecting and executing Will state transitions.
 * Enforces authoritative rules, transition registry guards, and atomic database execution.
 */
@Service
public class WillStateService {

    private static final Logger log = LoggerFactory.getLogger(WillStateService.class);

    private final WillStateRepository willStateRepository;
    private final AtomicWillTransitionRepository atomicTransitionRepository;
    private final TransitionRegistry transitionRegistry;
    private final TimeProvider timeProvider;
    private final jakarta.persistence.EntityManager entityManager;

    public WillStateService(WillStateRepository willStateRepository,
                            AtomicWillTransitionRepository atomicTransitionRepository,
                            TransitionRegistry transitionRegistry,
                            TimeProvider timeProvider) {
        this(willStateRepository, atomicTransitionRepository, transitionRegistry, timeProvider, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public WillStateService(WillStateRepository willStateRepository,
                            AtomicWillTransitionRepository atomicTransitionRepository,
                            TransitionRegistry transitionRegistry,
                            TimeProvider timeProvider,
                            @org.springframework.beans.factory.annotation.Autowired(required = false) jakarta.persistence.EntityManager entityManager) {
        this.willStateRepository = Objects.requireNonNull(willStateRepository, "willStateRepository must not be null");
        this.atomicTransitionRepository = Objects.requireNonNull(atomicTransitionRepository, "atomicTransitionRepository must not be null");
        this.transitionRegistry = Objects.requireNonNull(transitionRegistry, "transitionRegistry must not be null");
        this.timeProvider = Objects.requireNonNull(timeProvider, "timeProvider must not be null");
        this.entityManager = entityManager;
    }

    private void clearCache() {
        if (entityManager != null) {
            entityManager.clear();
        }
    }

    /**
     * Initializes a new Digital Will in the ACTIVE state.
     */
    @Transactional
    public WillStateEntity createWill(UUID ownerId, String title) {
        Instant now = timeProvider.now();
        UUID willId = UUID.randomUUID();
        WillStateEntity entity = new WillStateEntity(willId, ownerId, title, WillState.ACTIVE, now, now);
        return willStateRepository.save(entity);
    }

    public Optional<WillStateEntity> findWill(UUID willId) {
        return willStateRepository.findById(willId);
    }

    public WillStateEntity getWillOrThrow(UUID willId) {
        clearCache();
        return willStateRepository.findById(willId)
                .orElseThrow(() -> new IllegalArgumentException("Digital Will not found with ID: " + willId));
    }

    /**
     * Transitions ACTIVE -> INACTIVITY_WARNING if inactivity threshold is satisfied.
     */
    @Transactional
    public boolean triggerInactivityWarning(UUID willId, Duration inactivityThreshold) {
        WillStateEntity will = getWillOrThrow(willId);
        Instant now = timeProvider.now();

        TransitionGuardContext context = TransitionGuardContext.builder(now)
                .lastVerifiedActivityAt(will.getLastVerifiedActivityAt())
                .inactivityThreshold(inactivityThreshold)
                .build();

        transitionRegistry.evaluateTransition(will.getState(), WillEvent.INACTIVITY_THRESHOLD_REACHED, context);

        boolean claimed = atomicTransitionRepository.claimInactivityWarning(willId, now);
        if (claimed) {
            clearCache();
            log.info("Digital Will [{}] claimed INACTIVITY_WARNING transition at {}", willId, now);
        }
        return claimed;
    }

    /**
     * Transitions INACTIVITY_WARNING -> FINAL_WARNING if warning delay has elapsed with no activity.
     */
    @Transactional
    public boolean triggerFinalWarning(UUID willId, Duration warningDelay) {
        WillStateEntity will = getWillOrThrow(willId);
        Instant now = timeProvider.now();

        TransitionGuardContext context = TransitionGuardContext.builder(now)
                .warningSentAt(will.getWarningSentAt())
                .warningDelay(warningDelay)
                .lastVerifiedActivityAt(will.getLastVerifiedActivityAt())
                .build();

        transitionRegistry.evaluateTransition(will.getState(), WillEvent.WARNING_ELAPSED, context);

        boolean claimed = atomicTransitionRepository.claimFinalWarning(willId, now);
        if (claimed) {
            clearCache();
            log.info("Digital Will [{}] claimed FINAL_WARNING transition at {}", willId, now);
        }
        return claimed;
    }

    /**
     * Transitions FINAL_WARNING -> VERIFICATION_PENDING if final warning delay elapsed with no activity.
     */
    @Transactional
    public boolean triggerVerificationPending(UUID willId, Duration finalWarningDelay) {
        WillStateEntity will = getWillOrThrow(willId);
        Instant now = timeProvider.now();

        TransitionGuardContext context = TransitionGuardContext.builder(now)
                .finalWarningSentAt(will.getFinalWarningSentAt())
                .finalWarningDelay(finalWarningDelay)
                .lastVerifiedActivityAt(will.getLastVerifiedActivityAt())
                .build();

        transitionRegistry.evaluateTransition(will.getState(), WillEvent.FINAL_WARNING_ELAPSED, context);

        boolean claimed = atomicTransitionRepository.claimVerificationPending(willId, now);
        if (claimed) {
            clearCache();
            log.info("Digital Will [{}] claimed VERIFICATION_PENDING transition at {}", willId, now);
        }
        return claimed;
    }

    /**
     * Transitions VERIFICATION_PENDING -> VERIFIED when distinct trusted contacts confirmation requirement is met.
     */
    @Transactional
    public boolean triggerVerified(UUID willId, int distinctConfirmations, int requiredConfirmations) {
        WillStateEntity will = getWillOrThrow(willId);
        Instant now = timeProvider.now();

        TransitionGuardContext context = TransitionGuardContext.builder(now)
                .distinctConfirmations(distinctConfirmations)
                .requiredConfirmations(requiredConfirmations)
                .build();

        transitionRegistry.evaluateTransition(will.getState(), WillEvent.CONFIRMATIONS_SATISFIED, context);

        boolean claimed = atomicTransitionRepository.claimVerified(willId, now);
        if (claimed) {
            clearCache();
            log.info("Digital Will [{}] claimed VERIFIED transition at {} with {}/{} confirmations",
                    willId, now, distinctConfirmations, requiredConfirmations);
        }
        return claimed;
    }

    /**
     * Transitions VERIFIED -> RELEASE_PENDING establishing the safety cancellation window.
     */
    @Transactional
    public boolean scheduleRelease(UUID willId, Instant releaseAfter) {
        WillStateEntity will = getWillOrThrow(willId);
        Instant now = timeProvider.now();

        TransitionGuardContext context = TransitionGuardContext.builder(now)
                .releaseAfter(releaseAfter)
                .build();

        transitionRegistry.evaluateTransition(will.getState(), WillEvent.RELEASE_SCHEDULED, context);

        boolean scheduled = atomicTransitionRepository.scheduleRelease(willId, releaseAfter, now);
        if (scheduled) {
            clearCache();
            log.info("Digital Will [{}] scheduled RELEASE_PENDING at {} with releaseAfter={}", willId, now, releaseAfter);
        }
        return scheduled;
    }

    /**
     * Atomically claims execution from RELEASE_PENDING -> EXECUTING.
     * Guard: state = RELEASE_PENDING AND release_after <= now AND cancelled_at IS NULL.
     * Concurrency rule: exactly 1 worker can claim; other workers receive false (0 rows updated).
     */
    @Transactional
    public boolean claimExecution(UUID willId) {
        WillStateEntity will = getWillOrThrow(willId);
        Instant now = timeProvider.now();

        TransitionGuardContext context = TransitionGuardContext.builder(now)
                .releaseAfter(will.getReleaseAfter())
                .cancelledAt(will.getCancelledAt())
                .build();

        transitionRegistry.evaluateTransition(will.getState(), WillEvent.RELEASE_DELAY_ELAPSED, context);

        boolean claimed = atomicTransitionRepository.claimExecuting(willId, now, now);
        if (claimed) {
            clearCache();
            log.info("Worker successfully claimed EXECUTING for Will [{}] at {}", willId, now);
        } else {
            log.warn("Worker failed to claim EXECUTING for Will [{}] (0 rows updated, likely raced or ineligible)", willId);
        }
        return claimed;
    }

    /**
     * Transitions EXECUTING -> EXECUTED upon verified completion of estate disclosure.
     * Terminal transition.
     */
    @Transactional
    public boolean completeExecution(UUID willId) {
        WillStateEntity will = getWillOrThrow(willId);
        Instant now = timeProvider.now();

        TransitionGuardContext context = TransitionGuardContext.builder(now)
                .disclosureCompleted(true)
                .build();

        transitionRegistry.evaluateTransition(will.getState(), WillEvent.DISCLOSURE_COMPLETED, context);

        boolean completed = atomicTransitionRepository.completeExecution(willId, now, now);
        if (completed) {
            clearCache();
            log.info("Digital Will [{}] reached terminal state EXECUTED at {}", willId, now);
        }
        return completed;
    }

    /**
     * Records verified owner activity.
     * If the Will is in an active succession progression (INACTIVITY_WARNING, FINAL_WARNING,
     * VERIFICATION_PENDING, RELEASE_PENDING), atomically resets state to ACTIVE.
     * If already ACTIVE, updates last_verified_activity_at.
     * Cannot reset if EXECUTED or EXECUTING (unless recovered).
     */
    @Transactional
    public boolean recordVerifiedActivity(UUID willId, Instant activityTimestamp) {
        WillStateEntity will = getWillOrThrow(willId);
        Instant now = timeProvider.now();

        if (will.getState() == WillState.ACTIVE) {
            will.setLastVerifiedActivityAt(activityTimestamp);
            will.setUpdatedAt(now);
            willStateRepository.save(will);
            clearCache();
            log.info("Updated lastVerifiedActivityAt for ACTIVE Will [{}]", willId);
            return true;
        }

        if (will.getState().canResetToActive()) {
            boolean reset = atomicTransitionRepository.resetToActive(willId, activityTimestamp, now, null);
            if (reset) {
                clearCache();
                log.info("Reset Will [{}] from {} to ACTIVE due to verified owner activity", willId, will.getState());
            }
            return reset;
        }

        log.warn("Cannot reset Will [{}] from state [{}]: state does not permit reset to ACTIVE", willId, will.getState());
        return false;
    }

    /**
     * Explicit owner cancellation of pending succession workflow.
     */
    @Transactional
    public boolean cancelSuccession(UUID willId) {
        WillStateEntity will = getWillOrThrow(willId);
        Instant now = timeProvider.now();

        if (!will.getState().canResetToActive()) {
            throw new IllegalStateTransitionException(will.getState(), WillEvent.OWNER_CANCELLED);
        }

        boolean reset = atomicTransitionRepository.resetToActive(willId, now, now, now);
        if (reset) {
            clearCache();
            log.info("Owner cancelled pending succession for Will [{}], reset to ACTIVE", willId);
        }
        return reset;
    }

    /**
     * Recovers a stalled EXECUTING state back to RELEASE_PENDING if worker crashed and timeout elapsed.
     */
    @Transactional
    public boolean recoverStaleExecution(UUID willId, Duration recoveryTimeout) {
        WillStateEntity will = getWillOrThrow(willId);
        Instant now = timeProvider.now();

        TransitionGuardContext context = TransitionGuardContext.builder(now)
                .executingAt(will.getExecutingAt())
                .executionRecoveryTimeout(recoveryTimeout)
                .disclosureCompleted(false)
                .build();

        transitionRegistry.evaluateTransition(will.getState(), WillEvent.EXECUTION_RECOVERY_TRIGGERED, context);

        Instant cutoff = now.minus(recoveryTimeout);
        boolean recovered = atomicTransitionRepository.recoverStaleExecuting(willId, cutoff, now);
        if (recovered) {
            clearCache();
            log.info("Recovered stalled EXECUTING Will [{}] back to RELEASE_PENDING at {}", willId, now);
        }
        return recovered;
    }
}
