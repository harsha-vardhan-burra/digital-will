package com.digitalwill.release.service;

import com.digitalwill.audit.model.AuditAction;
import com.digitalwill.audit.model.AuditResourceType;
import com.digitalwill.audit.model.AuditStatus;
import com.digitalwill.audit.service.AuditLogService;
import com.digitalwill.common.TimeProvider;
import com.digitalwill.estate.model.Asset;
import com.digitalwill.estate.model.AssetAllocation;
import com.digitalwill.estate.model.Beneficiary;
import com.digitalwill.estate.repository.AssetAllocationRepository;
import com.digitalwill.estate.repository.AssetRepository;
import com.digitalwill.estate.repository.BeneficiaryRepository;
import com.digitalwill.release.model.DisclosedRecord;
import com.digitalwill.release.model.DisclosureToken;
import com.digitalwill.release.model.DisclosureTokenStatus;
import com.digitalwill.release.model.ExecutionItemStatus;
import com.digitalwill.release.model.ExecutionStatus;
import com.digitalwill.release.model.ReleaseExecution;
import com.digitalwill.release.model.ReleaseExecutionItem;
import com.digitalwill.release.repository.DisclosedRecordRepository;
import com.digitalwill.release.repository.DisclosureTokenRepository;
import com.digitalwill.release.repository.ReleaseExecutionItemRepository;
import com.digitalwill.release.repository.ReleaseExecutionRepository;
import com.digitalwill.state.model.WillState;
import com.digitalwill.state.model.WillStateEntity;
import com.digitalwill.state.repository.WillStateRepository;
import com.digitalwill.state.service.WillStateService;
import com.digitalwill.verification.service.VerificationTokenService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Service managing atomic release execution, partial failure handling, idempotency, and recovery (PR5).
 */
@Service
public class ReleaseExecutionService {

    private static final Logger log = LoggerFactory.getLogger(ReleaseExecutionService.class);
    private static final Duration DEFAULT_DISCLOSURE_TOKEN_TTL = Duration.ofDays(30);
    private static final int MAX_RETRY_COUNT = 3;

    private final WillStateRepository willStateRepository;
    private final WillStateService willStateService;
    private final BeneficiaryRepository beneficiaryRepository;
    private final AssetRepository assetRepository;
    private final AssetAllocationRepository allocationRepository;
    private final ReleaseExecutionRepository executionRepository;
    private final ReleaseExecutionItemRepository executionItemRepository;
    private final DisclosureTokenRepository disclosureTokenRepository;
    private final DisclosedRecordRepository disclosedRecordRepository;
    private final VerificationTokenService tokenService;
    private final AuditLogService auditLogService;
    private final TimeProvider timeProvider;
    private final com.digitalwill.notification.service.NotificationDeliveryService notificationDeliveryService;

    @org.springframework.beans.factory.annotation.Autowired
    public ReleaseExecutionService(WillStateRepository willStateRepository,
                                   WillStateService willStateService,
                                   BeneficiaryRepository beneficiaryRepository,
                                   AssetRepository assetRepository,
                                   AssetAllocationRepository allocationRepository,
                                   ReleaseExecutionRepository executionRepository,
                                   ReleaseExecutionItemRepository executionItemRepository,
                                   DisclosureTokenRepository disclosureTokenRepository,
                                   DisclosedRecordRepository disclosedRecordRepository,
                                   VerificationTokenService tokenService,
                                   AuditLogService auditLogService,
                                   TimeProvider timeProvider,
                                   com.digitalwill.notification.service.NotificationDeliveryService notificationDeliveryService) {
        this.willStateRepository = Objects.requireNonNull(willStateRepository);
        this.willStateService = Objects.requireNonNull(willStateService);
        this.beneficiaryRepository = Objects.requireNonNull(beneficiaryRepository);
        this.assetRepository = Objects.requireNonNull(assetRepository);
        this.allocationRepository = Objects.requireNonNull(allocationRepository);
        this.executionRepository = Objects.requireNonNull(executionRepository);
        this.executionItemRepository = Objects.requireNonNull(executionItemRepository);
        this.disclosureTokenRepository = Objects.requireNonNull(disclosureTokenRepository);
        this.disclosedRecordRepository = Objects.requireNonNull(disclosedRecordRepository);
        this.tokenService = Objects.requireNonNull(tokenService);
        this.auditLogService = Objects.requireNonNull(auditLogService);
        this.timeProvider = Objects.requireNonNull(timeProvider);
        this.notificationDeliveryService = notificationDeliveryService;
    }

