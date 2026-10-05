package com.digitalwill.job.service;

import com.digitalwill.audit.model.AuditAction;
import com.digitalwill.audit.model.AuditResourceType;
import com.digitalwill.audit.model.AuditStatus;
import com.digitalwill.audit.service.AuditLogService;
import com.digitalwill.common.TimeProvider;
import com.digitalwill.job.config.JobProperties;
import com.digitalwill.state.model.WillStateEntity;
import com.digitalwill.state.repository.WillStateRepository;
import com.digitalwill.state.service.WillStateService;
import com.digitalwill.verification.model.WillContact;
import com.digitalwill.verification.repository.WillContactRepository;
import com.digitalwill.verification.service.VerificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Service orchestrating background inactivity processing across succession stages.
 * Conforms to PR2 & Phase-2 reliability rules:
 * - Deterministic TimeProvider time evaluation
 * - Never skips required lifecycle stages (ACTIVE -> WARNING -> FINAL_WARNING -> VERIFICATION_PENDING)
 * - Safe under concurrent or duplicate execution via guarded conditional updates
 * - Isolates failures per Will: failure of one Will never aborts the batch
 * - Uses bounded indexed queries to prevent unbounded memory consumption
 */
@Service
public class InactivityProcessingJobService {

    private static final Logger log = LoggerFactory.getLogger(InactivityProcessingJobService.class);

    private final WillStateRepository willStateRepository;
    private final WillStateService willStateService;
    private final WillContactRepository willContactRepository;
    private final VerificationService verificationService;
    private final AuditLogService auditLogService;
    private final TimeProvider timeProvider;
    private final JobProperties jobProperties;

    public InactivityProcessingJobService(WillStateRepository willStateRepository,
                                         WillStateService willStateService,
                                         WillContactRepository willContactRepository,
                                         VerificationService verificationService,
                                         AuditLogService auditLogService,
                                         TimeProvider timeProvider,
                                         JobProperties jobProperties) {
        this.willStateRepository = Objects.requireNonNull(willStateRepository);
        this.willStateService = Objects.requireNonNull(willStateService);
        this.willContactRepository = Objects.requireNonNull(willContactRepository);
        this.verificationService = Objects.requireNonNull(verificationService);
        this.auditLogService = Objects.requireNonNull(auditLogService);
        this.timeProvider = Objects.requireNonNull(timeProvider);
        this.jobProperties = Objects.requireNonNull(jobProperties);
    }

    public record InactivityJobReport(
            Instant executionTimestamp,
            int warnedActiveWills,
            int finalWarnedWills,
            int initiatedVerifications,
            List<String> errors
    ) {}

