package com.digitalwill.common.web;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Map;

/**
 * Standardized API Error Response Contract.
 * Strict adherence to Section 26:
 * - timestamp
 * - status
 * - code (stable machine-readable code)
 * - message (safe human-readable message)
 * - path (request URI)
 * Does NOT expose stack traces, SQL, keys, tokens, or file paths.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        Instant timestamp,
        int status,
        String code,
        String message,
        String path,
        String error,
        Map<String, String> details
) {
    public ErrorResponse(Instant timestamp, int status, String code, String message, String path) {
        this(timestamp, status, code, message, path, code, null);
    }

    public ErrorResponse(Instant timestamp, int status, String code, String message, String path, Map<String, String> details) {
        this(timestamp, status, code, message, path, code, details);
    }
}
