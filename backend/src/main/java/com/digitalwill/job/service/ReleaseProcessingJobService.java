package com.digitalwill.job.service;

import com.digitalwill.common.TimeProvider;
import com.digitalwill.job.config.JobProperties;
import com.digitalwill.release.model.ExecutionStatus;
import com.digitalwill.release.service.ReleaseExecutionService;
import com.digitalwill.state.model.WillStateEntity;
import com.digitalwill.state.repository.WillStateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
public class ReleaseProcessingJobService {

    private static final Logger log = LoggerFactory.getLogger(ReleaseProcessingJobService.class);

    private final WillStateRepository willStateRepository;
    private final ReleaseExecutionService releaseExecutionService;
    private final TimeProvider timeProvider;
    private final JobProperties jobProperties;

    public ReleaseProcessingJobService(WillStateRepository willStateRepository,
                                     ReleaseExecutionService releaseExecutionService,
                                     TimeProvider timeProvider,
                                     JobProperties jobProperties) {
        this.willStateRepository = Objects.requireNonNull(willStateRepository);
        this.releaseExecutionService = Objects.requireNonNull(releaseExecutionService);
        this.timeProvider = Objects.requireNonNull(timeProvider);
        this.jobProperties = Objects.requireNonNull(jobProperties);
    }

    public record ReleaseJobReport(
            Instant timestamp,
            int eligibleCount,
            int completedCount,
            int partialCount,
            List<String> errors
    ) {}

    public record RecoveryJobReport(
            Instant timestamp,
            int eligibleCount,
            int recoveredCount,
            List<String> errors
    ) {}

    public ReleaseJobReport processReleasesJob() {
        Instant now = timeProvider.now();
        int batchSize = jobProperties.getBatchSize();
        List<String> errors = new ArrayList<>();

        List<WillStateEntity> eligibleWills = willStateRepository.findEligibleForExecution(
                now, PageRequest.of(0, batchSize)
        );

        int completed = 0;
        int partial = 0;

        for (WillStateEntity will : eligibleWills) {
            try {
                ReleaseExecutionService.ExecutionResult result = releaseExecutionService.claimAndExecuteRelease(will.getId());
                if (result.executed() && result.status() == ExecutionStatus.COMPLETED) {
                    completed++;
                } else if (result.status() == ExecutionStatus.PARTIALLY_COMPLETED) {
                    partial++;
                }
            } catch (Exception e) {
                String err = "Error executing release for will " + will.getId() + ": " + e.getMessage();
                log.error(err, e);
                errors.add(err);
            }
        }

        return new ReleaseJobReport(now, eligibleWills.size(), completed, partial, errors);
    }

    public RecoveryJobReport recoverStaleExecutionsJob() {
        Instant now = timeProvider.now();
        int batchSize = jobProperties.getBatchSize();
        List<String> errors = new ArrayList<>();

        Instant cutoff = now.minus(jobProperties.getExecutionRecoveryTimeout());
        List<WillStateEntity> stalledWills = willStateRepository.findStalledExecutions(
                cutoff, PageRequest.of(0, batchSize)
        );

        int recovered = 0;

        for (WillStateEntity will : stalledWills) {
            try {
                ReleaseExecutionService.ExecutionResult result =
                        releaseExecutionService.recoverStaleExecution(will.getId(), jobProperties.getExecutionRecoveryTimeout());
                if (result.executed()) {
                    recovered++;
                }
            } catch (Exception e) {
                String err = "Error recovering stalled execution for will " + will.getId() + ": " + e.getMessage();
                log.error(err, e);
                errors.add(err);
            }
        }

        return new RecoveryJobReport(now, stalledWills.size(), recovered, errors);
    }
}
