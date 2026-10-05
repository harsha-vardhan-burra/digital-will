package com.digitalwill.estate.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "asset_allocations")
public class AssetAllocation {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "asset_id", nullable = false, updatable = false)
    private UUID assetId;

    @Column(name = "beneficiary_id", nullable = false, updatable = false)
    private UUID beneficiaryId;

    @Column(name = "share_percentage", nullable = false)
    private int sharePercentage = 100;

    @Column(name = "instructions", columnDefinition = "TEXT")
    private String instructions;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public AssetAllocation() {
    }

    public AssetAllocation(UUID id, UUID assetId, UUID beneficiaryId, int sharePercentage, String instructions, Instant createdAt) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.assetId = Objects.requireNonNull(assetId, "assetId must not be null");
        this.beneficiaryId = Objects.requireNonNull(beneficiaryId, "beneficiaryId must not be null");
        this.sharePercentage = sharePercentage;
        this.instructions = instructions;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getAssetId() {
        return assetId;
    }

    public void setAssetId(UUID assetId) {
        this.assetId = assetId;
    }

    public UUID getBeneficiaryId() {
        return beneficiaryId;
    }

    public void setBeneficiaryId(UUID beneficiaryId) {
        this.beneficiaryId = beneficiaryId;
    }

    public int getSharePercentage() {
        return sharePercentage;
    }

    public void setSharePercentage(int sharePercentage) {
        this.sharePercentage = sharePercentage;
    }

    public String getInstructions() {
        return instructions;
    }

    public void setInstructions(String instructions) {
        this.instructions = instructions;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