    public ReleaseExecutionService(WillStateRepository willStateRepository,
                                   WillStateService willStateService,
                                   BeneficiaryRepository beneficiaryRepository,
                                   AssetRepository assetRepository,
                                   AssetAllocationRepository allocationRepository,
                                   ReleaseExecutionRepository executionRepository,
                                   ReleaseExecutionItemRepository executionItemRepository,
                                   DisclosureTokenRepository disclosureTokenRepository,
                                   DisclosedRecordRepository disclosedRecordRepository,
                                   VerificationTokenService tokenService,
                                   AuditLogService auditLogService,
                                   TimeProvider timeProvider) {
        this(willStateRepository, willStateService, beneficiaryRepository, assetRepository,
             allocationRepository, executionRepository, executionItemRepository, disclosureTokenRepository,
             disclosedRecordRepository, tokenService, auditLogService, timeProvider, null);
    }

    public record ExecutionResult(
            boolean executed,
            ExecutionStatus status,
            int totalItems,
            int completedItems,
            int failedItems,
            String message
    ) {}

    /**
     * Atomically claims execution from RELEASE_PENDING -> EXECUTING and executes controlled disclosure.
     * Guaranteed idempotent and safe against concurrent executions.
     */
    @Transactional
    public ExecutionResult claimAndExecuteRelease(UUID willId) {
        WillStateEntity will = willStateRepository.findById(willId)
                .orElseThrow(() -> new IllegalArgumentException("Will not found with ID: " + willId));

        Instant now = timeProvider.now();

        // 1. Idempotency: if already EXECUTED, return completed immediately
        if (will.getState() == WillState.EXECUTED) {
            return new ExecutionResult(true, ExecutionStatus.COMPLETED, 0, 0, 0, "Will is already EXECUTED");
        }

        // 2. Claim execution if in RELEASE_PENDING
        if (will.getState() == WillState.RELEASE_PENDING) {
            boolean claimed = willStateService.claimExecution(willId);
            if (!claimed) {
                // Raced with another worker or not yet eligible
                return new ExecutionResult(false, ExecutionStatus.NOT_STARTED, 0, 0, 0, "Execution claim rejected or raced");
            }
            auditLogService.logCritical(
                    willId, "SYSTEM", "SYSTEM_JOB",
                    AuditAction.RELEASE_CLAIMED, AuditStatus.SUCCESS,
                    AuditResourceType.RELEASE, willId.toString(),
                    "{\"timestamp\":\"" + now + "\"}"
            );
        } else if (will.getState() == WillState.EXECUTING) {
            return new ExecutionResult(false, ExecutionStatus.IN_PROGRESS, 0, 0, 0, "Execution already claimed and in progress");
        } else {
            return new ExecutionResult(false, ExecutionStatus.NOT_STARTED, 0, 0, 0, "Will not in releaseable state: " + will.getState());
        }

        // 3. Obtain or initialize ReleaseExecution record
        ReleaseExecution execution = executionRepository.findByWillId(willId)
                .orElseGet(() -> executionRepository.saveAndFlush(new ReleaseExecution(UUID.randomUUID(), willId, ExecutionStatus.IN_PROGRESS, now)));

        execution.setLastAttemptAt(now);
        execution.setStatus(ExecutionStatus.IN_PROGRESS);
        executionRepository.saveAndFlush(execution);

        // 4. Load beneficiaries and execute controlled disclosure items
        List<Beneficiary> beneficiaries = beneficiaryRepository.findByWillId(willId);
        execution.setTotalItems(beneficiaries.size());

        int completed = 0;
        int failed = 0;

        for (Beneficiary beneficiary : beneficiaries) {
            Optional<ReleaseExecutionItem> existingItemOpt =
                    executionItemRepository.findByExecutionIdAndTargetIdAndItemType(
                            execution.getId(), beneficiary.getId(), "BENEFICIARY_DISCLOSURE"
                    );

            ReleaseExecutionItem item = existingItemOpt.orElseGet(() ->
                    executionItemRepository.save(new ReleaseExecutionItem(
                            UUID.randomUUID(), execution.getId(), "BENEFICIARY_DISCLOSURE",
                            beneficiary.getId(), ExecutionItemStatus.PENDING
                    ))
            );

            if (item.getStatus() == ExecutionItemStatus.COMPLETED) {
                completed++;
                continue; // Idempotent skip
            }

            try {
                processBeneficiaryDisclosure(willId, beneficiary, now);
                item.setStatus(ExecutionItemStatus.COMPLETED);
                item.setCompletedAt(now);
                item.setErrorMessage(null);
                executionItemRepository.save(item);
                completed++;
            } catch (Exception e) {
                log.error("Failed to process disclosure for beneficiary [{}] on will [{}]: {}",
                        beneficiary.getId(), willId, e.getMessage(), e);
                item.setStatus(ExecutionItemStatus.FAILED);
                item.setErrorMessage(e.getMessage());
                executionItemRepository.save(item);
                failed++;
            }
        }

        execution.setCompletedItems(completed);
        execution.setFailedItems(failed);

        // 5. Evaluate final execution state
        if (failed == 0 && completed == execution.getTotalItems()) {
            execution.setStatus(ExecutionStatus.COMPLETED);
            execution.setCompletedAt(now);
            executionRepository.saveAndFlush(execution);

            // Complete state engine transition EXECUTING -> EXECUTED (terminal)
            willStateService.completeExecution(willId);

            auditLogService.logCritical(
                    willId, "SYSTEM", "SYSTEM_JOB",
                    AuditAction.RELEASE_EXECUTED, AuditStatus.SUCCESS,
                    AuditResourceType.RELEASE, execution.getId().toString(),
                    "{\"completedItems\":" + completed + "}"
            );
            return new ExecutionResult(true, ExecutionStatus.COMPLETED, execution.getTotalItems(), completed, failed, "Release completed successfully");
        } else {
            // Partial failure detected (Section 21)
            execution.setStatus(ExecutionStatus.PARTIALLY_COMPLETED);
            executionRepository.saveAndFlush(execution);

            auditLogService.logCritical(
                    willId, "SYSTEM", "SYSTEM_JOB",
                    AuditAction.SECURITY_ALERT, AuditStatus.FAILURE,
                    AuditResourceType.RELEASE, execution.getId().toString(),
                    "{\"error\":\"Partial release failure\",\"failedItems\":" + failed + ",\"completedItems\":" + completed + "}"
            );
            return new ExecutionResult(false, ExecutionStatus.PARTIALLY_COMPLETED, execution.getTotalItems(), completed, failed, "Partial release failure: " + failed + " items failed");
        }
    }

