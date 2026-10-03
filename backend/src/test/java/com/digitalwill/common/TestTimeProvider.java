package com.digitalwill.common;

import java.time.Duration;
import java.time.Instant;

/**
 * Controllable time provider for testing state transitions and temporal invariants.
 */
public class TestTimeProvider implements TimeProvider {

    private Instant currentInstant;

    public TestTimeProvider(Instant initialInstant) {
        this.currentInstant = initialInstant;
    }

    public TestTimeProvider() {
        this(Instant.parse("2026-10-01T00:00:00Z"));
    }

    @Override
    public synchronized Instant now() {
        return currentInstant;
    }

    public synchronized void setNow(Instant newInstant) {
        this.currentInstant = newInstant;
    }

    public synchronized void advance(Duration duration) {
        this.currentInstant = this.currentInstant.plus(duration);
    }

    public synchronized void advanceMinutes(long minutes) {
        advance(Duration.ofMinutes(minutes));
    }

    public synchronized void advanceHours(long hours) {
        advance(Duration.ofHours(hours));
    }

    public synchronized void advanceDays(long days) {
        advance(Duration.ofDays(days));
    }
}
