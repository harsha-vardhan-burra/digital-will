package com.digitalwill.state.model;

/**
 * Domain events that trigger state transitions in the Digital Will lifecycle.
 */
public enum WillEvent {

    /**
     * Inactivity threshold has been exceeded by the owner; first warning to be dispatched.
     */
    INACTIVITY_THRESHOLD_REACHED,

    /**
     * Warning delay elapsed with no verified owner activity; final warning to be dispatched.
     */
    WARNING_ELAPSED,

    /**
     * Final warning delay elapsed with no verified owner activity; trusted contact verification begins.
     */
    FINAL_WARNING_ELAPSED,

    /**
     * Required distinct trusted contacts (e.g. 2-of-3) have confirmed owner unavailability.
     */
    CONFIRMATIONS_SATISFIED,

    /**
     * Safety cancellation window established with a valid release_after timestamp.
     */
    RELEASE_SCHEDULED,

    /**
     * Cancellation window elapsed with no owner cancellation; release execution claimed.
     */
    RELEASE_DELAY_ELAPSED,

    /**
     * Disclosure records and document release tokens successfully generated and completed.
     */
    DISCLOSURE_COMPLETED,

    /**
     * Verified owner activity occurred (login, explicit check-in link), resetting the succession flow.
     */
    OWNER_ACTIVITY_DETECTED,

    /**
     * Explicit owner cancellation of the pending succession flow.
     */
    OWNER_CANCELLED,

    /**
     * Worker crash recovery triggered for a stalled EXECUTING state.
     */
    EXECUTION_RECOVERY_TRIGGERED
}
