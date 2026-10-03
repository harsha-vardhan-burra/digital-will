package com.digitalwill.state.model;

/**
 * Result of evaluating a transition guard condition.
 */
public record TransitionGuardResult(boolean isSatisfied, String reason) {

    private static final TransitionGuardResult SATISFIED = new TransitionGuardResult(true, "Guard condition satisfied");

    public static TransitionGuardResult satisfied() {
        return SATISFIED;
    }

    public static TransitionGuardResult violated(String reason) {
        return new TransitionGuardResult(false, reason);
    }
}
