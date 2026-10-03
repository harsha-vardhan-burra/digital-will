package com.digitalwill.state.repository;

import java.time.Instant;
import java.util.UUID;

/**
 * Repository interface for guarded, atomic database state transitions.
 * Complies with Principle 2 (Atomic state transitions via conditional UPDATEs) and Principle 3 (Idempotency).
 */
public interface AtomicWillTransitionRepository {

    /**
     * Atomically transitions from ACTIVE to INACTIVITY_WARNING.
     *
     * @param willId target Will ID
     * @param now current timestamp
     * @return true if claimed (1 row updated), false otherwise (0 rows updated)
     */
    boolean claimInactivityWarning(UUID willId, Instant now);

    /**
     * Atomically transitions from INACTIVITY_WARNING to FINAL_WARNING.
     *
     * @param willId target Will ID
     * @param now current timestamp
     * @return true if claimed, false otherwise
     */
    boolean claimFinalWarning(UUID willId, Instant now);

    /**
     * Atomically transitions from FINAL_WARNING to VERIFICATION_PENDING.
     *
     * @param willId target Will ID
     * @param now current timestamp
     * @return true if claimed, false otherwise
     */
    boolean claimVerificationPending(UUID willId, Instant now);

    /**
     * Atomically transitions from VERIFICATION_PENDING to VERIFIED.
     *
     * @param willId target Will ID
     * @param now current timestamp
     * @return true if claimed, false otherwise
     */
    boolean claimVerified(UUID willId, Instant now);

    /**
     * Atomically transitions from VERIFIED to RELEASE_PENDING with a designated release_after timestamp.
     *
     * @param willId target Will ID
     * @param releaseAfter future timestamp when release becomes eligible
     * @param now current timestamp
     * @return true if claimed, false otherwise
     */
    boolean scheduleRelease(UUID willId, Instant releaseAfter, Instant now);

    /**
     * Atomically claims execution: transitions from RELEASE_PENDING to EXECUTING.
     * Mandated guard: state = 'RELEASE_PENDING' AND release_after <= :now AND cancelled_at IS NULL.
     *
     * @param willId target Will ID
     * @param now current timestamp
     * @param executingAt timestamp recorded for execution start
     * @return true if 1 row updated, false if 0 rows updated
     */
    boolean claimExecuting(UUID willId, Instant now, Instant executingAt);

    /**
     * Atomically marks execution as complete: transitions from EXECUTING to EXECUTED.
     *
     * @param willId target Will ID
     * @param now current timestamp
     * @param executedAt completion timestamp
     * @return true if 1 row updated, false if 0 rows updated
     */
    boolean completeExecution(UUID willId, Instant now, Instant executedAt);

    /**
     * Atomically resets any active succession progression back to ACTIVE.
     * Applicable from states: INACTIVITY_WARNING, FINAL_WARNING, VERIFICATION_PENDING, RELEASE_PENDING.
     *
     * @param willId target Will ID
     * @param verifiedActivityAt the timestamp of the verified owner activity
     * @param now current timestamp
     * @param cancelledAt timestamp of cancellation (or null if normal activity reset)
     * @return true if reset succeeded (1 row updated), false if in another state or already executed
     */
    boolean resetToActive(UUID willId, Instant verifiedActivityAt, Instant now, Instant cancelledAt);

    /**
     * Recovers a stalled EXECUTING state back to RELEASE_PENDING if executing_at is older than cutoff.
     *
     * @param willId target Will ID
     * @param recoveryCutoff timestamp before which executing_at is considered stalled/crashed
     * @param now current timestamp
     * @return true if recovered (1 row updated), false otherwise
     */
    boolean recoverStaleExecuting(UUID willId, Instant recoveryCutoff, Instant now);
}
