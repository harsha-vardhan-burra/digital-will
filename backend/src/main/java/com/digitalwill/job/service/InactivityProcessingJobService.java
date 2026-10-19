package com.digitalwill.job.service;

import com.digitalwill.audit.model.AuditAction;
import com.digitalwill.audit.model.AuditResourceType;
import com.digitalwill.audit.model.AuditStatus;
import com.digitalwill.audit.service.AuditLogService;
import com.digitalwill.common.TimeProvider;
import com.digitalwill.job.config.JobProperties;
import com.digitalwill.state.exception.IllegalStateTransitionException;
import com.digitalwill.state.exception.TransitionGuardFailedException;
import com.digitalwill.state.model.WillStateEntity;
import com.digitalwill.state.repository.WillStateRepository;
import com.digitalwill.state.service.WillStateService;
import com.digitalwill.verification.model.VerificationRequestStatus;
import com.digitalwill.verification.model.WillContact;
import com.digitalwill.verification.repository.VerificationRequestRepository;
import com.digitalwill.verification.repository.WillContactRepository;
import com.digitalwill.verification.service.VerificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Service orchestrating background inactivity processing across succession stages.
 * Conforms to PR2 & Phase-2 reliability rules:
 * - Deterministic TimeProvider time evaluation
 * - Never skips required lifecycle stages (ACTIVE -> WARNING -> FINAL_WARNING -> VERIFICATION_PENDING)
 * - Safe under concurrent or duplicate execution via guarded conditional updates
 * - Isolates failures per Will: failure of one Will never aborts the batch
 * - Uses keyset pagination to prevent unbounded memory consumption and poison row starvation
 */
@Service
public class InactivityProcessingJobService {

    private static final Logger log = LoggerFactory.getLogger(InactivityProcessingJobService.class);

    private final WillStateRepository willStateRepository;
    private final WillStateService willStateService;
    private final WillContactRepository willContactRepository;
    private final VerificationService verificationService;
    private final VerificationRequestRepository verificationRequestRepository;
    private final AuditLogService auditLogService;
    private final TimeProvider timeProvider;
    private final JobProperties jobProperties;

    @Autowired
    public InactivityProcessingJobService(WillStateRepository willStateRepository,
                                         WillStateService willStateService,
                                         WillContactRepository willContactRepository,
                                         VerificationService verificationService,
                                         VerificationRequestRepository verificationRequestRepository,
                                         AuditLogService auditLogService,
                                         TimeProvider timeProvider,
                                         JobProperties jobProperties) {
        this.willStateRepository = Objects.requireNonNull(willStateRepository, "willStateRepository must not be null");
        this.willStateService = Objects.requireNonNull(willStateService, "willStateService must not be null");
        this.willContactRepository = Objects.requireNonNull(willContactRepository, "willContactRepository must not be null");
        this.verificationService = Objects.requireNonNull(verificationService, "verificationService must not be null");
        this.verificationRequestRepository = verificationRequestRepository;
        this.auditLogService = Objects.requireNonNull(auditLogService, "auditLogService must not be null");
        this.timeProvider = Objects.requireNonNull(timeProvider, "timeProvider must not be null");
        this.jobProperties = Objects.requireNonNull(jobProperties, "jobProperties must not be null");
    }

    public InactivityProcessingJobService(WillStateRepository willStateRepository,
                                         WillStateService willStateService,
                                         WillContactRepository willContactRepository,
                                         VerificationService verificationService,
                                         AuditLogService auditLogService,
                                         TimeProvider timeProvider,
                                         JobProperties jobProperties) {
        this(willStateRepository, willStateService, willContactRepository, verificationService,
                null, auditLogService, timeProvider, jobProperties);
    }

    public record InactivityJobReport(
            Instant executionTimestamp,
            int warnedActiveWills,
            int finalWarnedWills,
            int initiatedVerifications,
            List<String> errors
    ) {}

