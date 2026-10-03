package com.digitalwill.common;

import java.time.Instant;

/**
 * Clock abstraction for all business logic in Digital Will.
 * Ensures deterministic, testable time without scattering Instant.now() across domain logic.
 */
public interface TimeProvider {

    /**
     * Returns the current instant in UTC.
     *
     * @return current UTC instant
     */
    Instant now();
}
