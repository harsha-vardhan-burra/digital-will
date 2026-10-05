package com.digitalwill.audit.service;

import com.digitalwill.audit.model.AuditAction;
import com.digitalwill.audit.model.AuditLogEntry;
import com.digitalwill.audit.model.AuditResourceType;
import com.digitalwill.audit.model.AuditStatus;
import com.digitalwill.audit.repository.AuditLogRepository;
import com.digitalwill.common.TestTimeProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

import org.springframework.context.annotation.Import;
import com.digitalwill.config.TestTimeConfig;

import com.digitalwill.state.model.WillState;
import com.digitalwill.state.model.WillStateEntity;
import com.digitalwill.state.repository.WillStateRepository;

@SpringBootTest
@Import(TestTimeConfig.class)
@Transactional
class AuditLogServiceTest {

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private AuditLogService auditLogService;

    @Autowired
    private WillStateRepository willStateRepository;

    @Autowired
    private TestTimeProvider testTimeProvider;

    private final Instant baseTime = Instant.parse("2026-10-05T10:00:00Z");
    private UUID willId;

    @BeforeEach
    void setUp() {
        auditLogRepository.deleteAll();
        testTimeProvider.setNow(baseTime);

        willId = UUID.randomUUID();
        WillStateEntity will = new WillStateEntity(willId, UUID.randomUUID(), "Audit Will", WillState.ACTIVE, baseTime, baseTime);
        willStateRepository.save(will);
    }

    @Test
    @DisplayName("Audit log creates deterministic hash chain from genesis")
    void auditLogChainsDeterministically() {

        AuditLogEntry entry1 = auditLogService.logCritical(
                willId, "owner-1", "OWNER",
                AuditAction.WILL_CREATED, AuditStatus.SUCCESS,
                AuditResourceType.WILL, willId.toString(),
                "{\"title\":\"Test Will\"}"
        );

        assertThat(entry1.getSequenceNumber()).isEqualTo(1L);
        assertThat(entry1.getPrevHash()).isEqualTo(AuditLogService.GENESIS_PREV_HASH);
        assertThat(entry1.getEntryHash()).isNotBlank();

        testTimeProvider.advanceMinutes(1);

        AuditLogEntry entry2 = auditLogService.logCritical(
                willId, "owner-1", "OWNER",
                AuditAction.DOCUMENT_UPLOADED, AuditStatus.SUCCESS,
                AuditResourceType.DOCUMENT, "doc-123",
                "{\"file\":\"test.pdf\"}"
        );

        assertThat(entry2.getSequenceNumber()).isEqualTo(2L);
        assertThat(entry2.getPrevHash()).isEqualTo(entry1.getEntryHash());

        testTimeProvider.advanceMinutes(1);

        AuditLogEntry entry3 = auditLogService.logCritical(
                willId, "contact-1", "TRUSTED_CONTACT",
                AuditAction.CONTACT_CONFIRMATION, AuditStatus.SUCCESS,
                AuditResourceType.VERIFICATION, "req-1",
                "{\"cycle\":0}"
        );

        assertThat(entry3.getSequenceNumber()).isEqualTo(3L);
        assertThat(entry3.getPrevHash()).isEqualTo(entry2.getEntryHash());

        AuditVerificationResult result = auditLogService.verifyGlobalIntegrity();
        assertThat(result.valid()).isTrue();
        assertThat(result.checkedEntries()).isEqualTo(3L);
        assertThat(result.failureReason()).isNull();
    }

    @Test
    @DisplayName("Tamper detection catches modified audit log payload")
    void tamperDetectionCatchesModifiedPayload() {
        AuditLogEntry entry1 = auditLogService.logCritical(
                willId, "owner-1", "OWNER",
                AuditAction.WILL_CREATED, AuditStatus.SUCCESS,
                AuditResourceType.WILL, willId.toString(),
                "{\"title\":\"Original\"}"
        );

        auditLogService.logCritical(
                willId, "owner-1", "OWNER",
                AuditAction.STATE_TRANSITION, AuditStatus.SUCCESS,
                AuditResourceType.WILL, willId.toString(),
                "{\"state\":\"INACTIVITY_WARNING\"}"
        );

        // Tamper with entry 1 details directly in DB
        entry1.setDetailsJson("{\"title\":\"Tampered Data Without Rehash\"}");
        auditLogRepository.saveAndFlush(entry1);

        AuditVerificationResult result = auditLogService.verifyGlobalIntegrity();
        assertThat(result.valid()).isFalse();
        assertThat(result.failureReason()).contains("Tampering detected");
        assertThat(result.failedSequenceNumber()).isEqualTo(1L);
    }

