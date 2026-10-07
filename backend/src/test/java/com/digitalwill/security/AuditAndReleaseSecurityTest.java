package com.digitalwill.security;

import com.digitalwill.audit.exception.AuditPersistenceException;
import com.digitalwill.audit.model.AuditAction;
import com.digitalwill.audit.model.AuditLogEntry;
import com.digitalwill.audit.model.AuditResourceType;
import com.digitalwill.audit.model.AuditStatus;
import com.digitalwill.audit.repository.AuditLogRepository;
import com.digitalwill.audit.service.AuditLogService;
import com.digitalwill.audit.service.AuditVerificationResult;
import com.digitalwill.common.TestTimeProvider;
import com.digitalwill.config.TestTimeConfig;
import com.digitalwill.estate.model.Asset;
import com.digitalwill.estate.model.AssetCategory;
import com.digitalwill.estate.model.Beneficiary;
import com.digitalwill.estate.service.EstateService;
import com.digitalwill.job.config.JobProperties;
import com.digitalwill.release.model.ExecutionStatus;
import com.digitalwill.release.model.ReleaseExecution;
import com.digitalwill.release.repository.DisclosedRecordRepository;
import com.digitalwill.release.repository.DisclosureTokenRepository;
import com.digitalwill.release.repository.ReleaseExecutionRepository;
import com.digitalwill.release.service.ReleaseExecutionService;
import com.digitalwill.state.model.WillState;
import com.digitalwill.state.model.WillStateEntity;
import com.digitalwill.state.repository.WillStateRepository;
import com.digitalwill.state.service.WillStateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestTimeConfig.class)
public class AuditAndReleaseSecurityTest {

    @Autowired MockMvc mockMvc;
    @Autowired AuditLogService auditLogService;
    @Autowired AuditLogRepository auditLogRepository;
    @Autowired WillStateService willStateService;
    @Autowired WillStateRepository willStateRepository;
    @Autowired EstateService estateService;
    @Autowired ReleaseExecutionService releaseExecutionService;
    @Autowired ReleaseExecutionRepository releaseExecutionRepository;
    @Autowired DisclosureTokenRepository disclosureTokenRepository;
    @Autowired DisclosedRecordRepository disclosedRecordRepository;
    @Autowired JobProperties jobProperties;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired TestTimeProvider timeProvider;

    private final Instant baseTime = Instant.parse("2026-10-01T10:00:00Z");

    @BeforeEach
    void setUp() {
        timeProvider.setNow(baseTime);
        jdbcTemplate.update("DELETE FROM audit_logs");
    }

    // =========================================================================
    // WORKSTREAM 6: AUDIT CHAIN INTEGRITY & TAMPER DETECTION (ATK-21, ATK-22)
    // =========================================================================

    @Test
    @DisplayName("ATK-21: Untampered audit log chain verifies successfully")
    void auditLog_untamperedChain_verifiesSuccessfully() {
        UUID willId = willStateService.createWill(UUID.randomUUID(), "Audit Test Will").getId();
        auditLogService.logCritical(willId, "USER_1", "OWNER", AuditAction.OWNER_ACTIVITY,
                AuditStatus.SUCCESS, AuditResourceType.WILL, willId.toString(), "{\"step\":1}");
        auditLogService.logCritical(willId, "USER_1", "OWNER", AuditAction.DOCUMENT_UPLOADED,
                AuditStatus.SUCCESS, AuditResourceType.DOCUMENT, UUID.randomUUID().toString(), "{\"step\":2}");

        AuditVerificationResult result = auditLogService.verifyGlobalIntegrity();
        assertThat(result.valid()).isTrue();
        assertThat(result.checkedEntries()).isGreaterThanOrEqualTo(2);
        assertThat(result.failureReason()).isNull();
    }

    @Test
    @DisplayName("ATK-21: Tampering with audit payload is detected by verifyGlobalIntegrity()")
    void auditLog_tamperedPayload_detected() {
        UUID willId = willStateService.createWill(UUID.randomUUID(), "Audit Tamper Payload Will").getId();
        AuditLogEntry entry = auditLogService.logCritical(willId, "USER_1", "OWNER", AuditAction.OWNER_ACTIVITY,
                AuditStatus.SUCCESS, AuditResourceType.WILL, willId.toString(), "{\"original\":true}");

        // Attacker alters details_json in DB directly
        jdbcTemplate.update("UPDATE audit_logs SET details_json = ? WHERE id = ?",
                "{\"original\":false,\"tampered\":true}", entry.getId());

        AuditVerificationResult result = auditLogService.verifyGlobalIntegrity();
        assertThat(result.valid()).isFalse();
        assertThat(result.failureReason()).contains("Tampering detected at sequence " + entry.getSequenceNumber());
        assertThat(result.failedSequenceNumber()).isEqualTo(entry.getSequenceNumber());
    }