    @FunctionalInterface
    interface StageBatchFetcher {
        List<WillStateEntity> fetchBatch(Instant cutoff, Instant lastTime, UUID lastId, Pageable pageable);
    }

    /**
     * Reusable keyset drain mechanism across lifecycle stages.
     * Orders deterministically by (timestamp, id) and advances the keyset cursor past the last seen
     * item in each batch. This guarantees deterministic progress even if poison rows encounter errors.
     */
    private int drainStage(
            String stageName,
            Instant cutoff,
            Function<WillStateEntity, Instant> timestampExtractor,
            StageBatchFetcher fetcher,
            Predicate<UUID> claimFunction,
            Consumer<WillStateEntity> postClaimAction,
            List<String> errors) {

        int batchSize = jobProperties.getBatchSize();
        int processedCount = 0;
        Instant lastTime = null;
        UUID lastId = null;

        while (true) {
            Pageable pageable = PageRequest.of(0, batchSize);
            List<WillStateEntity> batch = fetcher.fetchBatch(cutoff, lastTime, lastId, pageable);
            if (batch.isEmpty()) {
                break;
            }

            for (WillStateEntity will : batch) {
                lastTime = timestampExtractor.apply(will);
                lastId = will.getId();

                try {
                    boolean claimed = claimFunction.test(will.getId());
                    if (claimed) {
                        processedCount++;
                        postClaimAction.accept(will);
                    }
                } catch (IllegalStateTransitionException | TransitionGuardFailedException e) {
                    log.debug("Will [{}] already transitioned by concurrent worker in {}: {}",
                            will.getId(), stageName, e.getMessage());
                } catch (Exception e) {
                    String err = "Error processing " + stageName + " for will " + will.getId() + ": " + e.getMessage();
                    log.error(err, e);
                    errors.add(err);
                }
            }
        }
        return processedCount;
    }

    public InactivityJobReport processInactivityJob() {
        Instant now = timeProvider.now();
        List<String> errors = new ArrayList<>();

        log.info("Starting scheduled inactivity processing job at {}", now);

        // 1. Stage 1: ACTIVE -> INACTIVITY_WARNING
        Instant inactivityCutoff = now.minus(jobProperties.getInactivityThreshold());
        int warnedCount = drainStage(
                "inactivity warning",
                inactivityCutoff,
                WillStateEntity::getLastVerifiedActivityAt,
                (cutoff, lastTime, lastId, pageable) -> lastTime == null
                        ? willStateRepository.findEligibleForInactivityWarningFirstBatch(cutoff, pageable)
                        : willStateRepository.findEligibleForInactivityWarningNextBatch(cutoff, lastTime, lastId, pageable),
                id -> willStateService.triggerInactivityWarning(id, jobProperties.getInactivityThreshold()),
                will -> auditLogService.logCritical(
                        will.getId(), "SYSTEM", "SYSTEM_JOB",
                        AuditAction.STATE_TRANSITION, AuditStatus.SUCCESS,
                        AuditResourceType.WILL, will.getId().toString(),
                        "{\"from\":\"ACTIVE\",\"to\":\"INACTIVITY_WARNING\"}"
                ),
                errors
        );

        // 2. Stage 2: INACTIVITY_WARNING -> FINAL_WARNING
        Instant warningCutoff = now.minus(jobProperties.getWarningDelay());
        int finalWarnedCount = drainStage(
                "final warning",
                warningCutoff,
                WillStateEntity::getWarningSentAt,
                (cutoff, lastTime, lastId, pageable) -> lastTime == null
                        ? willStateRepository.findEligibleForFinalWarningFirstBatch(cutoff, pageable)
                        : willStateRepository.findEligibleForFinalWarningNextBatch(cutoff, lastTime, lastId, pageable),
                id -> willStateService.triggerFinalWarning(id, jobProperties.getWarningDelay()),
                will -> auditLogService.logCritical(
                        will.getId(), "SYSTEM", "SYSTEM_JOB",
                        AuditAction.STATE_TRANSITION, AuditStatus.SUCCESS,
                        AuditResourceType.WILL, will.getId().toString(),
                        "{\"from\":\"INACTIVITY_WARNING\",\"to\":\"FINAL_WARNING\"}"
                ),
                errors
        );

        // 3. Stage 3: FINAL_WARNING -> VERIFICATION_PENDING
        Instant finalWarningCutoff = now.minus(jobProperties.getFinalWarningDelay());
        int verificationCount = drainStage(
                "verification initiation",
                finalWarningCutoff,
                WillStateEntity::getFinalWarningSentAt,
                (cutoff, lastTime, lastId, pageable) -> lastTime == null
                        ? willStateRepository.findEligibleForVerificationFirstBatch(cutoff, pageable)
                        : willStateRepository.findEligibleForVerificationNextBatch(cutoff, lastTime, lastId, pageable),
                id -> willStateService.triggerVerificationPending(id, jobProperties.getFinalWarningDelay()),
                will -> {
                    auditLogService.logCritical(
                            will.getId(), "SYSTEM", "SYSTEM_JOB",
                            AuditAction.STATE_TRANSITION, AuditStatus.SUCCESS,
                            AuditResourceType.WILL, will.getId().toString(),
                            "{\"from\":\"FINAL_WARNING\",\"to\":\"VERIFICATION_PENDING\"}"
                    );
                    createVerificationRequestsForContacts(will.getId());
                },
                errors
        );

        // Reconciliation: heal any wills in VERIFICATION_PENDING missing active verification requests
        reconcilePendingVerifications(errors);

        log.info("Completed inactivity processing job: warned={}, finalWarned={}, verifications={}, errors={}",
                warnedCount, finalWarnedCount, verificationCount, errors.size());

        return new InactivityJobReport(now, warnedCount, finalWarnedCount, verificationCount, errors);
    }

