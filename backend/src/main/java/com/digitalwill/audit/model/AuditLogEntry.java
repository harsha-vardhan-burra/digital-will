package com.digitalwill.audit.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * JPA entity representing a tamper-evident, hash-chained audit record.
 */
@Entity
@Table(name = "audit_logs")
public class AuditLogEntry {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "sequence_number", nullable = false, updatable = false, unique = true)
    private Long sequenceNumber;

    @Column(name = "will_id", updatable = false)
    private UUID willId;

    @Column(name = "actor_id", nullable = false, updatable = false, length = 100)
    private String actorId;

    @Column(name = "actor_type", nullable = false, updatable = false, length = 50)
    private String actorType;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, updatable = false, length = 100)
    private AuditAction action;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, updatable = false, length = 50)
    private AuditStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "resource_type", nullable = false, updatable = false, length = 50)
    private AuditResourceType resourceType;

    @Column(name = "resource_id", updatable = false, length = 100)
    private String resourceId;

    @Column(name = "details_json", nullable = false, updatable = false, columnDefinition = "TEXT")
    private String detailsJson;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "prev_hash", nullable = false, updatable = false, length = 64)
    private String prevHash;

    @Column(name = "entry_hash", nullable = false, updatable = false, length = 64)
    private String entryHash;

    public AuditLogEntry() {
    }

    public AuditLogEntry(UUID id, Long sequenceNumber, UUID willId, String actorId, String actorType,
                         AuditAction action, AuditStatus status, AuditResourceType resourceType,
                         String resourceId, String detailsJson, Instant createdAt,
                         String prevHash, String entryHash) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.sequenceNumber = Objects.requireNonNull(sequenceNumber, "sequenceNumber must not be null");
        this.willId = willId;
        this.actorId = Objects.requireNonNull(actorId, "actorId must not be null");
        this.actorType = Objects.requireNonNull(actorType, "actorType must not be null");
        this.action = Objects.requireNonNull(action, "action must not be null");
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.resourceType = Objects.requireNonNull(resourceType, "resourceType must not be null");
        this.resourceId = resourceId;
        this.detailsJson = detailsJson != null ? detailsJson : "{}";
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.prevHash = Objects.requireNonNull(prevHash, "prevHash must not be null");
        this.entryHash = Objects.requireNonNull(entryHash, "entryHash must not be null");
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Long getSequenceNumber() {
        return sequenceNumber;
    }

    public void setSequenceNumber(Long sequenceNumber) {
        this.sequenceNumber = sequenceNumber;
    }

    public UUID getWillId() {
        return willId;
    }

    public void setWillId(UUID willId) {
        this.willId = willId;
    }

    public String getActorId() {
        return actorId;
    }

    public void setActorId(String actorId) {
        this.actorId = actorId;
    }

    public String getActorType() {
        return actorType;
    }

    public void setActorType(String actorType) {
        this.actorType = actorType;
    }

    public AuditAction getAction() {
        return action;
    }

    public void setAction(AuditAction action) {
        this.action = action;
    }

    public AuditStatus getStatus() {
        return status;
    }

    public void setStatus(AuditStatus status) {
        this.status = status;
    }

    public AuditResourceType getResourceType() {
        return resourceType;
    }

    public void setResourceType(AuditResourceType resourceType) {
        this.resourceType = resourceType;
    }

    public String getResourceId() {
        return resourceId;
    }

    public void setResourceId(String resourceId) {
        this.resourceId = resourceId;
    }

    public String getDetailsJson() {
        return detailsJson;
    }

    public void setDetailsJson(String detailsJson) {
        this.detailsJson = detailsJson;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public String getPrevHash() {
        return prevHash;
    }

    public void setPrevHash(String prevHash) {
        this.prevHash = prevHash;
    }

    public String getEntryHash() {
        return entryHash;
    }

    public void setEntryHash(String entryHash) {
        this.entryHash = entryHash;
    }
}