    @Test
    @DisplayName("ATK-21: Tampering with prev_hash is detected by verifyGlobalIntegrity()")
    void auditLog_tamperedPrevHash_detected() {
        UUID willId = willStateService.createWill(UUID.randomUUID(), "Audit Tamper PrevHash Will").getId();
        auditLogService.logCritical(willId, "USER_1", "OWNER", AuditAction.OWNER_ACTIVITY,
                AuditStatus.SUCCESS, AuditResourceType.WILL, willId.toString(), "{\"step\":1}");
        AuditLogEntry second = auditLogService.logCritical(willId, "USER_1", "OWNER", AuditAction.DOCUMENT_UPLOADED,
                AuditStatus.SUCCESS, AuditResourceType.DOCUMENT, UUID.randomUUID().toString(), "{\"step\":2}");

        // Attacker modifies prev_hash of second entry
        jdbcTemplate.update("UPDATE audit_logs SET prev_hash = ? WHERE id = ?",
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef", second.getId());

        AuditVerificationResult result = auditLogService.verifyGlobalIntegrity();
        assertThat(result.valid()).isFalse();
        assertThat(result.failureReason()).contains("Broken hash chain at sequence " + second.getSequenceNumber());
        assertThat(result.failedSequenceNumber()).isEqualTo(second.getSequenceNumber());
    }

    @Test
    @DisplayName("ATK-21: Tampering with entry_hash is detected by verifyGlobalIntegrity()")
    void auditLog_tamperedEntryHash_detected() {
        UUID willId = willStateService.createWill(UUID.randomUUID(), "Audit Tamper EntryHash Will").getId();
        AuditLogEntry entry = auditLogService.logCritical(willId, "USER_1", "OWNER", AuditAction.OWNER_ACTIVITY,
                AuditStatus.SUCCESS, AuditResourceType.WILL, willId.toString(), "{\"step\":1}");

        // Attacker alters entry_hash
        jdbcTemplate.update("UPDATE audit_logs SET entry_hash = ? WHERE id = ?",
                "deadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeef", entry.getId());

        AuditVerificationResult result = auditLogService.verifyGlobalIntegrity();
        assertThat(result.valid()).isFalse();
        assertThat(result.failureReason()).contains("Tampering detected at sequence " + entry.getSequenceNumber());
        assertThat(result.failedSequenceNumber()).isEqualTo(entry.getSequenceNumber());
    }

    @Test
    @DisplayName("ATK-21: Deleting an entry from the middle breaks the sequence and is detected")
    void auditLog_deletedEntry_detected() {
        UUID willId = willStateService.createWill(UUID.randomUUID(), "Audit Deletion Will").getId();
        auditLogService.logCritical(willId, "USER_1", "OWNER", AuditAction.OWNER_ACTIVITY,
                AuditStatus.SUCCESS, AuditResourceType.WILL, willId.toString(), "{\"step\":1}");
        AuditLogEntry mid = auditLogService.logCritical(willId, "USER_1", "OWNER", AuditAction.DOCUMENT_UPLOADED,
                AuditStatus.SUCCESS, AuditResourceType.DOCUMENT, UUID.randomUUID().toString(), "{\"step\":2}");
        auditLogService.logCritical(willId, "USER_1", "OWNER", AuditAction.STATE_TRANSITION,
                AuditStatus.SUCCESS, AuditResourceType.WILL, willId.toString(), "{\"step\":3}");

        // Attacker deletes middle entry
        jdbcTemplate.update("DELETE FROM audit_logs WHERE id = ?", mid.getId());

        AuditVerificationResult result = auditLogService.verifyGlobalIntegrity();
        assertThat(result.valid()).isFalse();
        assertThat(result.failureReason()).contains("Sequence discontinuity");
        assertThat(result.failedSequenceNumber()).isEqualTo(mid.getSequenceNumber() + 1);
    }

    @Test
    @DisplayName("ATK-22: logCritical operates in calling transaction and aborts on audit failure (fail-closed)")
    void auditLog_logCritical_failsClosedInTransaction() {
        UUID willId = UUID.randomUUID();
        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);

        assertThrows(AuditPersistenceException.class, () -> {
            txTemplate.execute(status -> {
                // Insert a dummy will record directly in transaction
                willStateService.createWill(willId, "Transactional Fail-Closed Test");
                // Force an audit log failure by passing invalid null parameters that violate non-null constraints
                auditLogService.logCritical(willId, null, null, AuditAction.SECURITY_ALERT,
                        AuditStatus.FAILURE, null, null, null);
                return null;
            });
        });
    }

    // =========================================================================
    // WORKSTREAM 6: INTERNAL JOB ENDPOINT SECURITY (ATK-23, ATK-24)
    // =========================================================================

