package com.digitalwill.release.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "disclosed_records")
public class DisclosedRecord {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "will_id", nullable = false, updatable = false)
    private UUID willId;

    @Column(name = "beneficiary_id", nullable = false, updatable = false)
    private UUID beneficiaryId;

    @Column(name = "disclosure_token_id", nullable = false, updatable = false)
    private UUID disclosureTokenId;

    @Column(name = "package_payload_json", nullable = false, columnDefinition = "TEXT")
    private String packagePayloadJson;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public DisclosedRecord() {
    }

    public DisclosedRecord(UUID id, UUID willId, UUID beneficiaryId, UUID disclosureTokenId,
                           String packagePayloadJson, Instant createdAt) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.willId = Objects.requireNonNull(willId, "willId must not be null");
        this.beneficiaryId = Objects.requireNonNull(beneficiaryId, "beneficiaryId must not be null");
        this.disclosureTokenId = Objects.requireNonNull(disclosureTokenId, "disclosureTokenId must not be null");
        this.packagePayloadJson = Objects.requireNonNull(packagePayloadJson, "packagePayloadJson must not be null");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
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

    public UUID getBeneficiaryId() {
        return beneficiaryId;
    }

    public void setBeneficiaryId(UUID beneficiaryId) {
        this.beneficiaryId = beneficiaryId;
    }

    public UUID getDisclosureTokenId() {
        return disclosureTokenId;
    }

    public void setDisclosureTokenId(UUID disclosureTokenId) {
        this.disclosureTokenId = disclosureTokenId;
    }

    public String getPackagePayloadJson() {
        return packagePayloadJson;
    }

    public void setPackagePayloadJson(String packagePayloadJson) {
        this.packagePayloadJson = packagePayloadJson;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
