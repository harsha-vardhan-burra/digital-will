package com.digitalwill.state.exception;

import com.digitalwill.state.model.WillEvent;
import com.digitalwill.state.model.WillState;

/**
 * Thrown when an illegal state transition is attempted (i.e. No valid transition rule exists
 * from the current state on the given event).
 */
public class IllegalStateTransitionException extends RuntimeException {

    private final WillState fromState;
    private final WillEvent event;

    public IllegalStateTransitionException(WillState fromState, WillEvent event) {
        super(String.format("Illegal transition: cannot transition from state [%s] on event [%s]", fromState, event));
        this.fromState = fromState;
        this.event = event;
    }

    public WillState getFromState() {
        return fromState;
    }

    public WillEvent getEvent() {
        return event;
    }
}
