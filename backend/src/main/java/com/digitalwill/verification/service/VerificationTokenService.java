package com.digitalwill.verification.service;

import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

@Service
public class VerificationTokenService {

    private static final int TOKEN_BYTES = 32; // 256-bit
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Generates a cryptographically secure opaque token suitable for emailed verification link.
     * Uses SecureRandom, 32 bytes -> base64url without padding (~43 chars).
     */
    public String generateRawToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * Stores only SHA-256 hash of raw token.
     */
    public String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    public boolean matches(String rawToken, String storedHash) {
        String computed = hashToken(rawToken);
        // constant-time comparison to avoid timing side-channel
        return MessageDigest.isEqual(
                computed.getBytes(StandardCharsets.UTF_8),
                storedHash.getBytes(StandardCharsets.UTF_8));
    }
}