    private void createVerificationRequestsForContacts(UUID willId) {
        WillStateEntity will = willStateRepository.findById(willId).orElse(null);
        if (will == null) {
            return;
        }
        Long cycle = will.getVerificationCycle() != null ? will.getVerificationCycle() : 0L;
        List<WillContact> contacts = willContactRepository.findByWillId(willId);
        for (WillContact contact : contacts) {
            if (contact.isActive()) {
                try {
                    // Idempotency: verify no active request exists before issuing a new one
                    if (verificationRequestRepository != null) {
                        boolean alreadyActive = verificationRequestRepository
                                .findByWillIdAndContactIdAndVerificationCycleAndStatus(
                                        willId, contact.getContactId(), cycle, VerificationRequestStatus.ACTIVE)
                                .isPresent();
                        if (alreadyActive) {
                            continue;
                        }
                    }
                    verificationService.createVerificationRequest(willId, contact.getContactId());
                } catch (Exception ex) {
                    log.warn("Failed to create verification request for contact [{}] on will [{}]: {}",
                            contact.getContactId(), willId, ex.getMessage());
                }
            }
        }
    }

    /**
     * Reconciles wills in VERIFICATION_PENDING that lack active verification requests in the current cycle
     * (e.g., in case of a crash or interruption between transition and request creation).
     */
    public int reconcilePendingVerifications(List<String> errors) {
        int batchSize = jobProperties.getBatchSize();
        List<WillStateEntity> orphaned = willStateRepository.findVerificationPendingWillsMissingRequests(
                PageRequest.of(0, batchSize));
        int reconciled = 0;
        for (WillStateEntity will : orphaned) {
            try {
                createVerificationRequestsForContacts(will.getId());
                reconciled++;
                log.info("Reconciled missing verification requests for will [{}] in VERIFICATION_PENDING", will.getId());
            } catch (Exception e) {
                String err = "Failed to reconcile verification requests for will " + will.getId() + ": " + e.getMessage();
                log.error(err, e);
                if (errors != null) {
                    errors.add(err);
                }
            }
        }
        return reconciled;
    }
}
