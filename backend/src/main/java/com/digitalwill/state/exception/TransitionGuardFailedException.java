package com.digitalwill.state.exception;

import com.digitalwill.state.model.WillEvent;
import com.digitalwill.state.model.WillState;

/**
 * Thrown when a transition rule exists but its prerequisite guard condition is not satisfied.
 */
public class TransitionGuardFailedException extends RuntimeException {

    private final WillState fromState;
    private final WillState toState;
    private final WillEvent event;
    private final String reason;

    public TransitionGuardFailedException(WillState fromState, WillState toState, WillEvent event, String reason) {
        super(String.format("Transition guard failed from [%s] to [%s] on event [%s]: %s", fromState, toState, event, reason));
        this.fromState = fromState;
        this.toState = toState;
        this.event = event;
        this.reason = reason;
    }

    public WillState getFromState() {
        return fromState;
    }

    public WillState getToState() {
        return toState;
    }

    public WillEvent getEvent() {
        return event;
    }

    public String getReason() {
        return reason;
    }
}
