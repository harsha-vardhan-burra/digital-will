package com.digitalwill.state.model;

/**
 * Authoritative states for a Digital Will lifecycle.
 * Defined strictly per Section 15 of the specification.
 */
public enum WillState {

    /**
     * Normal operational state. The owner is active.
     */
    ACTIVE,

    /**
     * Owner has exceeded inactivity threshold and the first warning has been sent.
     */
    INACTIVITY_WARNING,

    /**
     * Owner still has no verified activity after warning period and final warning has been sent.
     */
    FINAL_WARNING,

    /**
     * Inactivity verified on owner side; trusted contacts must confirm the owner remains unreachable.
     */
    VERIFICATION_PENDING,

    /**
     * Required distinct trusted-contact confirmations (2-of-3) have been received.
     */
    VERIFIED,

    /**
     * Safety and cancellation window is active prior to release execution.
     */
    RELEASE_PENDING,

    /**
     * The system has atomically claimed the release/disclosure operation and is performing it.
     */
    EXECUTING,

    /**
     * Disclosure/release is successfully completed. Terminal state.
     */
    EXECUTED;

    /**
     * Indicates whether this state is terminal (no further transitions permitted).
     */
    public boolean isTerminal() {
        return this == EXECUTED;
    }

    /**
     * Indicates whether owner activity or explicit cancellation can reset the Will back to ACTIVE.
     * Per Section 17, applicable states are:
     * INACTIVITY_WARNING, FINAL_WARNING, VERIFICATION_PENDING, RELEASE_PENDING.
     */
    public boolean canResetToActive() {
        return this == INACTIVITY_WARNING
                || this == FINAL_WARNING
                || this == VERIFICATION_PENDING
                || this == RELEASE_PENDING;
    }
}
