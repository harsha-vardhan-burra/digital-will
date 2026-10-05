package com.digitalwill.job.web;

import com.digitalwill.job.config.JobProperties;
import com.digitalwill.job.service.InactivityProcessingJobService;
import com.digitalwill.job.service.ReleaseProcessingJobService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Protected internal job endpoints intended for GitHub Actions scheduled workflows
 * or internal cron schedulers.
 */
@RestController
@RequestMapping("/internal/jobs")
public class InternalJobController {

    private final InactivityProcessingJobService inactivityJobService;
    private final ReleaseProcessingJobService releaseJobService;
    private final JobProperties jobProperties;

    public InternalJobController(InactivityProcessingJobService inactivityJobService,
                                 ReleaseProcessingJobService releaseJobService,
                                 JobProperties jobProperties) {
        this.inactivityJobService = inactivityJobService;
        this.releaseJobService = releaseJobService;
        this.jobProperties = jobProperties;
    }

    private boolean isAuthorized(String providedSecret) {
        if (providedSecret == null || providedSecret.isBlank() || jobProperties.getSecret() == null) {
            return false;
        }
        byte[] a = providedSecret.trim().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] b = jobProperties.getSecret().trim().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        return java.security.MessageDigest.isEqual(a, b);
    }

    @PostMapping("/process-inactivity")
    public ResponseEntity<?> triggerInactivityJob(
            @RequestHeader(value = "X-Internal-Job-Secret", required = false) String secret) {
        if (!isAuthorized(secret)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "unauthorized", "message", "Invalid or missing X-Internal-Job-Secret header"));
        }
        InactivityProcessingJobService.InactivityJobReport report = inactivityJobService.processInactivityJob();
        return ResponseEntity.ok(report);
    }

    @PostMapping("/process-releases")
    public ResponseEntity<?> triggerReleaseJob(
            @RequestHeader(value = "X-Internal-Job-Secret", required = false) String secret) {
        if (!isAuthorized(secret)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "unauthorized", "message", "Invalid or missing X-Internal-Job-Secret header"));
        }
        ReleaseProcessingJobService.ReleaseJobReport report = releaseJobService.processReleasesJob();
        return ResponseEntity.ok(report);
    }

    @PostMapping("/recover-stalled-executions")
    public ResponseEntity<?> triggerRecoveryJob(
            @RequestHeader(value = "X-Internal-Job-Secret", required = false) String secret) {
        if (!isAuthorized(secret)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "unauthorized", "message", "Invalid or missing X-Internal-Job-Secret header"));
        }
        ReleaseProcessingJobService.RecoveryJobReport report = releaseJobService.recoverStaleExecutionsJob();
        return ResponseEntity.ok(report);
    }
}
