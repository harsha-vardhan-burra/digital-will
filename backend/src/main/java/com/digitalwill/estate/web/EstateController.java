package com.digitalwill.estate.web;

import com.digitalwill.audit.model.AuditLogEntry;
import com.digitalwill.audit.service.AuditLogService;
import com.digitalwill.audit.service.AuditVerificationResult;
import com.digitalwill.auth.security.UserPrincipal;
import com.digitalwill.common.TimeProvider;
import com.digitalwill.estate.model.Asset;
import com.digitalwill.estate.model.AssetAllocation;
import com.digitalwill.estate.model.AssetCategory;
import com.digitalwill.estate.model.Beneficiary;
import com.digitalwill.estate.service.EstateService;
import com.digitalwill.release.service.ReleaseExecutionService;
import com.digitalwill.state.model.WillState;
import com.digitalwill.state.model.WillStateEntity;
import com.digitalwill.state.repository.WillStateRepository;
import com.digitalwill.state.service.WillStateService;
import com.digitalwill.verification.service.VerificationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@RestController
@RequestMapping("/api/wills")
public class EstateController {

    private final WillStateService willStateService;
    private final WillStateRepository willStateRepository;
    private final EstateService estateService;
    private final ReleaseExecutionService releaseExecutionService;
    private final VerificationService verificationService;
    private final AuditLogService auditLogService;
    private final TimeProvider timeProvider;

    public EstateController(WillStateService willStateService,
                            WillStateRepository willStateRepository,
                            EstateService estateService,
                            ReleaseExecutionService releaseExecutionService,
                            VerificationService verificationService,
                            AuditLogService auditLogService,
                            TimeProvider timeProvider) {
        this.willStateService = Objects.requireNonNull(willStateService);
        this.willStateRepository = Objects.requireNonNull(willStateRepository);
        this.estateService = Objects.requireNonNull(estateService);
        this.releaseExecutionService = Objects.requireNonNull(releaseExecutionService);
        this.verificationService = Objects.requireNonNull(verificationService);
        this.auditLogService = Objects.requireNonNull(auditLogService);
        this.timeProvider = Objects.requireNonNull(timeProvider);
    }

    private WillStateEntity checkOwnership(UUID willId, UserPrincipal principal) {
        if (principal == null) {
            throw new SecurityException("Authentication required");
        }
        WillStateEntity will = willStateService.getWillOrThrow(willId);
        if (!will.getOwnerId().equals(principal.getId())) {
            throw new SecurityException("Unauthorized: actor is not the owner of Will: " + willId);
        }
        return will;
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
            @NotBlank(message = "Email must not be blank") @Email(message = "Email must be valid") String email,
            String relationship
    ) {}

    public record AllocateAssetRequest(
            @NotNull(message = "Asset ID must not be null") UUID assetId,
            @NotNull(message = "Beneficiary ID must not be null") UUID beneficiaryId,
            @Min(value = 1, message = "Share percentage must be at least 1")
            @Max(value = 100, message = "Share percentage must not exceed 100") int sharePercentage,
            String instructions
    ) {}

    public record AddContactRequest(
            @NotBlank(message = "Name must not be blank") String name,
            @NotBlank(message = "Email must not be blank") @Email(message = "Email must be valid") String email
    ) {}

    public record AuditLogDto(
            UUID id,
            long sequenceNumber,
            String action,
            String status,
            String actorType,
            String resourceType,
            String resourceId,
            Instant createdAt,
            String prevHash,
            String entryHash,
            String detailsJson
    ) {
        public static AuditLogDto fromEntity(AuditLogEntry e) {
            String sanitizedDetails = e.getDetailsJson();
            if (sanitizedDetails != null) {
                sanitizedDetails = sanitizedDetails.replaceAll("\"rawToken\":\\s*\"[^\"]*\"", "\"rawToken\":\"[REDACTED]\"");
                sanitizedDetails = sanitizedDetails.replaceAll("\"password\":\\s*\"[^\"]*\"", "\"password\":\"[REDACTED]\"");
            }
            return new AuditLogDto(
                    e.getId(),
                    e.getSequenceNumber(),
                    e.getAction().name(),
                    e.getStatus().name(),
                    e.getActorType(),
                    e.getResourceType().name(),
                    e.getResourceId(),
                    e.getCreatedAt(),
                    e.getPrevHash(),
                    e.getEntryHash(),
                    sanitizedDetails
            );
        }
    }

