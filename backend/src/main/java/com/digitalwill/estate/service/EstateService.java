package com.digitalwill.estate.service;

import com.digitalwill.audit.model.AuditAction;
import com.digitalwill.audit.model.AuditResourceType;
import com.digitalwill.audit.model.AuditStatus;
import com.digitalwill.audit.service.AuditLogService;
import com.digitalwill.common.TimeProvider;
import com.digitalwill.document.repository.EncryptedDocumentRepository;
import com.digitalwill.estate.model.Asset;
import com.digitalwill.estate.model.AssetAllocation;
import com.digitalwill.estate.model.AssetCategory;
import com.digitalwill.estate.model.Beneficiary;
import com.digitalwill.estate.repository.AssetAllocationRepository;
import com.digitalwill.estate.repository.AssetRepository;
import com.digitalwill.estate.repository.BeneficiaryRepository;
import com.digitalwill.state.model.WillStateEntity;
import com.digitalwill.state.repository.WillStateRepository;
import com.digitalwill.verification.repository.WillContactRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
public class EstateService {

    private static final Logger log = LoggerFactory.getLogger(EstateService.class);

    private final AssetRepository assetRepository;
    private final BeneficiaryRepository beneficiaryRepository;
    private final AssetAllocationRepository allocationRepository;
    private final WillStateRepository willStateRepository;
    private final EncryptedDocumentRepository documentRepository;
    private final WillContactRepository willContactRepository;
    private final AuditLogService auditLogService;
    private final TimeProvider timeProvider;
    private final com.digitalwill.estate.validation.AssetValidator assetValidator;

    @Autowired
    public EstateService(AssetRepository assetRepository,
                         BeneficiaryRepository beneficiaryRepository,
                         AssetAllocationRepository allocationRepository,
                         WillStateRepository willStateRepository,
                         EncryptedDocumentRepository documentRepository,
                         WillContactRepository willContactRepository,
                         AuditLogService auditLogService,
                         TimeProvider timeProvider,
                         com.digitalwill.estate.validation.AssetValidator assetValidator) {
        this.assetRepository = Objects.requireNonNull(assetRepository);
        this.beneficiaryRepository = Objects.requireNonNull(beneficiaryRepository);
        this.allocationRepository = Objects.requireNonNull(allocationRepository);
        this.willStateRepository = Objects.requireNonNull(willStateRepository);
        this.documentRepository = Objects.requireNonNull(documentRepository);
        this.willContactRepository = Objects.requireNonNull(willContactRepository);
        this.auditLogService = Objects.requireNonNull(auditLogService);
        this.timeProvider = Objects.requireNonNull(timeProvider);
        this.assetValidator = assetValidator != null ? assetValidator : new com.digitalwill.estate.validation.AssetValidator(assetRepository);
    }

