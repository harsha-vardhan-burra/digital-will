package com.digitalwill.verification.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "verification_requests")
public class VerificationRequest {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "will_id", nullable = false, updatable = false)
    private UUID willId;

    @Column(name = "contact_id", nullable = false, updatable = false)
    private UUID contactId;

    @Column(name = "token_hash", nullable = false, unique = true)
    private String tokenHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private VerificationRequestStatus status;

    @Column(name = "verification_cycle", nullable = false)
    private Long verificationCycle;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    protected VerificationRequest() {}

    public VerificationRequest(UUID id, UUID willId, UUID contactId, String tokenHash,
                               Long verificationCycle, Instant createdAt, Instant expiresAt) {
        this.id = Objects.requireNonNull(id);
        this.willId = Objects.requireNonNull(willId);
        this.contactId = Objects.requireNonNull(contactId);
        this.tokenHash = Objects.requireNonNull(tokenHash);
        this.verificationCycle = Objects.requireNonNull(verificationCycle);
        this.createdAt = Objects.requireNonNull(createdAt);
        this.expiresAt = Objects.requireNonNull(expiresAt);
        this.status = VerificationRequestStatus.ACTIVE;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getWillId() { return willId; }
    public void setWillId(UUID willId) { this.willId = willId; }
    public UUID getContactId() { return contactId; }
    public void setContactId(UUID contactId) { this.contactId = contactId; }
    public String getTokenHash() { return tokenHash; }
    public void setTokenHash(String tokenHash) { this.tokenHash = tokenHash; }
    public VerificationRequestStatus getStatus() { return status; }
    public void setStatus(VerificationRequestStatus status) { this.status = status; }
    public Long getVerificationCycle() { return verificationCycle; }
    public void setVerificationCycle(Long verificationCycle) { this.verificationCycle = verificationCycle; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
    public Instant getConsumedAt() { return consumedAt; }
    public void setConsumedAt(Instant consumedAt) { this.consumedAt = consumedAt; }
}