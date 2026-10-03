package com.digitalwill.state.registry;

import com.digitalwill.state.exception.IllegalStateTransitionException;
import com.digitalwill.state.exception.TransitionGuardFailedException;
import com.digitalwill.state.model.StateTransition;
import com.digitalwill.state.model.TransitionGuard;
import com.digitalwill.state.model.TransitionGuardContext;
import com.digitalwill.state.model.TransitionGuardResult;
import com.digitalwill.state.model.WillEvent;
import com.digitalwill.state.model.WillState;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Authoritative registry of all permissible state transitions and their guard conditions.
 * Strict adherence to the state machine defined in Sections 15, 16, 17, and 22.
 */
@Component
public class TransitionRegistry {

    private record TransitionKey(WillState fromState, WillEvent event) {}

    private final Map<TransitionKey, StateTransition> transitions;

    public TransitionRegistry() {
        Map<TransitionKey, StateTransition> map = new HashMap<>();

        // 1. ACTIVE -> INACTIVITY_WARNING (on INACTIVITY_THRESHOLD_REACHED)
        register(map, new StateTransition(
                WillState.ACTIVE,
                WillEvent.INACTIVITY_THRESHOLD_REACHED,
                WillState.INACTIVITY_WARNING,
                this::guardInactivityThreshold,
                "Inactivity threshold exceeded by owner; first warning dispatched"
        ));

        // 2. INACTIVITY_WARNING -> FINAL_WARNING (on WARNING_ELAPSED)
        register(map, new StateTransition(
                WillState.INACTIVITY_WARNING,
                WillEvent.WARNING_ELAPSED,
                WillState.FINAL_WARNING,
                this::guardWarningElapsed,
                "Warning period elapsed with warning sent and no owner activity; final warning dispatched"
        ));

        // 3. FINAL_WARNING -> VERIFICATION_PENDING (on FINAL_WARNING_ELAPSED)
        register(map, new StateTransition(
                WillState.FINAL_WARNING,
                WillEvent.FINAL_WARNING_ELAPSED,
                WillState.VERIFICATION_PENDING,
                this::guardFinalWarningElapsed,
                "Final warning period elapsed with final warning sent and no owner activity; contact verification initiated"
        ));

        // 4. VERIFICATION_PENDING -> VERIFIED (on CONFIRMATIONS_SATISFIED)
        register(map, new StateTransition(
                WillState.VERIFICATION_PENDING,
                WillEvent.CONFIRMATIONS_SATISFIED,
                WillState.VERIFIED,
                this::guardConfirmationsSatisfied,
                "Required distinct trusted contact confirmations received"
        ));

        // 5. VERIFIED -> RELEASE_PENDING (on RELEASE_SCHEDULED)
        register(map, new StateTransition(
                WillState.VERIFIED,
                WillEvent.RELEASE_SCHEDULED,
                WillState.RELEASE_PENDING,
                this::guardReleaseScheduled,
                "Release safety delay scheduled with a valid future timestamp"
        ));

        // 6. RELEASE_PENDING -> EXECUTING (on RELEASE_DELAY_ELAPSED)
        register(map, new StateTransition(
                WillState.RELEASE_PENDING,
                WillEvent.RELEASE_DELAY_ELAPSED,
                WillState.EXECUTING,
                this::guardReleaseDelayElapsed,
                "Release cancellation window elapsed without owner cancellation; claiming execution"
        ));

        // 7. EXECUTING -> EXECUTED (on DISCLOSURE_COMPLETED) - Terminal state
        register(map, new StateTransition(
                WillState.EXECUTING,
                WillEvent.DISCLOSURE_COMPLETED,
                WillState.EXECUTED,
                this::guardDisclosureCompleted,
                "Estate disclosure and document release tokens generated; transition to terminal EXECUTED state"
        ));

        // Reset to ACTIVE on OWNER_ACTIVITY_DETECTED
        register(map, new StateTransition(
                WillState.INACTIVITY_WARNING,
                WillEvent.OWNER_ACTIVITY_DETECTED,
                WillState.ACTIVE,
                context -> TransitionGuardResult.satisfied(),
                "Owner activity detected during inactivity warning; reset to ACTIVE"
        ));
        register(map, new StateTransition(
                WillState.FINAL_WARNING,
                WillEvent.OWNER_ACTIVITY_DETECTED,
                WillState.ACTIVE,
                context -> TransitionGuardResult.satisfied(),
                "Owner activity detected during final warning; reset to ACTIVE"
        ));
        register(map, new StateTransition(
                WillState.VERIFICATION_PENDING,
                WillEvent.OWNER_ACTIVITY_DETECTED,
                WillState.ACTIVE,
                context -> TransitionGuardResult.satisfied(),
                "Owner activity detected during verification pending; reset to ACTIVE and invalidate confirmations"
        ));
        register(map, new StateTransition(
                WillState.RELEASE_PENDING,
                WillEvent.OWNER_ACTIVITY_DETECTED,
                WillState.ACTIVE,
                context -> TransitionGuardResult.satisfied(),
                "Owner activity detected during release pending; cancel release and reset to ACTIVE"
        ));

        // Reset to ACTIVE on explicit OWNER_CANCELLED
        register(map, new StateTransition(
                WillState.INACTIVITY_WARNING,
                WillEvent.OWNER_CANCELLED,
                WillState.ACTIVE,
                context -> TransitionGuardResult.satisfied(),
                "Owner explicitly cancelled during inactivity warning; reset to ACTIVE"
        ));
        register(map, new StateTransition(
                WillState.FINAL_WARNING,
                WillEvent.OWNER_CANCELLED,
                WillState.ACTIVE,
                context -> TransitionGuardResult.satisfied(),
                "Owner explicitly cancelled during final warning; reset to ACTIVE"
        ));
        register(map, new StateTransition(
                WillState.VERIFICATION_PENDING,
                WillEvent.OWNER_CANCELLED,
                WillState.ACTIVE,
                context -> TransitionGuardResult.satisfied(),
                "Owner explicitly cancelled during verification pending; reset to ACTIVE"
        ));
        register(map, new StateTransition(
                WillState.RELEASE_PENDING,
                WillEvent.OWNER_CANCELLED,
                WillState.ACTIVE,
                context -> TransitionGuardResult.satisfied(),
                "Owner explicitly cancelled during release pending; reset to ACTIVE"
        ));

        // Recovery: EXECUTING -> RELEASE_PENDING (on EXECUTION_RECOVERY_TRIGGERED)
        register(map, new StateTransition(
                WillState.EXECUTING,
                WillEvent.EXECUTION_RECOVERY_TRIGGERED,
                WillState.RELEASE_PENDING,
                this::guardExecutionRecovery,
                "Crash recovery: stalled EXECUTING state reverted to RELEASE_PENDING for safe retry"
        ));

        this.transitions = Collections.unmodifiableMap(map);
    }