    public InactivityJobReport processInactivityJob() {
        Instant now = timeProvider.now();
        int batchSize = jobProperties.getBatchSize();
        List<String> errors = new ArrayList<>();

        log.info("Starting scheduled inactivity processing job at {}", now);

        // 1. Stage 1: ACTIVE -> INACTIVITY_WARNING
        int warnedCount = 0;
        Instant inactivityCutoff = now.minus(jobProperties.getInactivityThreshold());
        List<WillStateEntity> eligibleActive = willStateRepository.findEligibleForInactivityWarning(
                inactivityCutoff, PageRequest.of(0, batchSize)
        );
        for (WillStateEntity will : eligibleActive) {
            try {
                boolean claimed = willStateService.triggerInactivityWarning(will.getId(), jobProperties.getInactivityThreshold());
                if (claimed) {
                    warnedCount++;
                    auditLogService.logCritical(
                            will.getId(), "SYSTEM", "SYSTEM_JOB",
                            AuditAction.STATE_TRANSITION, AuditStatus.SUCCESS,
                            AuditResourceType.WILL, will.getId().toString(),
                            "{\"from\":\"ACTIVE\",\"to\":\"INACTIVITY_WARNING\"}"
                    );
                }
            } catch (com.digitalwill.state.exception.IllegalStateTransitionException |
                     com.digitalwill.state.exception.TransitionGuardFailedException e) {
                log.debug("Will [{}] already transitioned by concurrent worker: {}", will.getId(), e.getMessage());
            } catch (Exception e) {
                String err = "Error processing inactivity warning for will " + will.getId() + ": " + e.getMessage();
                log.error(err, e);
                errors.add(err);
            }
        }

        // 2. Stage 2: INACTIVITY_WARNING -> FINAL_WARNING
        int finalWarnedCount = 0;
        Instant warningCutoff = now.minus(jobProperties.getWarningDelay());
        List<WillStateEntity> eligibleWarning = willStateRepository.findEligibleForFinalWarning(
                warningCutoff, PageRequest.of(0, batchSize)
        );
        for (WillStateEntity will : eligibleWarning) {
            try {
                boolean claimed = willStateService.triggerFinalWarning(will.getId(), jobProperties.getWarningDelay());
                if (claimed) {
                    finalWarnedCount++;
                    auditLogService.logCritical(
                            will.getId(), "SYSTEM", "SYSTEM_JOB",
                            AuditAction.STATE_TRANSITION, AuditStatus.SUCCESS,
                            AuditResourceType.WILL, will.getId().toString(),
                            "{\"from\":\"INACTIVITY_WARNING\",\"to\":\"FINAL_WARNING\"}"
                    );
                }
            } catch (com.digitalwill.state.exception.IllegalStateTransitionException |
                     com.digitalwill.state.exception.TransitionGuardFailedException e) {
                log.debug("Will [{}] already transitioned by concurrent worker: {}", will.getId(), e.getMessage());
            } catch (Exception e) {
                String err = "Error processing final warning for will " + will.getId() + ": " + e.getMessage();
                log.error(err, e);
                errors.add(err);
            }
        }

        // 3. Stage 3: FINAL_WARNING -> VERIFICATION_PENDING
        int verificationCount = 0;
        Instant finalWarningCutoff = now.minus(jobProperties.getFinalWarningDelay());
        List<WillStateEntity> eligibleFinalWarning = willStateRepository.findEligibleForVerification(
                finalWarningCutoff, PageRequest.of(0, batchSize)
        );
        for (WillStateEntity will : eligibleFinalWarning) {
            try {
                boolean claimed = willStateService.triggerVerificationPending(will.getId(), jobProperties.getFinalWarningDelay());
                if (claimed) {
                    verificationCount++;
                    auditLogService.logCritical(
                            will.getId(), "SYSTEM", "SYSTEM_JOB",
                            AuditAction.STATE_TRANSITION, AuditStatus.SUCCESS,
                            AuditResourceType.WILL, will.getId().toString(),
                            "{\"from\":\"FINAL_WARNING\",\"to\":\"VERIFICATION_PENDING\"}"
                    );

                    // Create verification requests for all active trusted contacts
                    List<WillContact> contacts = willContactRepository.findByWillId(will.getId());
                    for (WillContact contact : contacts) {
                        if (contact.isActive()) {
                            try {
                                verificationService.createVerificationRequest(will.getId(), contact.getContactId());
                            } catch (Exception ex) {
                                log.warn("Failed to create verification request for contact [{}] on will [{}]: {}",
                                        contact.getContactId(), will.getId(), ex.getMessage());
                            }
                        }
                    }
                }
            } catch (com.digitalwill.state.exception.IllegalStateTransitionException |
                     com.digitalwill.state.exception.TransitionGuardFailedException e) {
                log.debug("Will [{}] already transitioned by concurrent worker: {}", will.getId(), e.getMessage());
            } catch (Exception e) {
                String err = "Error initiating verification for will " + will.getId() + ": " + e.getMessage();
                log.error(err, e);
                errors.add(err);
            }
        }

        log.info("Completed inactivity processing job: warned={}, finalWarned={}, verifications={}, errors={}",
                warnedCount, finalWarnedCount, verificationCount, errors.size());

        return new InactivityJobReport(now, warnedCount, finalWarnedCount, verificationCount, errors);
    }
}