    @Test
    @DisplayName("Tamper detection catches broken hash chain linkage")
    void tamperDetectionCatchesBrokenHashLinkage() {
        auditLogService.logCritical(
                willId, "owner-1", "OWNER",
                AuditAction.WILL_CREATED, AuditStatus.SUCCESS,
                AuditResourceType.WILL, willId.toString(),
                "{}"
        );

        AuditLogEntry entry2 = auditLogService.logCritical(
                willId, "owner-1", "OWNER",
                AuditAction.DOCUMENT_UPLOADED, AuditStatus.SUCCESS,
                AuditResourceType.DOCUMENT, "doc-1",
                "{}"
        );

        // Tamper with entry2's prevHash
        entry2.setPrevHash("f".repeat(64));
        auditLogRepository.saveAndFlush(entry2);

        AuditVerificationResult result = auditLogService.verifyGlobalIntegrity();
        assertThat(result.valid()).isFalse();
        assertThat(result.failureReason()).contains("Broken hash chain");
        assertThat(result.failedSequenceNumber()).isEqualTo(2L);
    }

    @Test
    @DisplayName("Audit verification detects deleted entry in sequence (gap detection)")
    void tamperDetectionCatchesDeletedEntry() {
        AuditLogEntry entry1 = auditLogService.logCritical(
                willId, "owner-1", "OWNER",
                AuditAction.WILL_CREATED, AuditStatus.SUCCESS,
                AuditResourceType.WILL, willId.toString(), "{}"
        );
        AuditLogEntry entry2 = auditLogService.logCritical(
                willId, "owner-1", "OWNER",
                AuditAction.DOCUMENT_UPLOADED, AuditStatus.SUCCESS,
                AuditResourceType.DOCUMENT, "doc-1", "{}"
        );
        auditLogService.logCritical(
                willId, "owner-1", "OWNER",
                AuditAction.STATE_TRANSITION, AuditStatus.SUCCESS,
                AuditResourceType.WILL, willId.toString(), "{}"
        );

        // Delete entry 2 from database
        auditLogRepository.delete(entry2);
        auditLogRepository.flush();

        AuditVerificationResult result = auditLogService.verifyGlobalIntegrity();
        assertThat(result.valid()).isFalse();
        // Will detect sequence discontinuity (expected 2, found 3) or broken hash
        assertThat(result.failureReason()).containsAnyOf("Sequence discontinuity", "Broken hash chain");
    }

    @Test
    @DisplayName("Critical audit logging failure rolls back calling transaction (fail-closed)")
    void criticalAuditFailureRollsBackTransaction() {
        // Create an audit service with mock repository that fails on save
        AuditLogRepository failingRepo = org.mockito.Mockito.mock(AuditLogRepository.class);
        org.mockito.Mockito.when(failingRepo.findTopByOrderBySequenceNumberDesc()).thenReturn(java.util.Optional.empty());
        org.mockito.Mockito.when(failingRepo.saveAndFlush(org.mockito.Mockito.any()))
                .thenThrow(new RuntimeException("Database disk full"));

        AuditLogService failingAuditService = new AuditLogService(failingRepo, testTimeProvider);

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                failingAuditService.logCritical(
                        willId, "actor-1", "OWNER",
                        AuditAction.STATE_TRANSITION, AuditStatus.SUCCESS,
                        AuditResourceType.WILL, willId.toString(), "{}"
                )
        ).isInstanceOf(com.digitalwill.audit.exception.AuditPersistenceException.class);
    }
}

