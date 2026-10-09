package com.digitalwill.audit.service;

import com.digitalwill.audit.exception.AuditPersistenceException;
import com.digitalwill.audit.model.AuditAction;
import com.digitalwill.audit.model.AuditLogEntry;
import com.digitalwill.audit.model.AuditResourceType;
import com.digitalwill.audit.model.AuditStatus;
import com.digitalwill.audit.repository.AuditLogRepository;
import com.digitalwill.common.TimeProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Service managing the tamper-evident hash-chained audit logging system (PR4).
 * Guarantees:
 * - Deterministic SHA-256 canonical hashing across every log entry
 * - Strict cryptographic hash chaining: entry_hash(N) = SHA-256(canonical(N) + entry_hash(N-1))
 * - Fail-closed policy for critical actions: failure to persist raises AuditPersistenceException
 * - Verification methods to detect any tampering, modification, or omission
 */
@Service
public class AuditLogService {

    private static final Logger log = LoggerFactory.getLogger(AuditLogService.class);
    public static final String GENESIS_PREV_HASH = "0".repeat(64);

    private final AuditLogRepository auditLogRepository;
    private final TimeProvider timeProvider;
    private final Object auditLock = new Object();
    private final org.springframework.transaction.support.TransactionTemplate transactionTemplate;

    @org.springframework.beans.factory.annotation.Autowired
    public AuditLogService(AuditLogRepository auditLogRepository,
                           TimeProvider timeProvider,
                           org.springframework.transaction.PlatformTransactionManager transactionManager) {
        this.auditLogRepository = Objects.requireNonNull(auditLogRepository, "auditLogRepository must not be null");
        this.timeProvider = Objects.requireNonNull(timeProvider, "timeProvider must not be null");
        this.transactionTemplate = transactionManager != null ?
                new org.springframework.transaction.support.TransactionTemplate(transactionManager) : null;
    }

    public AuditLogService(AuditLogRepository auditLogRepository, TimeProvider timeProvider) {
        this(auditLogRepository, timeProvider, null);
    }

    /**
     * Logs an audit event with fail-closed semantics for critical security events.
     * Synchronized on auditLock with transactionTemplate ensuring commits complete before lock release.
     */
    public AuditLogEntry logEvent(UUID willId, String actorId, String actorType,
                                  AuditAction action, AuditStatus status,
                                  AuditResourceType resourceType, String resourceId,
                                  String detailsJson, boolean isCritical) {
        synchronized (auditLock) {
            try {
                if (transactionTemplate != null) {
                    return transactionTemplate.execute(txStatus ->
                            doLogEvent(willId, actorId, actorType, action, status, resourceType, resourceId, detailsJson));
                } else {
                    return doLogEvent(willId, actorId, actorType, action, status, resourceType, resourceId, detailsJson);
                }
            } catch (Exception e) {
                String errorMsg = "Failed to record audit log for action " + action + ": " + e.getMessage();
                log.error(errorMsg, e);
                if (isCritical) {
                    // Fail closed: abort calling transaction
                    throw new AuditPersistenceException(errorMsg, e);
                }
                return null;
            }
        }
    }

    private AuditLogEntry doLogEvent(UUID willId, String actorId, String actorType,
                                     AuditAction action, AuditStatus status,
                                     AuditResourceType resourceType, String resourceId,
                                     String detailsJson) {
        Instant now = timeProvider.now();
        Optional<AuditLogEntry> latestEntryOpt = auditLogRepository.findTopByOrderBySequenceNumberDesc();

        long nextSequence = latestEntryOpt.map(e -> e.getSequenceNumber() + 1).orElse(1L);
        String prevHash = latestEntryOpt.map(AuditLogEntry::getEntryHash).orElse(GENESIS_PREV_HASH);

        String canonicalPayload = computeCanonicalString(
                nextSequence, now, willId, actorType, actorId, action, status, resourceType, resourceId, detailsJson, prevHash
        );
        String entryHash = computeSha256Hex(canonicalPayload);

        AuditLogEntry entry = new AuditLogEntry(
                UUID.randomUUID(),
                nextSequence,
                willId,
                actorId,
                actorType,
                action,
                status,
                resourceType,
                resourceId,
                detailsJson,
                now,
                prevHash,
                entryHash
        );

        AuditLogEntry saved = auditLogRepository.saveAndFlush(entry);
        log.info("Audit entry [{}] recorded: action=[{}] actor=[{}] willId=[{}] status=[{}] hash=[{}]",
                nextSequence, action, actorId, willId, status, entryHash);
        return saved;
    }