    private void register(Map<TransitionKey, StateTransition> map, StateTransition transition) {
        map.put(new TransitionKey(transition.fromState(), transition.event()), transition);
    }

    public Optional<StateTransition> findTransition(WillState fromState, WillEvent event) {
        return Optional.ofNullable(transitions.get(new TransitionKey(fromState, event)));
    }

    public StateTransition getTransitionOrThrow(WillState fromState, WillEvent event) {
        return findTransition(fromState, event)
                .orElseThrow(() -> new IllegalStateTransitionException(fromState, event));
    }

    public boolean isTransitionPermitted(WillState fromState, WillEvent event) {
        return transitions.containsKey(new TransitionKey(fromState, event));
    }

    public List<StateTransition> getAllTransitions() {
        return List.copyOf(transitions.values());
    }

    /**
     * Evaluates a proposed transition against registered rules and guards.
     *
     * @param currentState current state of the Will
     * @param event triggering event
     * @param context contextual parameters and timestamps
     * @return the target WillState
     * @throws IllegalStateTransitionException if transition is undefined
     * @throws TransitionGuardFailedException if guard condition is violated
     */
    public WillState evaluateTransition(WillState currentState, WillEvent event, TransitionGuardContext context) {
        StateTransition transition = getTransitionOrThrow(currentState, event);
        TransitionGuardResult guardResult = transition.guard().evaluate(context);
        if (!guardResult.isSatisfied()) {
            throw new TransitionGuardFailedException(currentState, transition.toState(), event, guardResult.reason());
        }
        return transition.toState();
    }

    // --- Guard Implementations ---

    private TransitionGuardResult guardInactivityThreshold(TransitionGuardContext context) {
        if (context.lastVerifiedActivityAt() == null) {
            return TransitionGuardResult.violated("Owner has no recorded verified activity timestamp");
        }
        Instant thresholdInstant = context.lastVerifiedActivityAt().plus(context.inactivityThreshold());
        if (context.now().isBefore(thresholdInstant)) {
            return TransitionGuardResult.violated(String.format(
                    "Inactivity threshold not yet reached. Required after: %s, Current time: %s",
                    thresholdInstant, context.now()));
        }
        return TransitionGuardResult.satisfied();
    }

