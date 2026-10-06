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

    public EstateService(AssetRepository assetRepository,
                         BeneficiaryRepository beneficiaryRepository,
                         AssetAllocationRepository allocationRepository,
                         WillStateRepository willStateRepository,
                         EncryptedDocumentRepository documentRepository,
                         WillContactRepository willContactRepository,
                         AuditLogService auditLogService,
                         TimeProvider timeProvider) {
        this.assetRepository = Objects.requireNonNull(assetRepository);
        this.beneficiaryRepository = Objects.requireNonNull(beneficiaryRepository);
        this.allocationRepository = Objects.requireNonNull(allocationRepository);
        this.willStateRepository = Objects.requireNonNull(willStateRepository);
        this.documentRepository = Objects.requireNonNull(documentRepository);
        this.willContactRepository = Objects.requireNonNull(willContactRepository);
        this.auditLogService = Objects.requireNonNull(auditLogService);
        this.timeProvider = Objects.requireNonNull(timeProvider);
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
                          String description, String encryptedAccessData, String instructions) {
        WillStateEntity will = willStateRepository.findById(willId)
                .orElseThrow(() -> new IllegalArgumentException("Will not found with ID: " + willId));

        Instant now = timeProvider.now();
        UUID assetId = UUID.randomUUID();
        Asset asset = new Asset(assetId, willId, title, category, description, encryptedAccessData, instructions, now, now);
        Asset saved = assetRepository.save(asset);

        auditLogService.logCritical(
                willId,
                will.getOwnerId().toString(),
                "OWNER",
                AuditAction.OWNER_ACTIVITY,
                AuditStatus.SUCCESS,
                AuditResourceType.WILL,
                assetId.toString(),
                "{\"action\":\"ADD_ASSET\",\"title\":\"" + title + "\",\"category\":\"" + category + "\"}"
        );

        log.info("Asset [{}] ({}) added to Will [{}]", assetId, title, willId);
        return saved;
    }

    public List<Asset> listAssets(UUID willId) {
        return assetRepository.findByWillId(willId);
    }

    @Transactional
    public void deleteAsset(UUID willId, UUID assetId) {
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

        Instant now = timeProvider.now();
        UUID beneficiaryId = UUID.randomUUID();
        Beneficiary beneficiary = new Beneficiary(beneficiaryId, willId, name, email, relationship, now);
        Beneficiary saved = beneficiaryRepository.save(beneficiary);

        auditLogService.logCritical(
                willId,
                will.getOwnerId().toString(),
                "OWNER",
                AuditAction.OWNER_ACTIVITY,
                AuditStatus.SUCCESS,
                AuditResourceType.WILL,
                beneficiaryId.toString(),
                "{\"action\":\"ADD_BENEFICIARY\",\"name\":\"" + name + "\"}"
        );

        log.info("Beneficiary [{}] ({}) added to Will [{}]", beneficiaryId, name, willId);
        return saved;
    }

    public List<Beneficiary> listBeneficiaries(UUID willId) {
        return beneficiaryRepository.findByWillId(willId);
    }

    @Transactional
    public void deleteBeneficiary(UUID willId, UUID beneficiaryId) {
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
    public AssetAllocation allocateAsset(UUID assetId, UUID beneficiaryId, int sharePercentage, String instructions) {
        Asset asset = assetRepository.findById(assetId)
                .orElseThrow(() -> new IllegalArgumentException("Asset not found with ID: " + assetId));
        Beneficiary beneficiary = beneficiaryRepository.findById(beneficiaryId)
                .orElseThrow(() -> new IllegalArgumentException("Beneficiary not found with ID: " + beneficiaryId));

        if (!asset.getWillId().equals(beneficiary.getWillId())) {
            throw new IllegalArgumentException("Asset and Beneficiary must belong to the same Will");
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

    public List<AssetAllocation> listAllocations(UUID willId) {
        return allocationRepository.findByWillId(willId);
    }

    @Transactional
    public void deleteAllocation(UUID willId, UUID allocationId) {
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

        boolean ready = hasAssets && hasBeneficiaries && hasQuorumContacts;

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
