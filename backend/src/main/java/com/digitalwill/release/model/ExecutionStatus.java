package com.digitalwill.release.model;

/**
 * Lifecycle states for estate release execution per Section 21 of the specification.
 */
public enum ExecutionStatus {
    NOT_STARTED,
    IN_PROGRESS,
    PARTIALLY_COMPLETED,
    COMPLETED,
    FAILED_RETRYABLE,
    FAILED_PERMANENT
}
