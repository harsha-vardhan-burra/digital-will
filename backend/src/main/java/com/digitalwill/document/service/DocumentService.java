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
import com.digitalwill.common.exception.DocumentValidationException;
import com.digitalwill.state.model.WillState;
import com.digitalwill.state.model.WillStateEntity;
import com.digitalwill.state.repository.WillStateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
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
    private final com.digitalwill.document.validation.DocumentValidationPipeline validationPipeline;

    @Autowired
    public DocumentService(EncryptedDocumentRepository documentRepository,
                           EncryptionService encryptionService,
                           DocumentStorageService storageService,
                           AuditLogService auditLogService,
                           WillStateRepository willStateRepository,
                           TimeProvider timeProvider,
                           com.digitalwill.document.validation.DocumentValidationPipeline validationPipeline) {
        this.documentRepository = Objects.requireNonNull(documentRepository);
        this.encryptionService = Objects.requireNonNull(encryptionService);
        this.storageService = Objects.requireNonNull(storageService);
        this.auditLogService = Objects.requireNonNull(auditLogService);
        this.willStateRepository = Objects.requireNonNull(willStateRepository);
        this.timeProvider = Objects.requireNonNull(timeProvider);
        this.validationPipeline = Objects.requireNonNull(validationPipeline);
    }

    public DocumentService(EncryptedDocumentRepository documentRepository,
                           EncryptionService encryptionService,
                           DocumentStorageService storageService,
                           AuditLogService auditLogService,
                           WillStateRepository willStateRepository,
                           TimeProvider timeProvider) {
        this(documentRepository, encryptionService, storageService, auditLogService,
             willStateRepository, timeProvider,
             new com.digitalwill.document.validation.DocumentValidationPipeline(
                     new com.digitalwill.document.validation.MediaDetector(),
                     new com.digitalwill.document.validation.SafeDocumentParser(),
                     new com.digitalwill.document.validation.ClamAvMalwareScanner(false, "localhost", 3310, 5000, "OPTIONAL")
             ));
    }

    private static String escapeJson(String input) {
        if (input == null) return "";
        return input.replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\b", "\\b")
                    .replace("\f", "\\f")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                    .replace("\t", "\\t");
    }

    @Transactional
    public EncryptedDocument uploadDocument(UUID willId, UUID ownerId, String fileName, String contentType,
                                           com.digitalwill.document.model.DocumentCategory category, byte[] plaintext) {
        Objects.requireNonNull(willId, "willId must not be null");
        Objects.requireNonNull(fileName, "fileName must not be null");
        Objects.requireNonNull(contentType, "contentType must not be null");
        Objects.requireNonNull(plaintext, "plaintext must not be null");

        WillStateEntity will = willStateRepository.findById(willId)
                .orElseThrow(() -> new IllegalArgumentException("Will not found with ID: " + willId));

        if (ownerId != null && !ownerId.equals(will.getOwnerId())) {
            throw new SecurityException("Unauthorized: actor is not the owner of Will: " + willId);
        }

        if (will.getState() == WillState.EXECUTING || will.getState() == WillState.EXECUTED) {
            throw new DocumentValidationException(
                    "INVALID_STATE",
                    "Cannot upload documents: Digital Will is in terminal or executing state (" + will.getState() + ")",
                    HttpStatus.CONFLICT
            );
        }

        // Run authoritative Document Validation Pipeline (Stages A through E)
        var pipelineResult = validationPipeline.validate(fileName, contentType, category, plaintext);

        UUID documentId = UUID.randomUUID();
        Instant now = timeProvider.now();

        // 1. Compute checksum of plaintext
        String checksum = computeSha256Checksum(plaintext);

        // 2. Perform envelope encryption (AES-256-GCM + wrapped DEK)
        EncryptedData encrypted = encryptionService.encrypt(plaintext);

        // 3. Store ciphertext securely on storage backend (Stage F - Private Staging)
        String storagePath = null;
        EncryptedDocument saved;
        try {
            storagePath = storageService.store(willId, documentId, encrypted.ciphertext());

            // 4. Save metadata entity
            EncryptedDocument entity = new EncryptedDocument(
                    documentId,
                    willId,
                    pipelineResult.sanitizedFileName(),
                    pipelineResult.detectedContentType(),
                    plaintext.length,
                    checksum,
                    storagePath,
                    encrypted.encryptedDek(),
                    encrypted.iv(),
                    encrypted.algorithm(),
                    encrypted.keyWrapAlgorithm(),
                    encrypted.version(),
                    now,
                    ownerId,
                    pipelineResult.status(),
                    pipelineResult.statusCode(),
                    pipelineResult.statusMessage(),
                    pipelineResult.detectedContentType(),
                    pipelineResult.category()
            );

            saved = documentRepository.save(entity);
        } catch (Exception e) {
            if (storagePath != null) {
                storageService.delete(storagePath);
            }
            throw e;
        }

        // 5. Audit log upload event (audit-required)
        String details = "{\"fileName\":\"" + escapeJson(pipelineResult.sanitizedFileName()) +
                "\",\"fileSize\":" + plaintext.length +
                ",\"checksum\":\"" + checksum +
                "\",\"status\":\"" + pipelineResult.status() + "\"}";
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

        log.info("Document [{}] uploaded, validated, and envelope-encrypted for Will [{}]", documentId, willId);
        return saved;
    }

    @Transactional
    public EncryptedDocument uploadDocument(UUID willId, UUID ownerId, String fileName, String contentType, byte[] plaintext) {
        return uploadDocument(willId, ownerId, fileName, contentType, com.digitalwill.document.model.DocumentCategory.GENERAL, plaintext);
    }

    @Transactional
    public byte[] downloadDocument(UUID willId, UUID documentId, String actorId, String actorType) {
        Objects.requireNonNull(willId, "willId must not be null");
        Objects.requireNonNull(documentId, "documentId must not be null");

        EncryptedDocument doc = documentRepository.findByIdAndWillId(documentId, willId)
                .orElseThrow(() -> new DocumentNotFoundException("Document not found with ID: " + documentId));

        if (doc.getStatus() != com.digitalwill.document.model.DocumentStatus.ACCEPTED) {
            throw new SecurityException("Document is not available for download (status: " + doc.getStatus() + ")");
        }

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
                "{\"fileName\":\"" + escapeJson(doc.getFileName()) + "\"}"
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
