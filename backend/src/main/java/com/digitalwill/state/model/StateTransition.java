package com.digitalwill.state.model;

import java.util.Objects;

/**
 * Encapsulates a valid state transition rule, including its source state, triggering event,
 * target state, and the guard prerequisite that must be satisfied.
 */
public record StateTransition(
        WillState fromState,
        WillEvent event,
        WillState toState,
        TransitionGuard guard,
        String description
) {
    public StateTransition {
        Objects.requireNonNull(fromState, "fromState must not be null");
        Objects.requireNonNull(event, "event must not be null");
        Objects.requireNonNull(toState, "toState must not be null");
        Objects.requireNonNull(guard, "guard must not be null");
        Objects.requireNonNull(description, "description must not be null");
    }
}
