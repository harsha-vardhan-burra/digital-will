package com.digitalwill.document.service;

import com.digitalwill.audit.model.AuditAction;
import com.digitalwill.audit.model.AuditResourceType;
import com.digitalwill.audit.model.AuditStatus;
import com.digitalwill.audit.service.AuditLogService;
import com.digitalwill.common.TimeProvider;
import com.digitalwill.crypto.exception.DecryptionFailedException;
import com.digitalwill.crypto.model.EncryptedData;
import com.digitalwill.crypto.service.EncryptionService;
import com.digitalwill.crypto.storage.DocumentStorageService;
import com.digitalwill.document.exception.DocumentNotFoundException;
import com.digitalwill.document.model.EncryptedDocument;
import com.digitalwill.document.repository.EncryptedDocumentRepository;
import com.digitalwill.state.model.WillStateEntity;
import com.digitalwill.state.repository.WillStateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Service managing envelope encryption and lifecycle of secure documents.
 * Ensures:
 * - Plaintext documents are never persisted to disk or database
 * - AES-256-GCM envelope encryption is applied prior to storage
 * - SHA-256 integrity checksums are computed and validated
 * - Every upload and access event is recorded in the tamper-evident audit log
 */
@Service
public class DocumentService {

    private static final Logger log = LoggerFactory.getLogger(DocumentService.class);

    private final EncryptedDocumentRepository documentRepository;
    private final EncryptionService encryptionService;
    private final DocumentStorageService storageService;
    private final AuditLogService auditLogService;
    private final WillStateRepository willStateRepository;
    private final TimeProvider timeProvider;

    public DocumentService(EncryptedDocumentRepository documentRepository,
                           EncryptionService encryptionService,
                           DocumentStorageService storageService,
                           AuditLogService auditLogService,
                           WillStateRepository willStateRepository,
                           TimeProvider timeProvider) {
        this.documentRepository = Objects.requireNonNull(documentRepository);
        this.encryptionService = Objects.requireNonNull(encryptionService);
        this.storageService = Objects.requireNonNull(storageService);
        this.auditLogService = Objects.requireNonNull(auditLogService);
        this.willStateRepository = Objects.requireNonNull(willStateRepository);
        this.timeProvider = Objects.requireNonNull(timeProvider);
    }

    @Transactional
    public EncryptedDocument uploadDocument(UUID willId, UUID ownerId, String fileName, String contentType, byte[] plaintext) {
        Objects.requireNonNull(willId, "willId must not be null");
        Objects.requireNonNull(fileName, "fileName must not be null");
        Objects.requireNonNull(contentType, "contentType must not be null");
        Objects.requireNonNull(plaintext, "plaintext must not be null");

        WillStateEntity will = willStateRepository.findById(willId)
                .orElseThrow(() -> new IllegalArgumentException("Will not found with ID: " + willId));

        if (ownerId != null && !ownerId.equals(will.getOwnerId())) {
            throw new SecurityException("Unauthorized: actor is not the owner of Will: " + willId);
        }

        UUID documentId = UUID.randomUUID();
        Instant now = timeProvider.now();

        // 1. Compute checksum of plaintext
        String checksum = computeSha256Checksum(plaintext);

        // 2. Perform envelope encryption (AES-256-GCM + wrapped DEK)
        EncryptedData encrypted = encryptionService.encrypt(plaintext);

        // 3. Store ciphertext securely on storage backend
        String storagePath = storageService.store(willId, documentId, encrypted.ciphertext());

        // 4. Save metadata entity
        EncryptedDocument entity = new EncryptedDocument(
                documentId,
                willId,
                fileName,
                contentType,
                plaintext.length,
                checksum,
                storagePath,
                encrypted.encryptedDek(),
                encrypted.iv(),
                encrypted.algorithm(),
                encrypted.keyWrapAlgorithm(),
                encrypted.version(),
                now,
                ownerId
        );

        EncryptedDocument saved = documentRepository.save(entity);

        // 5. Audit log upload event (audit-required)
        String details = "{\"fileName\":\"" + fileName + "\",\"fileSize\":" + plaintext.length + ",\"checksum\":\"" + checksum + "\"}";
        auditLogService.logCritical(
                willId,
                ownerId != null ? ownerId.toString() : "UNKNOWN",
                "OWNER",
                AuditAction.DOCUMENT_UPLOADED,
                AuditStatus.SUCCESS,
                AuditResourceType.DOCUMENT,
                documentId.toString(),
                details
        );

        log.info("Document [{}] uploaded and envelope-encrypted for Will [{}]", documentId, willId);
        return saved;
    }

    @Transactional
    public byte[] downloadDocument(UUID willId, UUID documentId, String actorId, String actorType) {
        Objects.requireNonNull(willId, "willId must not be null");
        Objects.requireNonNull(documentId, "documentId must not be null");

        EncryptedDocument doc = documentRepository.findByIdAndWillId(documentId, willId)
                .orElseThrow(() -> new DocumentNotFoundException("Document not found with ID: " + documentId));

        // 1. Retrieve ciphertext from storage
        byte[] ciphertext = storageService.retrieve(doc.getStoragePath());

        // 2. Decrypt with envelope decryption
        byte[] plaintext;
        try {
            plaintext = encryptionService.decrypt(
                    ciphertext,
                    doc.getEncryptedDek(),
                    doc.getIv(),
                    doc.getVersion()
            );
        } catch (Exception e) {
            auditLogService.logCritical(
                    willId,
                    actorId,
                    actorType,
                    AuditAction.SECURITY_ALERT,
                    AuditStatus.FAILURE,
                    AuditResourceType.DOCUMENT,
                    documentId.toString(),
                    "{\"error\":\"Decryption failed for document " + documentId + "\"}"
            );
            throw e;
        }

        // 3. Validate integrity checksum
        String verifiedChecksum = computeSha256Checksum(plaintext);
        if (!verifiedChecksum.equalsIgnoreCase(doc.getChecksumSha256())) {
            String errorMsg = "Integrity check failed: checksum mismatch for document " + documentId;
            log.error(errorMsg);
            auditLogService.logCritical(
                    willId,
                    actorId,
                    actorType,
                    AuditAction.SECURITY_ALERT,
                    AuditStatus.FAILURE,
                    AuditResourceType.DOCUMENT,
                    documentId.toString(),
                    "{\"error\":\"Integrity checksum mismatch\"}"
            );
            throw new DecryptionFailedException(errorMsg);
        }

        // 4. Audit document access
        auditLogService.logCritical(
                willId,
                actorId,
                actorType,
                AuditAction.DOCUMENT_ACCESSED,
                AuditStatus.SUCCESS,
                AuditResourceType.DOCUMENT,
                documentId.toString(),
                "{\"fileName\":\"" + doc.getFileName() + "\"}"
        );

        return plaintext;
    }

    public List<EncryptedDocument> listDocumentsForWill(UUID willId) {
        return documentRepository.findByWillId(willId);
    }

    public EncryptedDocument getDocumentMetadata(UUID willId, UUID documentId) {
        return documentRepository.findByIdAndWillId(documentId, willId)
                .orElseThrow(() -> new DocumentNotFoundException("Document not found with ID: " + documentId));
    }

    private String computeSha256Checksum(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 digest unavailable", e);
        }
    }
}
