package com.digitalwill.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Base domain exception representing a client request or domain rule validation failure,
 * carrying a stable error code and HTTP status code.
 */
public class ValidationException extends RuntimeException {

    private final String code;
    private final HttpStatus status;

    public ValidationException(String code, String message) {
        this(code, message, HttpStatus.BAD_REQUEST);
    }

    public ValidationException(String code, String message, HttpStatus status) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public String getCode() {
        return code;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
