package com.digitalwill.document.service;

import com.digitalwill.audit.model.AuditAction;
import com.digitalwill.audit.model.AuditLogEntry;
import com.digitalwill.audit.repository.AuditLogRepository;
import com.digitalwill.common.TestTimeProvider;
import com.digitalwill.crypto.exception.DecryptionFailedException;
import com.digitalwill.crypto.storage.DocumentStorageService;
import com.digitalwill.document.model.EncryptedDocument;
import com.digitalwill.document.repository.EncryptedDocumentRepository;
import com.digitalwill.state.model.WillState;
import com.digitalwill.state.model.WillStateEntity;
import com.digitalwill.state.repository.WillStateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.springframework.context.annotation.Import;
import com.digitalwill.config.TestTimeConfig;

@SpringBootTest
@Import(TestTimeConfig.class)
@Transactional
class DocumentServiceIntegrationTest {

    @Autowired
    private DocumentService documentService;

    @Autowired
    private EncryptedDocumentRepository documentRepository;

    @Autowired
    private DocumentStorageService storageService;

    @Autowired
    private WillStateRepository willStateRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private TestTimeProvider testTimeProvider;

    private final Instant baseTime = Instant.parse("2026-10-05T12:00:00Z");
    private UUID willId;
    private UUID ownerId;

    @BeforeEach
    void setUp() {
        testTimeProvider.setNow(baseTime);
        auditLogRepository.deleteAll();

        ownerId = UUID.randomUUID();
        willId = UUID.randomUUID();
        WillStateEntity will = new WillStateEntity(willId, ownerId, "Document Test Will", WillState.ACTIVE, baseTime, baseTime);
        willStateRepository.save(will);
    }

    @Test
    @DisplayName("Upload encrypts document with envelope encryption and records audit log; download decrypts accurately")
    void uploadAndDownloadDocumentSuccess() {
        byte[] originalContent = "Last Will & Testament Property Deed".getBytes(StandardCharsets.UTF_8);

        EncryptedDocument uploaded = documentService.uploadDocument(
                willId, ownerId, "deed.pdf", "application/pdf", originalContent
        );

        assertThat(uploaded).isNotNull();
        assertThat(uploaded.getId()).isNotNull();
        assertThat(uploaded.getFileName()).isEqualTo("deed.pdf");
        assertThat(uploaded.getStoragePath()).isNotBlank();
        assertThat(uploaded.getEncryptedDek()).isNotBlank();
        assertThat(uploaded.getIv()).isNotBlank();

        // Verify stored file on disk is encrypted, not plaintext!
        byte[] storedBytes = storageService.retrieve(uploaded.getStoragePath());
        assertThat(storedBytes).isNotEqualTo(originalContent);

        // Download and verify decryption
        byte[] downloaded = documentService.downloadDocument(willId, uploaded.getId(), ownerId.toString(), "OWNER");
        assertThat(downloaded).isEqualTo(originalContent);

        // Check audit log recorded upload and access
        List<AuditLogEntry> auditLogs = auditLogRepository.findByWillIdOrderBySequenceNumberAsc(willId);
        assertThat(auditLogs).hasSize(2);
        assertThat(auditLogs.get(0).getAction()).isEqualTo(AuditAction.DOCUMENT_UPLOADED);
        assertThat(auditLogs.get(1).getAction()).isEqualTo(AuditAction.DOCUMENT_ACCESSED);
    }

    @Test
    @DisplayName("Tampered stored document fails decryption and generates security alert audit entry")
    void tamperedDocumentFailsDecryptionAndAlerts() {
        byte[] originalContent = "Secret Asset Inventory".getBytes(StandardCharsets.UTF_8);

        EncryptedDocument uploaded = documentService.uploadDocument(
                willId, ownerId, "assets.pdf", "application/pdf", originalContent
        );

        // Tamper with the stored ciphertext
        byte[] storedBytes = storageService.retrieve(uploaded.getStoragePath());
        storedBytes[0] ^= 0x7F;
        storageService.store(willId, uploaded.getId(), storedBytes);

        assertThatThrownBy(() -> documentService.downloadDocument(willId, uploaded.getId(), ownerId.toString(), "OWNER"))
                .isInstanceOf(DecryptionFailedException.class);

        // Verify SECURITY_ALERT logged
        List<AuditLogEntry> auditLogs = auditLogRepository.findByWillIdOrderBySequenceNumberAsc(willId);
        assertThat(auditLogs).anyMatch(entry -> entry.getAction() == AuditAction.SECURITY_ALERT);
    }

    @Test
    @DisplayName("Path traversal attempts in storage retrieval fail closed without leaking filesystem paths")
    void storagePathTraversalAttemptsFailClosed() {
        assertThatThrownBy(() -> storageService.retrieve("../../../etc/passwd"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Path traversal attempt detected: invalid storage path")
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain("C:\\", "/Users", "/home"));

        assertThatThrownBy(() -> storageService.retrieve("..\\..\\windows\\system32"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Path traversal attempt detected: invalid storage path");

        assertThatThrownBy(() -> storageService.retrieve("/root/secret"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Path traversal attempt detected: invalid storage path");
    }

    @Test
    @DisplayName("Missing stored document fails deterministically without leaking internal server paths")
    void missingDocumentFailsDeterministically() {
        assertThatThrownBy(() -> storageService.retrieve(UUID.randomUUID() + "/missing.enc"))
                .isInstanceOf(com.digitalwill.crypto.exception.CryptoException.class)
                .hasMessage("Encrypted document file not found")
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain("C:\\", "/Users", "/home"));
    }

    @Test
    @DisplayName("Unauthorized document upload actor rejected with SecurityException")
    void unauthorizedUploadActorRejected() {
        UUID strangerId = UUID.randomUUID();
        byte[] content = "Stranger's file".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> documentService.uploadDocument(
                willId, strangerId, "stranger.pdf", "application/pdf", content
        )).isInstanceOf(SecurityException.class)
          .hasMessageContaining("Unauthorized");
    }
}