    private void processBeneficiaryDisclosure(UUID willId, Beneficiary beneficiary, Instant now) {
        // Collect allocated assets for this beneficiary
        List<AssetAllocation> allocations = allocationRepository.findByBeneficiaryId(beneficiary.getId());
        List<String> assetSummaries = new ArrayList<>();

        for (AssetAllocation alloc : allocations) {
            assetRepository.findById(alloc.getAssetId()).ifPresent(asset -> {
                String summary = "{\"assetId\":\"" + asset.getId() + "\",\"title\":\"" + asset.getTitle() +
                        "\",\"category\":\"" + asset.getCategory() + "\",\"sharePercentage\":" + alloc.getSharePercentage() +
                        ",\"instructions\":\"" + (alloc.getInstructions() != null ? alloc.getInstructions() : "") + "\"}";
                assetSummaries.add(summary);
            });
        }

        String payloadJson = "{\"beneficiaryId\":\"" + beneficiary.getId() + "\",\"name\":\"" + beneficiary.getName() +
                "\",\"disclosedAt\":\"" + now + "\",\"allocations\":[" + String.join(",", assetSummaries) + "]}";

        // Generate cryptographically secure disclosure token
        String rawToken = tokenService.generateRawToken();
        String tokenHash = tokenService.hashToken(rawToken);

        DisclosureToken token = disclosureTokenRepository.findByWillIdAndBeneficiaryId(willId, beneficiary.getId())
                .orElseGet(() -> new DisclosureToken(
                        UUID.randomUUID(), willId, beneficiary.getId(), tokenHash,
                        DisclosureTokenStatus.ACTIVE, now, now.plus(DEFAULT_DISCLOSURE_TOKEN_TTL)
                ));

        token.setTokenHash(tokenHash);
        token.setStatus(DisclosureTokenStatus.ACTIVE);
        token.setExpiresAt(now.plus(DEFAULT_DISCLOSURE_TOKEN_TTL));
        disclosureTokenRepository.save(token);

        // Snapshot into disclosed_records
        DisclosedRecord record = disclosedRecordRepository.findByWillIdAndBeneficiaryId(willId, beneficiary.getId())
                .orElseGet(() -> new DisclosedRecord(
                        UUID.randomUUID(), willId, beneficiary.getId(), token.getId(), payloadJson, now
                ));

        record.setDisclosureTokenId(token.getId());
        record.setPackagePayloadJson(payloadJson);
        disclosedRecordRepository.save(record);

        auditLogService.logCritical(
                willId, "SYSTEM", "SYSTEM_JOB",
                AuditAction.DISCLOSURE_GENERATED, AuditStatus.SUCCESS,
                AuditResourceType.DISCLOSURE, record.getId().toString(),
                "{\"beneficiaryId\":\"" + beneficiary.getId() + "\"}"
        );

        if (notificationDeliveryService != null) {
            notificationDeliveryService.sendDisclosureNotification(beneficiary.getEmail(), beneficiary.getName(), willId, rawToken);
        }
    }

