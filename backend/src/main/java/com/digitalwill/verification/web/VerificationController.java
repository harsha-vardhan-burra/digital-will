package com.digitalwill.verification.web;

import com.digitalwill.verification.exception.AlreadyConfirmedException;
import com.digitalwill.verification.exception.InvalidVerificationStateException;
import com.digitalwill.verification.exception.InvalidVerificationTokenException;
import com.digitalwill.verification.exception.VerificationRequestRevokedException;
import com.digitalwill.verification.exception.VerificationTokenAlreadyUsedException;
import com.digitalwill.verification.exception.VerificationTokenExpiredException;
import com.digitalwill.verification.service.VerificationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/verification")
public class VerificationController {

    private final VerificationService verificationService;

    public VerificationController(VerificationService verificationService) {
        this.verificationService = verificationService;
    }

    public record ConfirmRequest(String token) {}
    public record ConfirmResponse(boolean confirmed, boolean quorumReached, boolean alreadyConfirmed, String state) {}
    public record ErrorResponse(String error, String message) {}

    @PostMapping("/confirm")
    public ResponseEntity<ConfirmResponse> confirm(@RequestBody ConfirmRequest request) {
        if (request == null || request.token() == null || request.token().isBlank()) {
            throw new InvalidVerificationTokenException("Token must not be blank");
        }
        VerificationService.ConfirmationResult result = verificationService.confirmIdempotent(request.token().trim());
        return ResponseEntity.ok(new ConfirmResponse(
                result.confirmed(), result.quorumReached(), result.alreadyConfirmed(), result.resultingState().name()));
    }

    // Security-safe generic handlers - do not reveal enumeration details
    @ExceptionHandler(InvalidVerificationTokenException.class)
    public ResponseEntity<ErrorResponse> handleInvalid(InvalidVerificationTokenException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("invalid_token", "Invalid verification token"));
    }

    @ExceptionHandler(VerificationTokenExpiredException.class)
    public ResponseEntity<ErrorResponse> handleExpired(VerificationTokenExpiredException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("token_expired", "Verification token has expired"));
    }

    @ExceptionHandler(VerificationTokenAlreadyUsedException.class)
    public ResponseEntity<ErrorResponse> handleUsed(VerificationTokenAlreadyUsedException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("token_already_used", "Verification token already used"));
    }

    @ExceptionHandler(VerificationRequestRevokedException.class)
    public ResponseEntity<ErrorResponse> handleRevoked(VerificationRequestRevokedException e) {
        return ResponseEntity.status(HttpStatus.GONE)
                .body(new ErrorResponse("token_revoked", "Verification request has been revoked"));
    }

    @ExceptionHandler(AlreadyConfirmedException.class)
    public ResponseEntity<ErrorResponse> handleAlreadyConfirmed(AlreadyConfirmedException e) {
        // Idempotent already-confirmed still returns success-like but we also support 200 via confirmIdempotent
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("already_confirmed", "Contact has already confirmed"));
    }

    @ExceptionHandler(InvalidVerificationStateException.class)
    public ResponseEntity<ErrorResponse> handleInvalidState(InvalidVerificationStateException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("invalid_state", "Will not in verifiable state"));
    }
}