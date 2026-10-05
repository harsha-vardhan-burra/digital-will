package com.digitalwill.verification.web;

import com.digitalwill.verification.exception.InvalidVerificationTokenException;
import com.digitalwill.verification.service.VerificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}