    /**
     * Recovers stalled EXECUTING state if worker crashed or timed out.
     */
    @Transactional
    public ExecutionResult recoverStaleExecution(UUID willId, Duration recoveryTimeout) {
        WillStateEntity will = willStateRepository.findById(willId)
                .orElseThrow(() -> new IllegalArgumentException("Will not found: " + willId));

        if (will.getState() != WillState.EXECUTING) {
            return new ExecutionResult(false, ExecutionStatus.NOT_STARTED, 0, 0, 0, "Will is not EXECUTING: " + will.getState());
        }

        Instant now = timeProvider.now();
        Instant executingAt = will.getExecutingAt();
        if (executingAt == null || now.isBefore(executingAt.plus(recoveryTimeout))) {
            return new ExecutionResult(false, ExecutionStatus.IN_PROGRESS, 0, 0, 0, "Execution has not exceeded timeout");
        }

        ReleaseExecution execution = executionRepository.findByWillId(willId).orElse(null);
        if (execution != null && execution.getStatus() == ExecutionStatus.COMPLETED) {
            // Already completed items, state engine just missed final transition
            willStateService.completeExecution(willId);
            return new ExecutionResult(true, ExecutionStatus.COMPLETED, execution.getTotalItems(), execution.getCompletedItems(), 0, "Recovered completed execution to EXECUTED");
        }

        int retries = execution != null ? execution.getRetryCount() + 1 : 1;
        if (execution != null) {
            execution.setRetryCount(retries);
            executionRepository.save(execution);
        }

        // 1. Transition state engine back from EXECUTING -> RELEASE_PENDING
        boolean recovered = willStateService.recoverStaleExecution(willId, recoveryTimeout);
        if (!recovered) {
            return new ExecutionResult(false, ExecutionStatus.IN_PROGRESS, 0, 0, 0, "Failed to recover stalled state transition");
        }

        // 2. If max retries exceeded, remain in RELEASE_PENDING and mark FAILED_PERMANENT
        if (retries > MAX_RETRY_COUNT) {
            log.warn("Will [{}] exceeded max release retries ({}). Reverted to RELEASE_PENDING for intervention", willId, retries);
            if (execution != null) {
                execution.setStatus(ExecutionStatus.FAILED_PERMANENT);
                executionRepository.save(execution);
            }
            auditLogService.logCritical(
                    willId, "SYSTEM", "SYSTEM_JOB",
                    AuditAction.EXECUTION_RECOVERY, AuditStatus.FAILURE,
                    AuditResourceType.RELEASE, willId.toString(),
                    "{\"reason\":\"Max retries exceeded\",\"retries\":" + retries + "}"
            );
            return new ExecutionResult(false, ExecutionStatus.FAILED_PERMANENT, 0, 0, 0, "Max retries exceeded; reverted to RELEASE_PENDING");
        }

        log.info("Attempting release retry [{}/{}] for Will [{}]", retries, MAX_RETRY_COUNT, willId);
        auditLogService.logCritical(
                willId, "SYSTEM", "SYSTEM_JOB",
                AuditAction.EXECUTION_RECOVERY, AuditStatus.SUCCESS,
                AuditResourceType.RELEASE, willId.toString(),
                "{\"retryCount\":" + retries + "}"
        );
        // 3. Re-claim RELEASE_PENDING -> EXECUTING and resume release idempotently
        return claimAndExecuteRelease(willId);
    }
}