    /**
     * Convenience method for critical audit events (always fails closed).
     */
    public AuditLogEntry logCritical(UUID willId, String actorId, String actorType,
                                     AuditAction action, AuditStatus status,
                                     AuditResourceType resourceType, String resourceId,
                                     String detailsJson) {
        return logEvent(willId, actorId, actorType, action, status, resourceType, resourceId, detailsJson, true);
    }

    /**
     * Convenience method for non-critical audit events.
     */
    public AuditLogEntry logNonCritical(UUID willId, String actorId, String actorType,
                                        AuditAction action, AuditStatus status,
                                        AuditResourceType resourceType, String resourceId,
                                        String detailsJson) {
        return logEvent(willId, actorId, actorType, action, status, resourceType, resourceId, detailsJson, false);
    }

    /**
     * Verifies the global audit log hash chain integrity from genesis to current tip.
     */
    @Transactional(readOnly = true)
    public AuditVerificationResult verifyGlobalIntegrity() {
        List<AuditLogEntry> entries = auditLogRepository.findAllByOrderBySequenceNumberAsc();
        return verifyChainEntries(entries);
    }

    /**
     * Verifies the audit log chain for entries associated with a specific will.
     */
    @Transactional(readOnly = true)
    public AuditVerificationResult verifyWillChainIntegrity(UUID willId) {
        // Must verify global sequence as hash chain is linearly bound across the system
        return verifyGlobalIntegrity();
    }

    public List<AuditLogEntry> getAuditHistoryForWill(UUID willId) {
        return auditLogRepository.findByWillIdOrderBySequenceNumberAsc(willId);
    }

    public static String computeCanonicalString(long sequenceNumber, Instant createdAt, UUID willId,
                                                String actorType, String actorId, AuditAction action,
                                                AuditStatus status, AuditResourceType resourceType,
                                                String resourceId, String detailsJson, String prevHash) {
        return sequenceNumber + "|" +
                createdAt.toString() + "|" +
                (willId != null ? willId.toString() : "") + "|" +
                actorType + "|" +
                actorId + "|" +
                action.name() + "|" +
                status.name() + "|" +
                resourceType.name() + "|" +
                (resourceId != null ? resourceId : "") + "|" +
                (detailsJson != null ? detailsJson.trim() : "{}") + "|" +
                prevHash;
    }

    public static String computeSha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    private AuditVerificationResult verifyChainEntries(List<AuditLogEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return AuditVerificationResult.success(0);
        }

        String expectedPrevHash = GENESIS_PREV_HASH;
        long expectedSequence = 1L;

        for (AuditLogEntry entry : entries) {
            if (entry.getSequenceNumber() != expectedSequence) {
                return AuditVerificationResult.failure(
                        entries.size(),
                        "Sequence discontinuity: expected " + expectedSequence + " but found " + entry.getSequenceNumber(),
                        entry.getSequenceNumber()
                );
            }

            if (!expectedPrevHash.equals(entry.getPrevHash())) {
                return AuditVerificationResult.failure(
                        entries.size(),
                        "Broken hash chain at sequence " + entry.getSequenceNumber() +
                                ": expected prev_hash=" + expectedPrevHash + " but found " + entry.getPrevHash(),
                        entry.getSequenceNumber()
                );
            }

            String canonical = computeCanonicalString(
                    entry.getSequenceNumber(),
                    entry.getCreatedAt(),
                    entry.getWillId(),
                    entry.getActorType(),
                    entry.getActorId(),
                    entry.getAction(),
                    entry.getStatus(),
                    entry.getResourceType(),
                    entry.getResourceId(),
                    entry.getDetailsJson(),
                    entry.getPrevHash()
            );
            String computedHash = computeSha256Hex(canonical);

            if (!computedHash.equals(entry.getEntryHash())) {
                return AuditVerificationResult.failure(
                        entries.size(),
                        "Tampering detected at sequence " + entry.getSequenceNumber() +
                                ": entry hash mismatch. Recomputed=" + computedHash + " vs Recorded=" + entry.getEntryHash(),
                        entry.getSequenceNumber()
                );
            }

            expectedPrevHash = entry.getEntryHash();
            expectedSequence++;
        }

        return AuditVerificationResult.success(entries.size());
    }
}
