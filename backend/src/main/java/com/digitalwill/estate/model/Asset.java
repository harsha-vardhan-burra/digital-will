package com.digitalwill.estate.model;

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
@Table(name = "assets")
public class Asset {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "will_id", nullable = false, updatable = false)
    private UUID willId;

    @Column(name = "title", nullable = false)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 50)
    private AssetCategory category;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "encrypted_access_data", columnDefinition = "TEXT")
    private String encryptedAccessData;

    @Column(name = "instructions", columnDefinition = "TEXT")
    private String instructions;

    @Column(name = "institution_provider")
    private String institutionProvider;

    @Column(name = "account_type", length = 100)
    private String accountType;

    @Column(name = "currency", length = 3)
    private String currency;

    @Column(name = "asset_value", precision = 19, scale = 4)
    private java.math.BigDecimal assetValue;

    @Column(name = "valuation_date")
    private java.time.LocalDate valuationDate;

    @Column(name = "ownership_percentage", precision = 5, scale = 2)
    private java.math.BigDecimal ownershipPercentage;

    @Column(name = "asset_year")
    private Integer assetYear;

    @Column(name = "asset_url", length = 2000)
    private String assetUrl;

    @Column(name = "identifier_masked")
    private String identifierMasked;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Asset() {
    }

    public Asset(UUID id, UUID willId, String title, AssetCategory category, String description,
                 String encryptedAccessData, String instructions, Instant createdAt, Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.willId = Objects.requireNonNull(willId, "willId must not be null");
        this.title = Objects.requireNonNull(title, "title must not be null");
        this.category = Objects.requireNonNull(category, "category must not be null");
        this.description = description;
        this.encryptedAccessData = encryptedAccessData;
        this.instructions = instructions;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt must not be null");
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

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public AssetCategory getCategory() {
        return category;
    }

    public void setCategory(AssetCategory category) {
        this.category = category;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getEncryptedAccessData() {
        return encryptedAccessData;
    }

    public void setEncryptedAccessData(String encryptedAccessData) {
        this.encryptedAccessData = encryptedAccessData;
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

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getInstitutionProvider() {
        return institutionProvider;
    }

    public void setInstitutionProvider(String institutionProvider) {
        this.institutionProvider = institutionProvider;
    }

    public String getAccountType() {
        return accountType;
    }

    public void setAccountType(String accountType) {
        this.accountType = accountType;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public java.math.BigDecimal getAssetValue() {
        return assetValue;
    }

    public void setAssetValue(java.math.BigDecimal assetValue) {
        this.assetValue = assetValue;
    }

    public java.time.LocalDate getValuationDate() {
        return valuationDate;
    }

    public void setValuationDate(java.time.LocalDate valuationDate) {
        this.valuationDate = valuationDate;
    }

    public java.math.BigDecimal getOwnershipPercentage() {
        return ownershipPercentage;
    }

    public void setOwnershipPercentage(java.math.BigDecimal ownershipPercentage) {
        this.ownershipPercentage = ownershipPercentage;
    }

    public Integer getAssetYear() {
        return assetYear;
    }

    public void setAssetYear(Integer assetYear) {
        this.assetYear = assetYear;
    }

    public String getAssetUrl() {
        return assetUrl;
    }

    public void setAssetUrl(String assetUrl) {
        this.assetUrl = assetUrl;
    }

    public String getIdentifierMasked() {
        return identifierMasked;
    }

    public void setIdentifierMasked(String identifierMasked) {
        this.identifierMasked = identifierMasked;
    }
}
