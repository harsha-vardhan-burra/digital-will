package com.digitalwill.verification.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "will_contacts")
public class WillContact {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "will_id", nullable = false, updatable = false)
    private UUID willId;

    @Column(name = "contact_id", nullable = false, updatable = false)
    private UUID contactId;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected WillContact() {}

    public WillContact(UUID id, UUID willId, UUID contactId, Instant createdAt) {
        this.id = Objects.requireNonNull(id);
        this.willId = Objects.requireNonNull(willId);
        this.contactId = Objects.requireNonNull(contactId);
        this.createdAt = Objects.requireNonNull(createdAt);
        this.active = true;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getWillId() { return willId; }
    public void setWillId(UUID willId) { this.willId = willId; }
    public UUID getContactId() { return contactId; }
    public void setContactId(UUID contactId) { this.contactId = contactId; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}