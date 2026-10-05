package com.digitalwill.estate.service;

import com.digitalwill.audit.model.AuditAction;
import com.digitalwill.audit.model.AuditResourceType;
import com.digitalwill.audit.model.AuditStatus;
import com.digitalwill.audit.service.AuditLogService;
import com.digitalwill.common.TimeProvider;
import com.digitalwill.estate.model.Asset;
import com.digitalwill.estate.model.AssetAllocation;
import com.digitalwill.estate.model.AssetCategory;
import com.digitalwill.estate.model.Beneficiary;
import com.digitalwill.estate.repository.AssetAllocationRepository;
import com.digitalwill.estate.repository.AssetRepository;
import com.digitalwill.estate.repository.BeneficiaryRepository;
import com.digitalwill.state.model.WillStateEntity;
import com.digitalwill.state.repository.WillStateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class EstateService {

    private static final Logger log = LoggerFactory.getLogger(EstateService.class);

    private final AssetRepository assetRepository;
    private final BeneficiaryRepository beneficiaryRepository;
    private final AssetAllocationRepository allocationRepository;
    private final WillStateRepository willStateRepository;
    private final AuditLogService auditLogService;
    private final TimeProvider timeProvider;

    public EstateService(AssetRepository assetRepository,
                         BeneficiaryRepository beneficiaryRepository,
                         AssetAllocationRepository allocationRepository,
                         WillStateRepository willStateRepository,
                         AuditLogService auditLogService,
                         TimeProvider timeProvider) {
        this.assetRepository = Objects.requireNonNull(assetRepository);
        this.beneficiaryRepository = Objects.requireNonNull(beneficiaryRepository);
        this.allocationRepository = Objects.requireNonNull(allocationRepository);
        this.willStateRepository = Objects.requireNonNull(willStateRepository);
        this.auditLogService = Objects.requireNonNull(auditLogService);
        this.timeProvider = Objects.requireNonNull(timeProvider);
    }

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
    public AssetAllocation allocateAsset(UUID assetId, UUID beneficiaryId, int sharePercentage, String instructions) {
        Asset asset = assetRepository.findById(assetId)
                .orElseThrow(() -> new IllegalArgumentException("Asset not found with ID: " + assetId));
        Beneficiary beneficiary = beneficiaryRepository.findById(beneficiaryId)
                .orElseThrow(() -> new IllegalArgumentException("Beneficiary not found with ID: " + beneficiaryId));

        if (!asset.getWillId().equals(beneficiary.getWillId())) {
            throw new IllegalArgumentException("Asset and Beneficiary must belong to the same Will");
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
}
