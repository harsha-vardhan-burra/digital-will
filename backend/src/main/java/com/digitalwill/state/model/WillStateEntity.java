package com.digitalwill.state.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * JPA entity mapping the authoritative 'wills' table.
 */
@Entity
@Table(name = "wills")
public class WillStateEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "owner_id", nullable = false, updatable = false)
    private UUID ownerId;

    @Column(name = "title", nullable = false)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false, length = 50)
    private WillState state;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "last_verified_activity_at", nullable = false)
    private Instant lastVerifiedActivityAt;

    @Column(name = "warning_sent_at")
    private Instant warningSentAt;

    @Column(name = "final_warning_sent_at")
    private Instant finalWarningSentAt;

    @Column(name = "verification_started_at")
    private Instant verificationStartedAt;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "release_after")
    private Instant releaseAfter;

    @Column(name = "executing_at")
    private Instant executingAt;

    @Column(name = "executed_at")
    private Instant executedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "verification_cycle", nullable = false)
    private Long verificationCycle = 0L;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    public WillStateEntity() {
    }

    public WillStateEntity(UUID id, UUID ownerId, String title, WillState state, Instant now, Instant lastVerifiedActivityAt) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.ownerId = Objects.requireNonNull(ownerId, "ownerId must not be null");
        this.title = Objects.requireNonNull(title, "title must not be null");
        this.state = Objects.requireNonNull(state, "state must not be null");
        this.createdAt = Objects.requireNonNull(now, "createdAt must not be null");
        this.updatedAt = now;
        this.lastVerifiedActivityAt = Objects.requireNonNull(lastVerifiedActivityAt, "lastVerifiedActivityAt must not be null");
        this.verificationCycle = 0L;
    }

    // Getters and setters
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(UUID ownerId) {
        this.ownerId = ownerId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public WillState getState() {
        return state;
    }

    public void setState(WillState state) {
        this.state = state;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Instant getLastVerifiedActivityAt() {
        return lastVerifiedActivityAt;
    }

    public void setLastVerifiedActivityAt(Instant lastVerifiedActivityAt) {
        this.lastVerifiedActivityAt = lastVerifiedActivityAt;
    }

    public Instant getWarningSentAt() {
        return warningSentAt;
    }

    public void setWarningSentAt(Instant warningSentAt) {
        this.warningSentAt = warningSentAt;
    }

    public Instant getFinalWarningSentAt() {
        return finalWarningSentAt;
    }

    public void setFinalWarningSentAt(Instant finalWarningSentAt) {
        this.finalWarningSentAt = finalWarningSentAt;
    }

    public Instant getVerificationStartedAt() {
        return verificationStartedAt;
    }

    public void setVerificationStartedAt(Instant verificationStartedAt) {
        this.verificationStartedAt = verificationStartedAt;
    }

    public Instant getVerifiedAt() {
        return verifiedAt;
    }

    public void setVerifiedAt(Instant verifiedAt) {
        this.verifiedAt = verifiedAt;
    }

    public Instant getReleaseAfter() {
        return releaseAfter;
    }

    public void setReleaseAfter(Instant releaseAfter) {
        this.releaseAfter = releaseAfter;
    }

    public Instant getExecutingAt() {
        return executingAt;
    }

    public void setExecutingAt(Instant executingAt) {
        this.executingAt = executingAt;
    }

    public Instant getExecutedAt() {
        return executedAt;
    }

    public void setExecutedAt(Instant executedAt) {
        this.executedAt = executedAt;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }

    public void setCancelledAt(Instant cancelledAt) {
        this.cancelledAt = cancelledAt;
    }

    public Long getVerificationCycle() {
        return verificationCycle;
    }

    public void setVerificationCycle(Long verificationCycle) {
        this.verificationCycle = verificationCycle;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }
}
