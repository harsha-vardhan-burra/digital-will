package com.digitalwill.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Exception thrown when a candidate record is flagged as a duplicate of an existing record.
 */
public class DuplicateResourceException extends ValidationException {

    public DuplicateResourceException(String message) {
        super("POSSIBLE_DUPLICATE", message, HttpStatus.CONFLICT);
    }
}
