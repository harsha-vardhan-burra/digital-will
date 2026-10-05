package com.digitalwill.estate.web;

import com.digitalwill.audit.service.AuditLogService;
import com.digitalwill.audit.service.AuditVerificationResult;
import com.digitalwill.common.TimeProvider;
import com.digitalwill.estate.model.Asset;
import com.digitalwill.estate.model.AssetAllocation;
import com.digitalwill.estate.model.AssetCategory;
import com.digitalwill.estate.model.Beneficiary;
import com.digitalwill.estate.service.EstateService;
import com.digitalwill.release.service.ReleaseExecutionService;
import com.digitalwill.state.model.WillState;
import com.digitalwill.state.model.WillStateEntity;
import com.digitalwill.state.service.WillStateService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@RestController
@RequestMapping("/api/wills")
public class EstateController {

    private final WillStateService willStateService;
    private final EstateService estateService;
    private final ReleaseExecutionService releaseExecutionService;
    private final AuditLogService auditLogService;
    private final TimeProvider timeProvider;

    public EstateController(WillStateService willStateService,
                            EstateService estateService,
                            ReleaseExecutionService releaseExecutionService,
                            AuditLogService auditLogService,
                            TimeProvider timeProvider) {
        this.willStateService = Objects.requireNonNull(willStateService);
        this.estateService = Objects.requireNonNull(estateService);
        this.releaseExecutionService = Objects.requireNonNull(releaseExecutionService);
        this.auditLogService = Objects.requireNonNull(auditLogService);
        this.timeProvider = Objects.requireNonNull(timeProvider);
    }

    // --- DTOs ---

    public record CreateWillRequest(
            UUID ownerId,
            @NotBlank(message = "Title must not be blank") String title
    ) {}

    public record WillResponse(
            UUID id,
            UUID ownerId,
            String title,
            WillState state,
            Instant lastVerifiedActivityAt,
            Instant createdAt,
            Instant updatedAt
    ) {
        public static WillResponse fromEntity(WillStateEntity entity) {
            return new WillResponse(
                    entity.getId(),
                    entity.getOwnerId(),
                    entity.getTitle(),
                    entity.getState(),
                    entity.getLastVerifiedActivityAt(),
                    entity.getCreatedAt(),
                    entity.getUpdatedAt()
            );
        }
    }

    public record CancelRequest(String reason) {}

    public record CreateAssetRequest(
            @NotBlank(message = "Title must not be blank") String title,
            @NotNull(message = "Category must not be null") AssetCategory category,
            String description,
            String encryptedAccessData,
            String instructions
    ) {}

    public record CreateBeneficiaryRequest(
            @NotBlank(message = "Name must not be blank") String name,
            @NotBlank(message = "Email must not be blank") String email,
            String relationship
    ) {}

    public record AllocateAssetRequest(
            @NotNull(message = "Asset ID must not be null") UUID assetId,
            @NotNull(message = "Beneficiary ID must not be null") UUID beneficiaryId,
            @Min(value = 1, message = "Share percentage must be at least 1")
            @Max(value = 100, message = "Share percentage must not exceed 100") int sharePercentage,
            String instructions
    ) {}

    // --- Endpoints ---

    @PostMapping
    public ResponseEntity<WillResponse> createWill(@Valid @RequestBody CreateWillRequest request) {
        UUID owner = request.ownerId() != null ? request.ownerId() : UUID.randomUUID();
        WillStateEntity created = willStateService.createWill(owner, request.title());
        return ResponseEntity.status(HttpStatus.CREATED).body(WillResponse.fromEntity(created));
    }

    @GetMapping("/{id}")
    public ResponseEntity<WillResponse> getWill(@PathVariable UUID id) {
        WillStateEntity will = willStateService.getWillOrThrow(id);
        return ResponseEntity.ok(WillResponse.fromEntity(will));
    }

    @PostMapping("/{id}/check-in")
    public ResponseEntity<WillResponse> checkIn(@PathVariable UUID id) {
        willStateService.recordVerifiedActivity(id, timeProvider.now());
        WillStateEntity updated = willStateService.getWillOrThrow(id);
        return ResponseEntity.ok(WillResponse.fromEntity(updated));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<WillResponse> cancel(@PathVariable UUID id, @RequestBody(required = false) CancelRequest request) {
        willStateService.cancelSuccession(id);
        WillStateEntity updated = willStateService.getWillOrThrow(id);
        return ResponseEntity.ok(WillResponse.fromEntity(updated));
    }

    @PostMapping("/{id}/schedule-release")
    public ResponseEntity<WillResponse> scheduleRelease(@PathVariable UUID id) {
        willStateService.scheduleRelease(id, timeProvider.now());
        WillStateEntity updated = willStateService.getWillOrThrow(id);
        return ResponseEntity.ok(WillResponse.fromEntity(updated));
    }

    @PostMapping("/{id}/execute-release")
    public ResponseEntity<ReleaseExecutionService.ExecutionResult> executeRelease(@PathVariable UUID id) {
        ReleaseExecutionService.ExecutionResult result = releaseExecutionService.claimAndExecuteRelease(id);
        return ResponseEntity.ok(result);
    }

    // --- Assets ---

    @GetMapping("/{id}/assets")
    public ResponseEntity<List<Asset>> listAssets(@PathVariable UUID id) {
        return ResponseEntity.ok(estateService.listAssets(id));
    }

    @PostMapping("/{id}/assets")
    public ResponseEntity<Asset> addAsset(@PathVariable UUID id, @Valid @RequestBody CreateAssetRequest request) {
        Asset asset = estateService.addAsset(
                id,
                request.title(),
                request.category(),
                request.description(),
                request.encryptedAccessData(),
                request.instructions()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(asset);
    }

    // --- Beneficiaries ---

    @GetMapping("/{id}/beneficiaries")
    public ResponseEntity<List<Beneficiary>> listBeneficiaries(@PathVariable UUID id) {
        return ResponseEntity.ok(estateService.listBeneficiaries(id));
    }

    @PostMapping("/{id}/beneficiaries")
    public ResponseEntity<Beneficiary> addBeneficiary(@PathVariable UUID id, @Valid @RequestBody CreateBeneficiaryRequest request) {
        Beneficiary beneficiary = estateService.addBeneficiary(
                id,
                request.name(),
                request.email(),
                request.relationship()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(beneficiary);
    }

    // --- Allocations ---

    @GetMapping("/{id}/allocations")
    public ResponseEntity<List<AssetAllocation>> listAllocations(@PathVariable UUID id) {
        return ResponseEntity.ok(estateService.listAllocations(id));
    }

    @PostMapping("/{id}/allocations")
    public ResponseEntity<AssetAllocation> allocateAsset(@PathVariable UUID id, @Valid @RequestBody AllocateAssetRequest request) {
        AssetAllocation allocation = estateService.allocateAsset(
                request.assetId(),
                request.beneficiaryId(),
                request.sharePercentage(),
                request.instructions()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(allocation);
    }

    // --- Audit Integrity ---

    @GetMapping("/{id}/audit/verify")
    public ResponseEntity<AuditVerificationResult> verifyAuditChain(@PathVariable UUID id) {
        AuditVerificationResult result = auditLogService.verifyWillChainIntegrity(id);
        return ResponseEntity.ok(result);
    }
}
