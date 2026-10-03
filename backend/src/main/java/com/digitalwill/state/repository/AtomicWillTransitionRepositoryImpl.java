package com.digitalwill.state.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Implementation of {@link AtomicWillTransitionRepository} executing guarded database transitions.
 * Guarantees atomicity and idempotency via row-level conditional UPDATE statements.
 */
@Repository
public class AtomicWillTransitionRepositoryImpl implements AtomicWillTransitionRepository {

    private final JdbcTemplate jdbcTemplate;

    public AtomicWillTransitionRepositoryImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
    }

    @Override
    public boolean claimInactivityWarning(UUID willId, Instant now) {
        String sql = """
                UPDATE wills
                SET state = 'INACTIVITY_WARNING',
                    warning_sent_at = ?,
                    updated_at = ?,
                    version = version + 1
                WHERE id = ?
                  AND state = 'ACTIVE'
                """;
        int updated = jdbcTemplate.update(
                sql,
                Timestamp.from(now),
                Timestamp.from(now),
                willId
        );
        return updated == 1;
    }

    @Override
    public boolean claimFinalWarning(UUID willId, Instant now) {
        String sql = """
                UPDATE wills
                SET state = 'FINAL_WARNING',
                    final_warning_sent_at = ?,
                    updated_at = ?,
                    version = version + 1
                WHERE id = ?
                  AND state = 'INACTIVITY_WARNING'
                  AND warning_sent_at IS NOT NULL
                """;
        int updated = jdbcTemplate.update(
                sql,
                Timestamp.from(now),
                Timestamp.from(now),
                willId
        );
        return updated == 1;
    }

    @Override
    public boolean claimVerificationPending(UUID willId, Instant now) {
        String sql = """
                UPDATE wills
                SET state = 'VERIFICATION_PENDING',
                    verification_started_at = ?,
                    updated_at = ?,
                    version = version + 1
                WHERE id = ?
                  AND state = 'FINAL_WARNING'
                  AND final_warning_sent_at IS NOT NULL
                """;
        int updated = jdbcTemplate.update(
                sql,
                Timestamp.from(now),
                Timestamp.from(now),
                willId
        );
        return updated == 1;
    }

    @Override
    public boolean claimVerified(UUID willId, Instant now) {
        String sql = """
                UPDATE wills
                SET state = 'VERIFIED',
                    verified_at = ?,
                    updated_at = ?,
                    version = version + 1
                WHERE id = ?
                  AND state = 'VERIFICATION_PENDING'
                """;
        int updated = jdbcTemplate.update(
                sql,
                Timestamp.from(now),
                Timestamp.from(now),
                willId
        );
        return updated == 1;
    }

    @Override
    public boolean scheduleRelease(UUID willId, Instant releaseAfter, Instant now) {
        String sql = """
                UPDATE wills
                SET state = 'RELEASE_PENDING',
                    release_after = ?,
                    updated_at = ?,
                    version = version + 1
                WHERE id = ?
                  AND state = 'VERIFIED'
                """;
        int updated = jdbcTemplate.update(
                sql,
                Timestamp.from(releaseAfter),
                Timestamp.from(now),
                willId
        );
        return updated == 1;
    }

    @Override
    public boolean claimExecuting(UUID willId, Instant now, Instant executingAt) {
        String sql = """
                UPDATE wills
                SET state = 'EXECUTING',
                    executing_at = ?,
                    updated_at = ?,
                    version = version + 1
                WHERE id = ?
                  AND state = 'RELEASE_PENDING'
                  AND release_after <= ?
                  AND cancelled_at IS NULL
                """;
        int updated = jdbcTemplate.update(
                sql,
                Timestamp.from(executingAt),
                Timestamp.from(now),
                willId,
                Timestamp.from(now)
        );
        return updated == 1;
    }

    @Override
    public boolean completeExecution(UUID willId, Instant now, Instant executedAt) {
        String sql = """
                UPDATE wills
                SET state = 'EXECUTED',
                    executed_at = ?,
                    updated_at = ?,
                    version = version + 1
                WHERE id = ?
                  AND state = 'EXECUTING'
                """;
        int updated = jdbcTemplate.update(
                sql,
                Timestamp.from(executedAt),
                Timestamp.from(now),
                willId
        );
        return updated == 1;
    }

    @Override
    public boolean resetToActive(UUID willId, Instant verifiedActivityAt, Instant now, Instant cancelledAt) {
        String sql = """
                UPDATE wills
                SET state = 'ACTIVE',
                    last_verified_activity_at = ?,
                    warning_sent_at = NULL,
                    final_warning_sent_at = NULL,
                    verification_started_at = NULL,
                    verified_at = NULL,
                    release_after = NULL,
                    executing_at = NULL,
                    cancelled_at = ?,
                    updated_at = ?,
                    version = version + 1
                WHERE id = ?
                  AND state IN ('INACTIVITY_WARNING', 'FINAL_WARNING', 'VERIFICATION_PENDING', 'RELEASE_PENDING')
                """;
        int updated = jdbcTemplate.update(
                sql,
                Timestamp.from(verifiedActivityAt),
                cancelledAt != null ? Timestamp.from(cancelledAt) : null,
                Timestamp.from(now),
                willId
        );
        return updated == 1;
    }

    @Override
    public boolean recoverStaleExecuting(UUID willId, Instant recoveryCutoff, Instant now) {
        String sql = """
                UPDATE wills
                SET state = 'RELEASE_PENDING',
                    executing_at = NULL,
                    updated_at = ?,
                    version = version + 1
                WHERE id = ?
                  AND state = 'EXECUTING'
                  AND executing_at < ?
                """;
        int updated = jdbcTemplate.update(
                sql,
                Timestamp.from(now),
                willId,
                Timestamp.from(recoveryCutoff)
        );
        return updated == 1;
    }
}
