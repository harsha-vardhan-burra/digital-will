package com.digitalwill.common.web;

import com.digitalwill.audit.exception.AuditPersistenceException;
import com.digitalwill.common.TimeProvider;
import com.digitalwill.crypto.exception.CryptoException;
import com.digitalwill.crypto.exception.DecryptionFailedException;
import com.digitalwill.document.exception.DocumentNotFoundException;
import com.digitalwill.release.exception.DisclosureNotReadyException;
import com.digitalwill.release.exception.DisclosureTokenExpiredException;
import com.digitalwill.release.exception.DisclosureTokenInvalidException;
import com.digitalwill.release.exception.DisclosureTokenRevokedException;
import com.digitalwill.state.exception.IllegalStateTransitionException;
import com.digitalwill.state.exception.TransitionGuardFailedException;
import com.digitalwill.verification.exception.AlreadyConfirmedException;
import com.digitalwill.verification.exception.InvalidVerificationStateException;
import com.digitalwill.verification.exception.InvalidVerificationTokenException;
import com.digitalwill.verification.exception.VerificationRequestRevokedException;
import com.digitalwill.verification.exception.VerificationTokenAlreadyUsedException;
import com.digitalwill.verification.exception.VerificationTokenExpiredException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final TimeProvider timeProvider;

    public GlobalExceptionHandler(TimeProvider timeProvider) {
        this.timeProvider = Objects.requireNonNull(timeProvider);
    }

    // --- State Engine Exceptions (409 Conflict) ---

    @ExceptionHandler(IllegalStateTransitionException.class)
    public ResponseEntity<ErrorResponse> handleIllegalStateTransition(IllegalStateTransitionException ex, HttpServletRequest request) {
        log.warn("Illegal state transition attempted at [{}]: {}", request.getRequestURI(), ex.getMessage());
        ErrorResponse body = new ErrorResponse(
                timeProvider.now(),
                HttpStatus.CONFLICT.value(),
                "INVALID_STATE_TRANSITION",
                ex.getMessage(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(TransitionGuardFailedException.class)
    public ResponseEntity<ErrorResponse> handleTransitionGuardFailed(TransitionGuardFailedException ex, HttpServletRequest request) {
        log.warn("Transition guard failed at [{}]: {}", request.getRequestURI(), ex.getMessage());
        ErrorResponse body = new ErrorResponse(
                timeProvider.now(),
                HttpStatus.CONFLICT.value(),
                "TRANSITION_GUARD_FAILED",
                ex.getMessage(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    // --- Trusted Contact Verification Exceptions ---

    @ExceptionHandler(InvalidVerificationTokenException.class)
    public ResponseEntity<ErrorResponse> handleInvalidVerificationToken(InvalidVerificationTokenException ex, HttpServletRequest request) {
        log.warn("Invalid verification token presented at [{}]", request.getRequestURI());
        ErrorResponse body = new ErrorResponse(
                timeProvider.now(),
                HttpStatus.BAD_REQUEST.value(),
                "INVALID_VERIFICATION_TOKEN",
                "Invalid verification token",
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(VerificationTokenExpiredException.class)
    public ResponseEntity<ErrorResponse> handleVerificationTokenExpired(VerificationTokenExpiredException ex, HttpServletRequest request) {
        log.warn("Expired verification token presented at [{}]", request.getRequestURI());
        ErrorResponse body = new ErrorResponse(
                timeProvider.now(),
                HttpStatus.BAD_REQUEST.value(),
                "VERIFICATION_TOKEN_EXPIRED",
                "Verification token has expired",
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(VerificationTokenAlreadyUsedException.class)
    public ResponseEntity<ErrorResponse> handleVerificationTokenAlreadyUsed(VerificationTokenAlreadyUsedException ex, HttpServletRequest request) {
        log.warn("Already used verification token presented at [{}]", request.getRequestURI());
        ErrorResponse body = new ErrorResponse(
                timeProvider.now(),
                HttpStatus.CONFLICT.value(),
                "VERIFICATION_TOKEN_ALREADY_USED",
                "Verification token has already been used",
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(VerificationRequestRevokedException.class)
    public ResponseEntity<ErrorResponse> handleVerificationRequestRevoked(VerificationRequestRevokedException ex, HttpServletRequest request) {
        log.warn("Revoked verification token presented at [{}]", request.getRequestURI());
        ErrorResponse body = new ErrorResponse(
                timeProvider.now(),
                HttpStatus.GONE.value(),
                "VERIFICATION_TOKEN_REVOKED",
                "Verification request has been revoked",
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.GONE).body(body);
    }

    @ExceptionHandler(AlreadyConfirmedException.class)
    public ResponseEntity<ErrorResponse> handleAlreadyConfirmed(AlreadyConfirmedException ex, HttpServletRequest request) {
        log.warn("Contact already confirmed at [{}]", request.getRequestURI());
        ErrorResponse body = new ErrorResponse(
                timeProvider.now(),
                HttpStatus.CONFLICT.value(),
                "ALREADY_CONFIRMED",
                "Contact has already confirmed for this verification cycle",
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(InvalidVerificationStateException.class)
    public ResponseEntity<ErrorResponse> handleInvalidVerificationState(InvalidVerificationStateException ex, HttpServletRequest request) {
        log.warn("Verification requested in invalid state at [{}]", request.getRequestURI());
        ErrorResponse body = new ErrorResponse(
                timeProvider.now(),
                HttpStatus.CONFLICT.value(),
                "INVALID_VERIFICATION_STATE",
                "Will is not in a verifiable state",
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    // --- Controlled Disclosure Exceptions ---

    @ExceptionHandler(DisclosureTokenInvalidException.class)
    public ResponseEntity<ErrorResponse> handleDisclosureTokenInvalid(DisclosureTokenInvalidException ex, HttpServletRequest request) {
        log.warn("Invalid disclosure token presented at [{}]", request.getRequestURI());
        ErrorResponse body = new ErrorResponse(
                timeProvider.now(),
                HttpStatus.BAD_REQUEST.value(),
                "INVALID_DISCLOSURE_TOKEN",
                "Invalid disclosure token",
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(DisclosureTokenExpiredException.class)
    public ResponseEntity<ErrorResponse> handleDisclosureTokenExpired(DisclosureTokenExpiredException ex, HttpServletRequest request) {
        log.warn("Expired disclosure token presented at [{}]", request.getRequestURI());
        ErrorResponse body = new ErrorResponse(
                timeProvider.now(),
                HttpStatus.GONE.value(),
                "DISCLOSURE_TOKEN_EXPIRED",
                "Disclosure token has expired",
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.GONE).body(body);
    }

    @ExceptionHandler(com.digitalwill.release.exception.DisclosureTokenConsumedException.class)
    public ResponseEntity<ErrorResponse> handleDisclosureTokenConsumed(com.digitalwill.release.exception.DisclosureTokenConsumedException ex, HttpServletRequest request) {
        log.warn("Already consumed disclosure token presented at [{}]", request.getRequestURI());
        ErrorResponse body = new ErrorResponse(
                timeProvider.now(),
                HttpStatus.CONFLICT.value(),
                "DISCLOSURE_TOKEN_ALREADY_CONSUMED",
                "Disclosure token has already been consumed",
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(DisclosureTokenRevokedException.class)
    public ResponseEntity<ErrorResponse> handleDisclosureTokenRevoked(DisclosureTokenRevokedException ex, HttpServletRequest request) {
        log.warn("Revoked disclosure token presented at [{}]", request.getRequestURI());
        ErrorResponse body = new ErrorResponse(
                timeProvider.now(),
                HttpStatus.GONE.value(),
                "DISCLOSURE_TOKEN_REVOKED",
                "Disclosure token has been revoked",
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.GONE).body(body);
    }

    @ExceptionHandler(DisclosureNotReadyException.class)
    public ResponseEntity<ErrorResponse> handleDisclosureNotReady(DisclosureNotReadyException ex, HttpServletRequest request) {
        log.warn("Disclosure accessed before ready at [{}]: {}", request.getRequestURI(), ex.getMessage());
        ErrorResponse body = new ErrorResponse(
                timeProvider.now(),
                HttpStatus.CONFLICT.value(),
                "DISCLOSURE_NOT_READY",
                ex.getMessage(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    // --- Document & Crypto Exceptions ---

    @ExceptionHandler(DocumentNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleDocumentNotFound(DocumentNotFoundException ex, HttpServletRequest request) {
        log.warn("Document not found at [{}]: {}", request.getRequestURI(), ex.getMessage());
        ErrorResponse body = new ErrorResponse(
                timeProvider.now(),
                HttpStatus.NOT_FOUND.value(),
                "DOCUMENT_NOT_FOUND",
                ex.getMessage(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    @ExceptionHandler(DecryptionFailedException.class)
    public ResponseEntity<ErrorResponse> handleDecryptionFailed(DecryptionFailedException ex, HttpServletRequest request) {
        log.error("Decryption failed at [{}]: {}", request.getRequestURI(), ex.getMessage());
        ErrorResponse body = new ErrorResponse(
                timeProvider.now(),
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "DECRYPTION_FAILED",
                "Failed to decrypt document payload safely",
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }

    @ExceptionHandler(CryptoException.class)
    public ResponseEntity<ErrorResponse> handleCryptoException(CryptoException ex, HttpServletRequest request) {
        log.error("Cryptographic error at [{}]: {}", request.getRequestURI(), ex.getMessage());
        ErrorResponse body = new ErrorResponse(
                timeProvider.now(),
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "CRYPTO_ERROR",
                "A cryptographic operation error occurred",
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }

    @ExceptionHandler(AuditPersistenceException.class)
    public ResponseEntity<ErrorResponse> handleAuditPersistence(AuditPersistenceException ex, HttpServletRequest request) {
        log.error("Audit log persistence failure at [{}]: {}", request.getRequestURI(), ex.getMessage());
        ErrorResponse body = new ErrorResponse(
                timeProvider.now(),
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "AUDIT_FAILURE",
                "Critical audit log operation failed; transaction aborted",
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }

    // --- Client / Validation / Auth Exceptions ---

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest request) {
        log.warn("Bad request argument at [{}]: {}", request.getRequestURI(), ex.getMessage());
        ErrorResponse body = new ErrorResponse(
                timeProvider.now(),
                HttpStatus.BAD_REQUEST.value(),
                "INVALID_ARGUMENT",
                ex.getMessage(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleIllegalState(IllegalStateException ex, HttpServletRequest request) {
        log.warn("Illegal state condition at [{}]: {}", request.getRequestURI(), ex.getMessage());
        ErrorResponse body = new ErrorResponse(
                timeProvider.now(),
                HttpStatus.CONFLICT.value(),
                "INVALID_STATE",
                ex.getMessage(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<ErrorResponse> handleSecurityException(SecurityException ex, HttpServletRequest request) {
        log.warn("Access denied at [{}]: {}", request.getRequestURI(), ex.getMessage());
        ErrorResponse body = new ErrorResponse(
                timeProvider.now(),
                HttpStatus.FORBIDDEN.value(),
                "FORBIDDEN",
                "Access is denied",
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationErrors(MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> errors = new HashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            errors.put(fieldError.getField(), fieldError.getDefaultMessage());
        }
        log.warn("Validation failed at [{}]: {}", request.getRequestURI(), errors);
        ErrorResponse body = new ErrorResponse(
                timeProvider.now(),
                HttpStatus.BAD_REQUEST.value(),
                "VALIDATION_FAILED",
                "Request validation failed",
                request.getRequestURI(),
                errors
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    // --- Fallback Handler (500 Internal Server Error) ---

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneralException(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception at [{}]", request.getRequestURI(), ex);
        ErrorResponse body = new ErrorResponse(
                timeProvider.now(),
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "INTERNAL_SERVER_ERROR",
                "An unexpected internal error occurred",
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}
