package com.digitalwill.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Exception thrown when asset data fails structural, category, numerical, or integrity checks.
 */
public class AssetValidationException extends ValidationException {

    public AssetValidationException(String code, String message) {
        super(code, message, HttpStatus.BAD_REQUEST);
    }

    public AssetValidationException(String code, String message, HttpStatus status) {
        super(code, message, status);
    }
}
