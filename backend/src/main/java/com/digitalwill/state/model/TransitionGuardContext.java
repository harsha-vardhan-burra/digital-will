package com.digitalwill.state.model;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Contextual data provided to evaluate transition guards.
 * All temporal checks use the injected time inside this context.
 */
public record TransitionGuardContext(
        Instant now,
        Instant lastVerifiedActivityAt,
        Duration inactivityThreshold,
        Instant warningSentAt,
        Duration warningDelay,
        Instant finalWarningSentAt,
        Duration finalWarningDelay,
        int distinctConfirmations,
        int requiredConfirmations,
        Instant releaseAfter,
        Instant executingAt,
        Duration executionRecoveryTimeout,
        boolean disclosureCompleted,
        Instant cancelledAt
) {
    public TransitionGuardContext {
        Objects.requireNonNull(now, "now must not be null");
    }

    public static Builder builder(Instant now) {
        return new Builder(now);
    }

    public static class Builder {
        private final Instant now;
        private Instant lastVerifiedActivityAt;
        private Duration inactivityThreshold = Duration.ofDays(30);
        private Instant warningSentAt;
        private Duration warningDelay = Duration.ofDays(7);
        private Instant finalWarningSentAt;
        private Duration finalWarningDelay = Duration.ofDays(7);
        private int distinctConfirmations = 0;
        private int requiredConfirmations = 2;
        private Instant releaseAfter;
        private Instant executingAt;
        private Duration executionRecoveryTimeout = Duration.ofMinutes(30);
        private boolean disclosureCompleted = false;
        private Instant cancelledAt;

        public Builder(Instant now) {
            this.now = Objects.requireNonNull(now, "now must not be null");
        }

        public Builder lastVerifiedActivityAt(Instant lastVerifiedActivityAt) {
            this.lastVerifiedActivityAt = lastVerifiedActivityAt;
            return this;
        }

        public Builder inactivityThreshold(Duration inactivityThreshold) {
            this.inactivityThreshold = inactivityThreshold;
            return this;
        }

        public Builder warningSentAt(Instant warningSentAt) {
            this.warningSentAt = warningSentAt;
            return this;
        }

        public Builder warningDelay(Duration warningDelay) {
            this.warningDelay = warningDelay;
            return this;
        }

        public Builder finalWarningSentAt(Instant finalWarningSentAt) {
            this.finalWarningSentAt = finalWarningSentAt;
            return this;
        }

        public Builder finalWarningDelay(Duration finalWarningDelay) {
            this.finalWarningDelay = finalWarningDelay;
            return this;
        }

        public Builder distinctConfirmations(int distinctConfirmations) {
            this.distinctConfirmations = distinctConfirmations;
            return this;
        }

        public Builder requiredConfirmations(int requiredConfirmations) {
            this.requiredConfirmations = requiredConfirmations;
            return this;
        }

        public Builder releaseAfter(Instant releaseAfter) {
            this.releaseAfter = releaseAfter;
            return this;
        }

        public Builder executingAt(Instant executingAt) {
            this.executingAt = executingAt;
            return this;
        }

        public Builder executionRecoveryTimeout(Duration executionRecoveryTimeout) {
            this.executionRecoveryTimeout = executionRecoveryTimeout;
            return this;
        }

        public Builder disclosureCompleted(boolean disclosureCompleted) {
            this.disclosureCompleted = disclosureCompleted;
            return this;
        }

        public Builder cancelledAt(Instant cancelledAt) {
            this.cancelledAt = cancelledAt;
            return this;
        }

        public TransitionGuardContext build() {
            return new TransitionGuardContext(
                    now,
                    lastVerifiedActivityAt,
                    inactivityThreshold,
                    warningSentAt,
                    warningDelay,
                    finalWarningSentAt,
                    finalWarningDelay,
                    distinctConfirmations,
                    requiredConfirmations,
                    releaseAfter,
                    executingAt,
                    executionRecoveryTimeout,
                    disclosureCompleted,
                    cancelledAt
            );
        }
    }
}
