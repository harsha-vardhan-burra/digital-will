package com.digitalwill.job.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
@ConfigurationProperties(prefix = "digitalwill.jobs")
public class JobProperties {

    /**
     * Shared secret required in X-Internal-Job-Secret header for protected job endpoints.
     */
    private String secret = "digitalwill-internal-secret-token";

    /**
     * Duration of owner inactivity before triggering first warning (default: 90 days).
     */
    private Duration inactivityThreshold = Duration.ofDays(90);

    /**
     * Duration between first warning and final warning (default: 14 days).
     */
    private Duration warningDelay = Duration.ofDays(14);

    /**
     * Duration between final warning and trusted contact verification (default: 7 days).
     */
    private Duration finalWarningDelay = Duration.ofDays(7);

    /**
     * Timeout after which an EXECUTING state without completion is considered crashed (default: 15 mins).
     */
    private Duration executionRecoveryTimeout = Duration.ofMinutes(15);

    /**
     * Bounded batch size for database queries during scheduled jobs.
     */
    private int batchSize = 50;

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public Duration getInactivityThreshold() {
        return inactivityThreshold;
    }

    public void setInactivityThreshold(Duration inactivityThreshold) {
        this.inactivityThreshold = inactivityThreshold;
    }

    public Duration getWarningDelay() {
        return warningDelay;
    }

    public void setWarningDelay(Duration warningDelay) {
        this.warningDelay = warningDelay;
    }

    public Duration getFinalWarningDelay() {
        return finalWarningDelay;
    }

    public void setFinalWarningDelay(Duration finalWarningDelay) {
        this.finalWarningDelay = finalWarningDelay;
    }

    public Duration getExecutionRecoveryTimeout() {
        return executionRecoveryTimeout;
    }

    public void setExecutionRecoveryTimeout(Duration executionRecoveryTimeout) {
        this.executionRecoveryTimeout = executionRecoveryTimeout;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }
}