    public EstateService(AssetRepository assetRepository,
                         BeneficiaryRepository beneficiaryRepository,
                         AssetAllocationRepository allocationRepository,
                         WillStateRepository willStateRepository,
                         EncryptedDocumentRepository documentRepository,
                         WillContactRepository willContactRepository,
                         AuditLogService auditLogService,
                         TimeProvider timeProvider) {
        this(assetRepository, beneficiaryRepository, allocationRepository, willStateRepository,
             documentRepository, willContactRepository, auditLogService, timeProvider, null);
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

    public record AllocationReviewItem(
            UUID allocationId,
            UUID assetId,
            String assetTitle,
            UUID beneficiaryId,
            String beneficiaryName,
            int sharePercentage,
            String instructions
    ) {}

    public record WillReview(
            UUID willId,
            String title,
            com.digitalwill.state.model.WillState state,
            Instant createdAt,
            Instant lastVerifiedActivityAt,
            int assetCount,
            int beneficiaryCount,
            int allocationCount,
            int documentCount,
            int activeTrustedContactCount,
            boolean hasAssets,
            boolean hasBeneficiaries,
            boolean allAssetsFullyAllocated,
            boolean hasQuorumContacts,
            boolean readyForActivation,
            List<String> warnings,
            List<Asset> assets,
            List<Beneficiary> beneficiaries,
            List<AllocationReviewItem> allocations
    ) {}

    @Transactional
    public Asset addAsset(UUID willId, String title, AssetCategory category,
                          String description, String encryptedAccessData, String instructions,
                          String institutionProvider, String accountType, String currency,
                          java.math.BigDecimal assetValue, java.time.LocalDate valuationDate,
                          java.math.BigDecimal ownershipPercentage, Integer assetYear,
                          String assetUrl, String rawIdentifier) {
        WillStateEntity will = willStateRepository.findById(willId)
                .orElseThrow(() -> new IllegalArgumentException("Will not found with ID: " + willId));

        com.digitalwill.estate.validation.AssetValidator.ValidatedAssetData validated =
                assetValidator.validate(
                        will, title, category, description, encryptedAccessData, instructions,
                        institutionProvider, accountType, currency, assetValue, valuationDate,
                        ownershipPercentage, assetYear, assetUrl, rawIdentifier
                );

        Instant now = timeProvider.now();
        UUID assetId = UUID.randomUUID();
        Asset asset = new Asset(assetId, willId, validated.title(), validated.category(),
                validated.description(), validated.encryptedAccessData(), validated.instructions(), now, now);
        asset.setInstitutionProvider(validated.institutionProvider());
        asset.setAccountType(validated.accountType());
        asset.setCurrency(validated.currency());
        asset.setAssetValue(validated.assetValue());
        asset.setValuationDate(validated.valuationDate());
        asset.setOwnershipPercentage(validated.ownershipPercentage());
        asset.setAssetYear(validated.assetYear());
        asset.setAssetUrl(validated.assetUrl());
        asset.setIdentifierMasked(validated.identifierMasked());

        Asset saved = assetRepository.save(asset);

        auditLogService.logCritical(
                willId,
                will.getOwnerId().toString(),
                "OWNER",
                AuditAction.OWNER_ACTIVITY,
                AuditStatus.SUCCESS,
                AuditResourceType.WILL,
                assetId.toString(),
                "{\"action\":\"ADD_ASSET\",\"title\":\"" + escapeJson(validated.title()) + "\",\"category\":\"" + validated.category() + "\"}"
        );

        log.info("Asset [{}] ({}) added to Will [{}]", assetId, validated.title(), willId);
        return saved;
    }

    @Transactional
    public Asset addAsset(UUID willId, String title, AssetCategory category,
                          String description, String encryptedAccessData, String instructions) {
        return addAsset(willId, title, category, description, encryptedAccessData, instructions,
                null, null, null, null, null, null, null, null, null);
    }

    public List<Asset> listAssets(UUID willId) {
        return assetRepository.findByWillId(willId);
    }

    @Transactional
    public void deleteAsset(UUID willId, UUID assetId) {
        WillStateEntity will = willStateRepository.findById(willId)
                .orElseThrow(() -> new IllegalArgumentException("Will not found with ID: " + willId));
        assetValidator.verifyWillModifiable(will);

        Asset asset = assetRepository.findById(assetId)
                .orElseThrow(() -> new IllegalArgumentException("Asset not found with ID: " + assetId));
        if (!asset.getWillId().equals(willId)) {
            throw new IllegalArgumentException("Asset does not belong to Will: " + willId);
        }
        List<AssetAllocation> allocs = allocationRepository.findByAssetId(assetId);
        allocationRepository.deleteAll(allocs);
        assetRepository.delete(asset);

        auditLogService.logCritical(
                willId,
                "OWNER",
                "OWNER",
                AuditAction.OWNER_ACTIVITY,
                AuditStatus.SUCCESS,
                AuditResourceType.WILL,
                assetId.toString(),
                "{\"action\":\"DELETE_ASSET\",\"assetId\":\"" + assetId + "\"}"
        );
        log.info("Asset [{}] deleted from Will [{}]", assetId, willId);
    }

    @Transactional
    public Beneficiary addBeneficiary(UUID willId, String name, String email, String relationship) {
        WillStateEntity will = willStateRepository.findById(willId)
                .orElseThrow(() -> new IllegalArgumentException("Will not found with ID: " + willId));
        assetValidator.verifyWillModifiable(will);

        if (name == null || name.isBlank()) {
            throw new com.digitalwill.common.exception.ValidationException("REQUIRED_FIELD_MISSING", "Beneficiary name is required");
        }
        String cleanName = name.trim();
        if (cleanName.length() > 255) {
            throw new com.digitalwill.common.exception.ValidationException("INVALID_ARGUMENT", "Beneficiary name exceeds 255 characters");
        }
        com.digitalwill.common.validation.ProhibitedCredentialValidator.validateNoProhibitedCredentials("name", cleanName);

        if (email == null || email.isBlank()) {
            throw new com.digitalwill.common.exception.ValidationException("REQUIRED_FIELD_MISSING", "Beneficiary email is required");
        }
        String cleanEmail = email.trim().toLowerCase();
        if (cleanEmail.length() > 255 || !cleanEmail.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")) {
            throw new com.digitalwill.common.exception.ValidationException("INVALID_ARGUMENT", "Invalid beneficiary email format");
        }

        String cleanRelationship = null;
        if (relationship != null && !relationship.isBlank()) {
            cleanRelationship = relationship.trim();
            if (cleanRelationship.length() > 100) {
                throw new com.digitalwill.common.exception.ValidationException("INVALID_ARGUMENT", "Relationship exceeds 100 characters");
            }
            com.digitalwill.common.validation.ProhibitedCredentialValidator.validateNoProhibitedCredentials("relationship", cleanRelationship);
        }

        if (beneficiaryRepository.existsByWillIdAndEmailIgnoreCase(willId, cleanEmail)) {
            throw new com.digitalwill.common.exception.DuplicateResourceException("A beneficiary with email '" + cleanEmail + "' already exists in this estate");
        }

        Instant now = timeProvider.now();
        UUID beneficiaryId = UUID.randomUUID();
        Beneficiary beneficiary = new Beneficiary(beneficiaryId, willId, cleanName, cleanEmail, cleanRelationship, now);
        Beneficiary saved = beneficiaryRepository.save(beneficiary);

        auditLogService.logCritical(
                willId,
                will.getOwnerId().toString(),
                "OWNER",
                AuditAction.OWNER_ACTIVITY,
                AuditStatus.SUCCESS,
                AuditResourceType.WILL,
                beneficiaryId.toString(),
                "{\"action\":\"ADD_BENEFICIARY\",\"name\":\"" + escapeJson(cleanName) + "\"}"
        );

        log.info("Beneficiary [{}] ({}) added to Will [{}]", beneficiaryId, cleanName, willId);
        return saved;
    }

    public List<Beneficiary> listBeneficiaries(UUID willId) {
        return beneficiaryRepository.findByWillId(willId);
    }

    @Transactional
    public void deleteBeneficiary(UUID willId, UUID beneficiaryId) {
        WillStateEntity will = willStateRepository.findById(willId)
                .orElseThrow(() -> new IllegalArgumentException("Will not found with ID: " + willId));
        assetValidator.verifyWillModifiable(will);

        Beneficiary beneficiary = beneficiaryRepository.findById(beneficiaryId)
                .orElseThrow(() -> new IllegalArgumentException("Beneficiary not found with ID: " + beneficiaryId));
        if (!beneficiary.getWillId().equals(willId)) {
            throw new IllegalArgumentException("Beneficiary does not belong to Will: " + willId);
        }
        List<AssetAllocation> allocs = allocationRepository.findByBeneficiaryId(beneficiaryId);
        allocationRepository.deleteAll(allocs);
        beneficiaryRepository.delete(beneficiary);

        auditLogService.logCritical(
                willId,
                "OWNER",
                "OWNER",
                AuditAction.OWNER_ACTIVITY,
                AuditStatus.SUCCESS,
                AuditResourceType.WILL,
                beneficiaryId.toString(),
                "{\"action\":\"DELETE_BENEFICIARY\",\"beneficiaryId\":\"" + beneficiaryId + "\"}"
        );
        log.info("Beneficiary [{}] deleted from Will [{}]", beneficiaryId, willId);
    }

    @Transactional
    public AssetAllocation allocateAsset(UUID willId, UUID assetId, UUID beneficiaryId, int sharePercentage, String instructions) {
        Asset asset = assetRepository.findById(assetId)
                .orElseThrow(() -> new IllegalArgumentException("Asset not found with ID: " + assetId));
        if (willId != null && !asset.getWillId().equals(willId)) {
            throw new IllegalArgumentException("Asset does not belong to Will: " + willId);
        }
        UUID targetWillId = willId != null ? willId : asset.getWillId();
        WillStateEntity will = willStateRepository.findById(targetWillId)
                .orElseThrow(() -> new IllegalArgumentException("Will not found with ID: " + targetWillId));
        assetValidator.verifyWillModifiable(will);

        Beneficiary beneficiary = beneficiaryRepository.findById(beneficiaryId)
                .orElseThrow(() -> new IllegalArgumentException("Beneficiary not found with ID: " + beneficiaryId));

        if (!asset.getWillId().equals(beneficiary.getWillId())) {
            throw new IllegalArgumentException("Asset and Beneficiary must belong to the same Will");
        }

        if (sharePercentage <= 0 || sharePercentage > 100) {
            throw new IllegalArgumentException("Share percentage must be between 1 and 100");
        }

        // Validate that total allocation for this asset does not exceed 100%
        int currentAllocated = allocationRepository.findByAssetId(assetId).stream()
                .filter(a -> !a.getBeneficiaryId().equals(beneficiaryId))
                .mapToInt(AssetAllocation::getSharePercentage)
                .sum();
        if (currentAllocated + sharePercentage > 100) {
            throw new IllegalArgumentException("Total allocation for asset exceeds 100% (currently " +
                    currentAllocated + "%, requested " + sharePercentage + "%)");
        }

        Instant now = timeProvider.now();
        AssetAllocation allocation = allocationRepository.findByAssetIdAndBeneficiaryId(assetId, beneficiaryId)
                .orElseGet(() -> new AssetAllocation(UUID.randomUUID(), assetId, beneficiaryId, sharePercentage, instructions, now));

        allocation.setSharePercentage(sharePercentage);
        allocation.setInstructions(instructions);
        AssetAllocation saved = allocationRepository.save(allocation);

        log.info("Asset [{}] allocated to Beneficiary [{}] ({}%)", assetId, beneficiaryId, sharePercentage);
        return saved;
    }

    @Transactional
    public AssetAllocation allocateAsset(UUID assetId, UUID beneficiaryId, int sharePercentage, String instructions) {
        return allocateAsset(null, assetId, beneficiaryId, sharePercentage, instructions);
    }

    public List<AssetAllocation> listAllocations(UUID willId) {
        return allocationRepository.findByWillId(willId);
    }

    @Transactional
    public void deleteAllocation(UUID willId, UUID allocationId) {
        WillStateEntity will = willStateRepository.findById(willId)
                .orElseThrow(() -> new IllegalArgumentException("Will not found with ID: " + willId));
        assetValidator.verifyWillModifiable(will);

        AssetAllocation alloc = allocationRepository.findById(allocationId)
                .orElseThrow(() -> new IllegalArgumentException("Allocation not found with ID: " + allocationId));
        Asset asset = assetRepository.findById(alloc.getAssetId())
                .orElseThrow(() -> new IllegalArgumentException("Asset not found"));
        if (!asset.getWillId().equals(willId)) {
            throw new IllegalArgumentException("Allocation does not belong to Will: " + willId);
        }
        allocationRepository.delete(alloc);
        log.info("Allocation [{}] deleted from Will [{}]", allocationId, willId);
    }

    @Transactional(readOnly = true)
    public WillReview getWillReview(UUID willId) {
        WillStateEntity will = willStateRepository.findById(willId)
                .orElseThrow(() -> new IllegalArgumentException("Will not found with ID: " + willId));

        List<Asset> assets = assetRepository.findByWillId(willId);
        List<Beneficiary> beneficiaries = beneficiaryRepository.findByWillId(willId);
        List<AssetAllocation> allocations = allocationRepository.findByWillId(willId);
        long docCount = documentRepository.countByWillId(willId);
        long activeContactCount = willContactRepository.countByWillIdAndActiveTrue(willId);

        List<String> warnings = new ArrayList<>();
        boolean hasAssets = !assets.isEmpty();
        if (!hasAssets) warnings.add("No assets recorded in estate.");

        boolean hasBeneficiaries = !beneficiaries.isEmpty();
        if (!hasBeneficiaries) warnings.add("No beneficiaries designated.");

        boolean allFullyAllocated = true;
        for (Asset asset : assets) {
            int totalShare = allocations.stream()
                    .filter(a -> a.getAssetId().equals(asset.getId()))
                    .mapToInt(AssetAllocation::getSharePercentage)
                    .sum();
            if (totalShare < 100) {
                allFullyAllocated = false;
                warnings.add("Asset '" + asset.getTitle() + "' is only " + totalShare + "% allocated.");
            }
        }

        boolean hasQuorumContacts = activeContactCount >= 3;
        if (!hasQuorumContacts) {
            warnings.add("Fewer than 3 trusted contacts configured (" + activeContactCount + "/3). At least 3 recommended for 2-of-3 quorum.");
        }

        boolean ready = hasAssets && hasBeneficiaries && hasQuorumContacts && allFullyAllocated;

        Map<UUID, String> assetNames = new HashMap<>();
        for (Asset a : assets) assetNames.put(a.getId(), a.getTitle());

        Map<UUID, String> beneNames = new HashMap<>();
        for (Beneficiary b : beneficiaries) beneNames.put(b.getId(), b.getName());

        List<AllocationReviewItem> allocItems = allocations.stream().map(a -> new AllocationReviewItem(
                a.getId(),
                a.getAssetId(),
                assetNames.getOrDefault(a.getAssetId(), "Unknown Asset"),
                a.getBeneficiaryId(),
                beneNames.getOrDefault(a.getBeneficiaryId(), "Unknown Beneficiary"),
                a.getSharePercentage(),
                a.getInstructions()
        )).toList();

        return new WillReview(
                will.getId(),
                will.getTitle(),
                will.getState(),
                will.getCreatedAt(),
                will.getLastVerifiedActivityAt(),
                assets.size(),
                beneficiaries.size(),
                allocations.size(),
                (int) docCount,
                (int) activeContactCount,
                hasAssets,
                hasBeneficiaries,
                allFullyAllocated,
                hasQuorumContacts,
                ready,
                warnings,
                assets,
                beneficiaries,
                allocItems
        );
    }
}