    // --- Endpoints ---

    @PostMapping
    public ResponseEntity<WillResponse> createWill(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateWillRequest request) {
        if (principal == null) {
            throw new SecurityException("Authentication required");
        }
        UUID owner = principal.getId();

        // Enforce 1 Will per user MVP constraint
        List<WillStateEntity> existing = willStateRepository.findByOwnerId(owner);
        if (!existing.isEmpty()) {
            throw new IllegalStateException("User already owns a Digital Will: " + existing.get(0).getId());
        }

        WillStateEntity created = willStateService.createWill(owner, request.title());
        return ResponseEntity.status(HttpStatus.CREATED).body(WillResponse.fromEntity(created));
    }

    @GetMapping("/my")
    public ResponseEntity<WillResponse> getMyWill(@AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null) {
            throw new SecurityException("Authentication required");
        }
        WillStateEntity will = willStateRepository.findByOwnerId(principal.getId()).stream().findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No Digital Will found for current user"));
        return ResponseEntity.ok(WillResponse.fromEntity(will));
    }

    @GetMapping("/{id}")
    public ResponseEntity<WillResponse> getWill(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {
        WillStateEntity will = checkOwnership(id, principal);
        return ResponseEntity.ok(WillResponse.fromEntity(will));
    }

    @PostMapping("/{id}/check-in")
    public ResponseEntity<WillResponse> checkIn(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {
        checkOwnership(id, principal);
        willStateService.recordVerifiedActivity(id, timeProvider.now());
        WillStateEntity updated = willStateService.getWillOrThrow(id);
        return ResponseEntity.ok(WillResponse.fromEntity(updated));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<WillResponse> cancel(
            @PathVariable UUID id,
            @RequestBody(required = false) CancelRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        checkOwnership(id, principal);
        willStateService.cancelSuccession(id);
        WillStateEntity updated = willStateService.getWillOrThrow(id);
        return ResponseEntity.ok(WillResponse.fromEntity(updated));
    }

    @PostMapping("/{id}/schedule-release")
    public ResponseEntity<WillResponse> scheduleRelease(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {
        checkOwnership(id, principal);
        willStateService.scheduleRelease(id, timeProvider.now());
        WillStateEntity updated = willStateService.getWillOrThrow(id);
        return ResponseEntity.ok(WillResponse.fromEntity(updated));
    }

    @PostMapping("/{id}/execute-release")
    public ResponseEntity<ReleaseExecutionService.ExecutionResult> executeRelease(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {
        checkOwnership(id, principal);
        ReleaseExecutionService.ExecutionResult result = releaseExecutionService.claimAndExecuteRelease(id);
        return ResponseEntity.ok(result);
    }

    // --- Assets ---

    @GetMapping("/{id}/assets")
    public ResponseEntity<List<Asset>> listAssets(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {
        checkOwnership(id, principal);
        return ResponseEntity.ok(estateService.listAssets(id));
    }

    @PostMapping("/{id}/assets")
    public ResponseEntity<Asset> addAsset(
            @PathVariable UUID id,
            @Valid @RequestBody CreateAssetRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        checkOwnership(id, principal);
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

    @DeleteMapping("/{id}/assets/{assetId}")
    public ResponseEntity<Map<String, String>> deleteAsset(
            @PathVariable UUID id,
            @PathVariable UUID assetId,
            @AuthenticationPrincipal UserPrincipal principal) {
        checkOwnership(id, principal);
        estateService.deleteAsset(id, assetId);
        return ResponseEntity.ok(Map.of("message", "Asset deleted successfully"));
    }

    // --- Beneficiaries ---

    @GetMapping("/{id}/beneficiaries")
    public ResponseEntity<List<Beneficiary>> listBeneficiaries(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {
        checkOwnership(id, principal);
        return ResponseEntity.ok(estateService.listBeneficiaries(id));
    }

    @PostMapping("/{id}/beneficiaries")
    public ResponseEntity<Beneficiary> addBeneficiary(
            @PathVariable UUID id,
            @Valid @RequestBody CreateBeneficiaryRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        checkOwnership(id, principal);
        Beneficiary beneficiary = estateService.addBeneficiary(
                id,
                request.name(),
                request.email(),
                request.relationship()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(beneficiary);
    }

    @DeleteMapping("/{id}/beneficiaries/{beneficiaryId}")
    public ResponseEntity<Map<String, String>> deleteBeneficiary(
            @PathVariable UUID id,
            @PathVariable UUID beneficiaryId,
            @AuthenticationPrincipal UserPrincipal principal) {
        checkOwnership(id, principal);
        estateService.deleteBeneficiary(id, beneficiaryId);
        return ResponseEntity.ok(Map.of("message", "Beneficiary deleted successfully"));
    }

    // --- Allocations ---

    @GetMapping("/{id}/allocations")
    public ResponseEntity<List<AssetAllocation>> listAllocations(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {
        checkOwnership(id, principal);
        return ResponseEntity.ok(estateService.listAllocations(id));
    }

    @PostMapping("/{id}/allocations")
    public ResponseEntity<AssetAllocation> allocateAsset(
            @PathVariable UUID id,
            @Valid @RequestBody AllocateAssetRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        checkOwnership(id, principal);
        AssetAllocation allocation = estateService.allocateAsset(
                id,
                request.assetId(),
                request.beneficiaryId(),
                request.sharePercentage(),
                request.instructions()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(allocation);
    }

    @DeleteMapping("/{id}/allocations/{allocationId}")
    public ResponseEntity<Map<String, String>> deleteAllocation(
            @PathVariable UUID id,
            @PathVariable UUID allocationId,
            @AuthenticationPrincipal UserPrincipal principal) {
        checkOwnership(id, principal);
        estateService.deleteAllocation(id, allocationId);
        return ResponseEntity.ok(Map.of("message", "Allocation deleted successfully"));
    }

    // --- Trusted Contacts ---

    @GetMapping("/{id}/contacts")
    public ResponseEntity<List<VerificationService.WillContactDetailed>> listContacts(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {
        checkOwnership(id, principal);
        return ResponseEntity.ok(verificationService.listContactsForWillDetailed(id));
    }

    @PostMapping("/{id}/contacts")
    public ResponseEntity<VerificationService.WillContactDetailed> addContact(
            @PathVariable UUID id,
            @Valid @RequestBody AddContactRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        checkOwnership(id, principal);
        VerificationService.WillContactDetailed contact =
                verificationService.addTrustedContactToWill(id, request.name(), request.email());
        return ResponseEntity.status(HttpStatus.CREATED).body(contact);
    }

    @DeleteMapping("/{id}/contacts/{contactId}")
    public ResponseEntity<Map<String, String>> deactivateContact(
            @PathVariable UUID id,
            @PathVariable UUID contactId,
            @AuthenticationPrincipal UserPrincipal principal) {
        checkOwnership(id, principal);
        verificationService.deactivateContactForWill(id, contactId);
        return ResponseEntity.ok(Map.of("message", "Trusted contact deactivated successfully"));
    }

    // --- Will Review / Preview ---

    @GetMapping("/{id}/review")
    public ResponseEntity<EstateService.WillReview> getWillReview(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {
        checkOwnership(id, principal);
        return ResponseEntity.ok(estateService.getWillReview(id));
    }

    // --- Audit Trail & Integrity ---

    @GetMapping("/{id}/audit")
    public ResponseEntity<List<AuditLogDto>> getAuditHistory(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {
        checkOwnership(id, principal);
        List<AuditLogDto> dtos = auditLogService.getAuditHistoryForWill(id).stream()
                .map(AuditLogDto::fromEntity)
                .toList();
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/{id}/audit/verify")
    public ResponseEntity<AuditVerificationResult> verifyAuditChain(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {
        checkOwnership(id, principal);
        AuditVerificationResult result = auditLogService.verifyWillChainIntegrity(id);
        return ResponseEntity.ok(result);
    }
}