    private TransitionGuardResult guardWarningElapsed(TransitionGuardContext context) {
        if (context.warningSentAt() == null) {
            return TransitionGuardResult.violated("Warning notification has not reached SENT status");
        }
        Instant warningElapsedInstant = context.warningSentAt().plus(context.warningDelay());
        if (context.now().isBefore(warningElapsedInstant)) {
            return TransitionGuardResult.violated(String.format(
                    "Warning delay period has not elapsed yet. Required after: %s, Current time: %s",
                    warningElapsedInstant, context.now()));
        }
        if (context.lastVerifiedActivityAt() != null && context.lastVerifiedActivityAt().isAfter(context.warningSentAt())) {
            return TransitionGuardResult.violated("Verified owner activity occurred after warning notification was sent");
        }
        return TransitionGuardResult.satisfied();
    }

    private TransitionGuardResult guardFinalWarningElapsed(TransitionGuardContext context) {
        if (context.finalWarningSentAt() == null) {
            return TransitionGuardResult.violated("Final warning notification has not reached SENT status");
        }
        Instant finalElapsedInstant = context.finalWarningSentAt().plus(context.finalWarningDelay());
        if (context.now().isBefore(finalElapsedInstant)) {
            return TransitionGuardResult.violated(String.format(
                    "Final warning delay period has not elapsed yet. Required after: %s, Current time: %s",
                    finalElapsedInstant, context.now()));
        }
        if (context.lastVerifiedActivityAt() != null && context.lastVerifiedActivityAt().isAfter(context.finalWarningSentAt())) {
            return TransitionGuardResult.violated("Verified owner activity occurred after final warning notification was sent");
        }
        return TransitionGuardResult.satisfied();
    }

    private TransitionGuardResult guardConfirmationsSatisfied(TransitionGuardContext context) {
        if (context.distinctConfirmations() < context.requiredConfirmations()) {
            return TransitionGuardResult.violated(String.format(
                    "Insufficient distinct trusted contact confirmations: received %d, required %d",
                    context.distinctConfirmations(), context.requiredConfirmations()));
        }
        return TransitionGuardResult.satisfied();
    }

    private TransitionGuardResult guardReleaseScheduled(TransitionGuardContext context) {
        if (context.releaseAfter() == null) {
            return TransitionGuardResult.violated("release_after timestamp must be provided");
        }
        if (!context.releaseAfter().isAfter(context.now())) {
            return TransitionGuardResult.violated(String.format(
                    "release_after (%s) must be in the future relative to current time (%s)",
                    context.releaseAfter(), context.now()));
        }
        return TransitionGuardResult.satisfied();
    }

    private TransitionGuardResult guardReleaseDelayElapsed(TransitionGuardContext context) {
        if (context.cancelledAt() != null) {
            return TransitionGuardResult.violated("Digital Will succession has been cancelled by the owner");
        }
        if (context.releaseAfter() == null) {
            return TransitionGuardResult.violated("release_after timestamp is missing");
        }
        if (context.now().isBefore(context.releaseAfter())) {
            return TransitionGuardResult.violated(String.format(
                    "Release delay has not yet elapsed. Required after: %s, Current time: %s",
                    context.releaseAfter(), context.now()));
        }
        return TransitionGuardResult.satisfied();
    }

    private TransitionGuardResult guardDisclosureCompleted(TransitionGuardContext context) {
        if (!context.disclosureCompleted()) {
            return TransitionGuardResult.violated("Estate disclosure and document release operations are not completed");
        }
        return TransitionGuardResult.satisfied();
    }

    private TransitionGuardResult guardExecutionRecovery(TransitionGuardContext context) {
        if (context.executingAt() == null) {
            return TransitionGuardResult.violated("Cannot recover execution: executing_at timestamp is null");
        }
        if (context.disclosureCompleted()) {
            return TransitionGuardResult.violated("Cannot recover execution: disclosure has already completed");
        }
        Instant cutoff = context.executingAt().plus(context.executionRecoveryTimeout());
        if (context.now().isBefore(cutoff)) {
            return TransitionGuardResult.violated(String.format(
                    "Execution recovery timeout has not elapsed yet. Executing at: %s, Cutoff: %s, Current time: %s",
                    context.executingAt(), cutoff, context.now()));
        }
        return TransitionGuardResult.satisfied();
    }
}