    @Test
    @DisplayName("ATK-23: Missing X-Internal-Job-Secret header is rejected with 401 Unauthorized")
    void internalJob_missingSecret_rejectedWith401() throws Exception {
        mockMvc.perform(post("/internal/jobs/process-inactivity"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("unauthorized"));

        mockMvc.perform(post("/internal/jobs/process-releases"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("unauthorized"));

        mockMvc.perform(post("/internal/jobs/recover-stalled-executions"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("unauthorized"));
    }

    @Test
    @DisplayName("ATK-23: Invalid X-Internal-Job-Secret header is rejected with 401 Unauthorized")
    void internalJob_invalidSecret_rejectedWith401() throws Exception {
        mockMvc.perform(post("/internal/jobs/process-inactivity")
                        .header("X-Internal-Job-Secret", "attacker-wrong-secret-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("unauthorized"));

        mockMvc.perform(post("/internal/jobs/process-releases")
                        .header("X-Internal-Job-Secret", "attacker-wrong-secret-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("unauthorized"));

        mockMvc.perform(post("/internal/jobs/recover-stalled-executions")
                        .header("X-Internal-Job-Secret", "attacker-wrong-secret-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("unauthorized"));
    }

    @Test
    @DisplayName("ATK-23: Valid X-Internal-Job-Secret header successfully triggers jobs")
    void internalJob_validSecret_authorizedWith200() throws Exception {
        String validSecret = jobProperties.getSecret();

        mockMvc.perform(post("/internal/jobs/process-inactivity")
                        .header("X-Internal-Job-Secret", validSecret))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.executionTimestamp").exists());

        mockMvc.perform(post("/internal/jobs/process-releases")
                        .header("X-Internal-Job-Secret", validSecret))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timestamp").exists());

        mockMvc.perform(post("/internal/jobs/recover-stalled-executions")
                        .header("X-Internal-Job-Secret", validSecret))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timestamp").exists());
    }

    // =========================================================================
    // WORKSTREAM 6: RELEASE EXECUTION & CRASH RECOVERY (ATK-25, ATK-26)
    // =========================================================================

    @Test
    @DisplayName("ATK-25: Duplicate release execution is idempotent and does not regenerate tokens")
    void releaseExecution_idempotentExecution_duplicateSafe() {
        UUID willId = willStateService.createWill(UUID.randomUUID(), "Idempotent Release Will").getId();
        Asset asset = estateService.addAsset(willId, "House", AssetCategory.REAL_ESTATE, "Desc", null, null);
        Beneficiary ben = estateService.addBeneficiary(willId, "Child", "child_" + UUID.randomUUID() + "@example.com", "Child");
        estateService.allocateAsset(willId, asset.getId(), ben.getId(), 100, "100% share");

        // Progress will to RELEASE_PENDING
        timeProvider.advance(Duration.ofDays(181));
        willStateService.triggerInactivityWarning(willId, Duration.ofDays(180));
        timeProvider.advance(Duration.ofDays(8));
        willStateService.triggerFinalWarning(willId, Duration.ofDays(7));
        timeProvider.advance(Duration.ofDays(8));
        willStateService.triggerVerificationPending(willId, Duration.ofDays(7));
        willStateService.triggerVerified(willId, 2, 2);

        Instant releaseAfter = timeProvider.now().plus(Duration.ofDays(3));
        willStateService.scheduleRelease(willId, releaseAfter);
        timeProvider.setNow(releaseAfter.plusSeconds(30));

        // First execution succeeds
        var result1 = releaseExecutionService.claimAndExecuteRelease(willId);
        assertThat(result1.executed()).isTrue();
        assertThat(result1.status()).isEqualTo(ExecutionStatus.COMPLETED);

        long tokenCountAfterFirst = disclosureTokenRepository.findByWillId(willId).size();
        long recordCountAfterFirst = disclosedRecordRepository.findByWillId(willId).size();
        assertThat(tokenCountAfterFirst).isEqualTo(1);
        assertThat(recordCountAfterFirst).isEqualTo(1);

        // Second execution attempt must be completely idempotent
        var result2 = releaseExecutionService.claimAndExecuteRelease(willId);
        assertThat(result2.executed()).isTrue();
        assertThat(result2.status()).isEqualTo(ExecutionStatus.COMPLETED);
        assertThat(result2.message()).contains("already EXECUTED");

        // Verify counts remain unchanged (no duplicates generated)
        assertThat(disclosureTokenRepository.findByWillId(willId)).hasSize(1);
        assertThat(disclosedRecordRepository.findByWillId(willId)).hasSize(1);
    }

    @Test
    @DisplayName("ATK-26: Stale EXECUTING lease recovers after crash timeout and finishes release")
    void releaseExecution_staleExecutionLease_recoversAndCompletes() {
        UUID willId = willStateService.createWill(UUID.randomUUID(), "Crash Recovery Will").getId();
        Asset asset = estateService.addAsset(willId, "Art", AssetCategory.PERSONAL_PROPERTY, "Painting", null, null);
        Beneficiary ben = estateService.addBeneficiary(willId, "Friend", "friend_" + UUID.randomUUID() + "@example.com", "Friend");
        estateService.allocateAsset(willId, asset.getId(), ben.getId(), 100, "Painting to friend");

        // Advance to RELEASE_PENDING
        timeProvider.advance(Duration.ofDays(181));
        willStateService.triggerInactivityWarning(willId, Duration.ofDays(180));
        timeProvider.advance(Duration.ofDays(8));
        willStateService.triggerFinalWarning(willId, Duration.ofDays(7));
        timeProvider.advance(Duration.ofDays(8));
        willStateService.triggerVerificationPending(willId, Duration.ofDays(7));
        willStateService.triggerVerified(willId, 2, 2);

        Instant releaseAfter = timeProvider.now().plus(Duration.ofDays(3));
        willStateService.scheduleRelease(willId, releaseAfter);
        timeProvider.setNow(releaseAfter.plusSeconds(30));

        // Simulate Worker 1 claiming execution and then crashing
        boolean claimed = willStateService.claimExecution(willId);
        assertThat(claimed).isTrue();

        WillStateEntity crashedWill = willStateRepository.findById(willId).orElseThrow();
        assertThat(crashedWill.getState()).isEqualTo(WillState.EXECUTING);

        // Attempting recovery before timeout must fail / do nothing
        Duration timeout = Duration.ofMinutes(15);
        timeProvider.advance(Duration.ofMinutes(5));
        var earlyRecovery = releaseExecutionService.recoverStaleExecution(willId, timeout);
        assertThat(earlyRecovery.executed()).isFalse();
        assertThat(earlyRecovery.message()).contains("has not exceeded timeout");

        // Advance past 15-minute lease timeout
        timeProvider.advance(Duration.ofMinutes(12)); // Total 17 mins elapsed
        var successfulRecovery = releaseExecutionService.recoverStaleExecution(willId, timeout);
        assertThat(successfulRecovery.executed()).isTrue();
        assertThat(successfulRecovery.status()).isEqualTo(ExecutionStatus.COMPLETED);

        // Will is now successfully EXECUTED
        WillStateEntity finalWill = willStateRepository.findById(willId).orElseThrow();
        assertThat(finalWill.getState()).isEqualTo(WillState.EXECUTED);
        assertThat(disclosureTokenRepository.findByWillId(willId)).hasSize(1);
    }

    @Test
    @DisplayName("ATK-26: Max retries exceeded permanently halts execution and alerts without looping")
    void releaseExecution_maxRetriesExceeded_transitionsToFailedPermanent() {
        UUID willId = willStateService.createWill(UUID.randomUUID(), "Max Retries Will").getId();
        estateService.addBeneficiary(willId, "Child", "child_" + UUID.randomUUID() + "@example.com", "Child");

        // Advance to RELEASE_PENDING
        timeProvider.advance(Duration.ofDays(181));
        willStateService.triggerInactivityWarning(willId, Duration.ofDays(180));
        timeProvider.advance(Duration.ofDays(8));
        willStateService.triggerFinalWarning(willId, Duration.ofDays(7));
        timeProvider.advance(Duration.ofDays(8));
        willStateService.triggerVerificationPending(willId, Duration.ofDays(7));
        willStateService.triggerVerified(willId, 2, 2);

        Instant releaseAfter = timeProvider.now().plus(Duration.ofDays(3));
        willStateService.scheduleRelease(willId, releaseAfter);
        timeProvider.setNow(releaseAfter.plusSeconds(30));

        // Claim execution
        willStateService.claimExecution(willId);

        // Simulate ReleaseExecution record with retryCount = 4 (exceeding MAX_RETRY_COUNT = 3)
        ReleaseExecution exec = new ReleaseExecution(UUID.randomUUID(), willId, ExecutionStatus.IN_PROGRESS, timeProvider.now());
        exec.setRetryCount(3); // recoverStaleExecution increments to 4
        releaseExecutionRepository.saveAndFlush(exec);

        Duration timeout = Duration.ofMinutes(15);
        timeProvider.advance(Duration.ofMinutes(20));

        var recoveryResult = releaseExecutionService.recoverStaleExecution(willId, timeout);
        assertThat(recoveryResult.executed()).isFalse();
        assertThat(recoveryResult.status()).isEqualTo(ExecutionStatus.FAILED_PERMANENT);
        assertThat(recoveryResult.message()).contains("Max retries exceeded");

        ReleaseExecution updatedExec = releaseExecutionRepository.findByWillId(willId).orElseThrow();
        assertThat(updatedExec.getStatus()).isEqualTo(ExecutionStatus.FAILED_PERMANENT);
    }
}
