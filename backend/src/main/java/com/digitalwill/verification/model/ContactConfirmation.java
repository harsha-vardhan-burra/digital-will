package com.digitalwill.verification.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "contact_confirmations", uniqueConstraints = {
        @UniqueConstraint(name = "uq_contact_confirmations_will_contact_cycle",
                columnNames = {"will_id", "contact_id", "verification_cycle"})
})
public class ContactConfirmation {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "will_id", nullable = false, updatable = false)
    private UUID willId;

    @Column(name = "contact_id", nullable = false, updatable = false)
    private UUID contactId;

    @Column(name = "verification_request_id", nullable = false, updatable = false)
    private UUID verificationRequestId;

    @Column(name = "verification_cycle", nullable = false, updatable = false)
    private Long verificationCycle;

    @Column(name = "confirmed_at", nullable = false, updatable = false)
    private Instant confirmedAt;

    protected ContactConfirmation() {}

    public ContactConfirmation(UUID id, UUID willId, UUID contactId, UUID verificationRequestId,
                               Long verificationCycle, Instant confirmedAt) {
        this.id = Objects.requireNonNull(id);
        this.willId = Objects.requireNonNull(willId);
        this.contactId = Objects.requireNonNull(contactId);
        this.verificationRequestId = Objects.requireNonNull(verificationRequestId);
        this.verificationCycle = Objects.requireNonNull(verificationCycle);
        this.confirmedAt = Objects.requireNonNull(confirmedAt);
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getWillId() { return willId; }
    public void setWillId(UUID willId) { this.willId = willId; }
    public UUID getContactId() { return contactId; }
    public void setContactId(UUID contactId) { this.contactId = contactId; }
    public UUID getVerificationRequestId() { return verificationRequestId; }
    public void setVerificationRequestId(UUID verificationRequestId) { this.verificationRequestId = verificationRequestId; }
    public Long getVerificationCycle() { return verificationCycle; }
    public void setVerificationCycle(Long verificationCycle) { this.verificationCycle = verificationCycle; }
    public Instant getConfirmedAt() { return confirmedAt; }
    public void setConfirmedAt(Instant confirmedAt) { this.confirmedAt = confirmedAt; }
}