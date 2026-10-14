package com.digitalwill.document.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * JPA entity representing metadata for an envelope-encrypted document stored in the system.
 */
@Entity
@Table(name = "encrypted_documents")
public class EncryptedDocument {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "will_id", nullable = false, updatable = false)
    private UUID willId;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "content_type", nullable = false)
    private String contentType;

    @Column(name = "file_size", nullable = false)
    private long fileSize;

    @Column(name = "checksum_sha256", nullable = false, length = 64)
    private String checksumSha256;

    @Column(name = "storage_path", nullable = false, length = 500)
    private String storagePath;

    @Column(name = "encrypted_dek", nullable = false, length = 500)
    private String encryptedDek;

    @Column(name = "iv", nullable = false, length = 100)
    private String iv;

    @Column(name = "algorithm", nullable = false, length = 50)
    private String algorithm;

    @Column(name = "key_wrap_algorithm", nullable = false, length = 50)
    private String keyWrapAlgorithm;

    @Column(name = "version", nullable = false)
    private int version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by")
    private UUID createdBy;

    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private DocumentStatus status = DocumentStatus.ACCEPTED;

    @Column(name = "status_code", length = 100)
    private String statusCode;

    @Column(name = "status_message", length = 500)
    private String statusMessage;

    @Column(name = "detected_content_type", length = 100)
    private String detectedContentType;

    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    @Column(name = "category", nullable = false, length = 50)
    private DocumentCategory category = DocumentCategory.GENERAL;

    public EncryptedDocument() {
    }

    public EncryptedDocument(UUID id, UUID willId, String fileName, String contentType, long fileSize,
                             String checksumSha256, String storagePath, String encryptedDek, String iv,
                             String algorithm, String keyWrapAlgorithm, int version, Instant createdAt, UUID createdBy) {
        this(id, willId, fileName, contentType, fileSize, checksumSha256, storagePath, encryptedDek, iv,
             algorithm, keyWrapAlgorithm, version, createdAt, createdBy, DocumentStatus.ACCEPTED, null, null, contentType, DocumentCategory.GENERAL);
    }

    public EncryptedDocument(UUID id, UUID willId, String fileName, String contentType, long fileSize,
                             String checksumSha256, String storagePath, String encryptedDek, String iv,
                             String algorithm, String keyWrapAlgorithm, int version, Instant createdAt, UUID createdBy,
                             DocumentStatus status, String statusCode, String statusMessage,
                             String detectedContentType, DocumentCategory category) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.willId = Objects.requireNonNull(willId, "willId must not be null");
        this.fileName = Objects.requireNonNull(fileName, "fileName must not be null");
        this.contentType = Objects.requireNonNull(contentType, "contentType must not be null");
        this.fileSize = fileSize;
        this.checksumSha256 = Objects.requireNonNull(checksumSha256, "checksumSha256 must not be null");
        this.storagePath = Objects.requireNonNull(storagePath, "storagePath must not be null");
        this.encryptedDek = Objects.requireNonNull(encryptedDek, "encryptedDek must not be null");
        this.iv = Objects.requireNonNull(iv, "iv must not be null");
        this.algorithm = Objects.requireNonNull(algorithm, "algorithm must not be null");
        this.keyWrapAlgorithm = Objects.requireNonNull(keyWrapAlgorithm, "keyWrapAlgorithm must not be null");
        this.version = version;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.createdBy = createdBy;
        this.status = status != null ? status : DocumentStatus.ACCEPTED;
        this.statusCode = statusCode;
        this.statusMessage = statusMessage;
        this.detectedContentType = detectedContentType != null ? detectedContentType : contentType;
        this.category = category != null ? category : DocumentCategory.GENERAL;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getWillId() {
        return willId;
    }

    public void setWillId(UUID willId) {
        this.willId = willId;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public long getFileSize() {
        return fileSize;
    }

    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }

    public String getChecksumSha256() {
        return checksumSha256;
    }

    public void setChecksumSha256(String checksumSha256) {
        this.checksumSha256 = checksumSha256;
    }

    public String getStoragePath() {
        return storagePath;
    }

    public void setStoragePath(String storagePath) {
        this.storagePath = storagePath;
    }

    public String getEncryptedDek() {
        return encryptedDek;
    }

    public void setEncryptedDek(String encryptedDek) {
        this.encryptedDek = encryptedDek;
    }

    public String getIv() {
        return iv;
    }

    public void setIv(String iv) {
        this.iv = iv;
    }

    public String getAlgorithm() {
        return algorithm;
    }

    public void setAlgorithm(String algorithm) {
        this.algorithm = algorithm;
    }

    public String getKeyWrapAlgorithm() {
        return keyWrapAlgorithm;
    }

    public void setKeyWrapAlgorithm(String keyWrapAlgorithm) {
        this.keyWrapAlgorithm = keyWrapAlgorithm;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(UUID createdBy) {
        this.createdBy = createdBy;
    }

    public DocumentStatus getStatus() {
        return status;
    }

    public void setStatus(DocumentStatus status) {
        this.status = status;
    }

    public String getStatusCode() {
        return statusCode;
    }

    public void setStatusCode(String statusCode) {
        this.statusCode = statusCode;
    }

    public String getStatusMessage() {
        return statusMessage;
    }

    public void setStatusMessage(String statusMessage) {
        this.statusMessage = statusMessage;
    }

    public String getDetectedContentType() {
        return detectedContentType;
    }

    public void setDetectedContentType(String detectedContentType) {
        this.detectedContentType = detectedContentType;
    }

    public DocumentCategory getCategory() {
        return category;
    }

    public void setCategory(DocumentCategory category) {
        this.category = category;
    }
}
