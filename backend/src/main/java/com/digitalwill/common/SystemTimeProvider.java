package com.digitalwill.common;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;

/**
 * Production implementation of {@link TimeProvider} backed by UTC system clock.
 */
@Component
public class SystemTimeProvider implements TimeProvider {

    private final Clock clock;

    public SystemTimeProvider() {
        this(Clock.systemUTC());
    }

    public SystemTimeProvider(Clock clock) {
        this.clock = clock;
    }

    @Override
    public Instant now() {
        return Instant.now(clock);
    }
}
