package com.digitalwill.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Exception thrown when a document fails validation pipeline security or structural checks.
 */
public class DocumentValidationException extends ValidationException {

    public DocumentValidationException(String code, String message) {
        super(code, message, HttpStatus.BAD_REQUEST);
    }

    public DocumentValidationException(String code, String message, HttpStatus status) {
        super(code, message, status);
    }
}
