package com.digitalwill.state.model;

/**
 * Functional interface for evaluating whether a proposed state transition is legally permissible.
 */
@FunctionalInterface
public interface TransitionGuard {

    /**
     * Evaluates the guard condition using the provided context.
     *
     * @param context contextual parameters including timestamps and configuration
     * @return result indicating satisfaction or specific violation reason
     */
    TransitionGuardResult evaluate(TransitionGuardContext context);
}
