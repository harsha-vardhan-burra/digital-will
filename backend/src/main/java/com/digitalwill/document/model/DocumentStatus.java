package com.digitalwill.document.model;

/**
 * Authoritative document processing status lifecycle in the validation pipeline.
 */
public enum DocumentStatus {
    PENDING,
    ACCEPTED,
    REJECTED,
    NEEDS_REVIEW,
    FAILED_RETRYABLE
}
