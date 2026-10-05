package com.digitalwill.audit.service;

/**
 * Result of audit log chain verification.
 */
public record AuditVerificationResult(
        boolean valid,
        long checkedEntries,
        String failureReason,
        Long failedSequenceNumber
) {
    public static AuditVerificationResult success(long count) {
        return new AuditVerificationResult(true, count, null, null);
    }

    public static AuditVerificationResult failure(long count, String reason, Long sequenceNumber) {
        return new AuditVerificationResult(false, count, reason, sequenceNumber);
    }
}
