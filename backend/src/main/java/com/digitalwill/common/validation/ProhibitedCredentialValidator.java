package com.digitalwill.common.validation;

import java.util.regex.Pattern;

/**
 * Defensive validator that detects and blocks accidental or intentional submission
 * of sensitive credentials, banking secrets, cryptographic private keys, seed phrases,
 * and session tokens into Digital Will record fields.
 */
public final class ProhibitedCredentialValidator {

    private ProhibitedCredentialValidator() {}

    // 1. Private keys (PEM headers, PGP private key blocks, etc.)
    private static final Pattern PEM_PRIVATE_KEY = Pattern.compile(
            "-----BEGIN\\s+(?:RSA|DSA|EC|OPENSSH|ENCRYPTED|PGP)?\\s*PRIVATE\\s+(?:KEY|KEY\\s+BLOCK)-----",
            Pattern.CASE_INSENSITIVE
    );

    // 2. BIP-39 mnemonic / seed phrase indicators
    private static final Pattern SEED_PHRASE_PATTERN = Pattern.compile(
            "(?i)\\b(?:seed\\s*phrase|recovery\\s*phrase|mnemonic\\s*phrase|12[ -]word\\s*seed|24[ -]word\\s*seed|secret\\s*recovery\\s*phrase)\\b"
    );

    // 3. Cleartext password/PIN/CVV indicators
    private static final Pattern PASSWORD_PATTERN = Pattern.compile(
            "(?i)\\b(?:password|passwd|pwd)\\s*[:=]\\s*\\S+"
    );

    private static final Pattern PIN_CVV_PATTERN = Pattern.compile(
            "(?i)\\b(?:atm\\s*pin|bank\\s*pin|pin\\s*code|card\\s*pin)\\s*[:=]\\s*\\d{3,8}\\b|\\b(?:cvv|cvc|security\\s*code)\\s*[:=]\\s*\\d{3,4}\\b"
    );

    // 4. API keys, secrets, session cookies, bearer tokens, AWS/GitHub/Slack tokens, standalone JWTs
    private static final Pattern API_SECRET_PATTERN = Pattern.compile(
            "(?i)\\b(?:api[_-]?secret|client[_-]?secret|app[_-]?secret)\\s*[:=]\\s*\\S+|\\b(?:AKIA|ABIA|ACCA|ASIA)[0-9A-Z]{16}\\b|\\bgh[pousr]_[A-Za-z0-9_]{36,255}\\b|\\bxox[baprs]-[0-9a-zA-Z-]{10,}\\b"
    );

    private static final Pattern BEARER_OR_COOKIE_PATTERN = Pattern.compile(
            "(?i)\\b(?:bearer\\s+ey[A-Za-z0-9_-]{20,}|session_id\\s*[:=]|cookie\\s*[:=]\\s*\\S+)|\\beyJ[A-Za-z0-9-_=]+\\.eyJ[A-Za-z0-9-_=]+\\.[A-Za-z0-9-_.+/=]+\\b"
    );

    // 5. MFA / OTP secret codes and setup URIs
    private static final Pattern MFA_SECRET_PATTERN = Pattern.compile(
            "(?i)\\b(?:totp\\s*secret|mfa\\s*code\\s*[:=]|2fa\\s*secret\\s*[:=]|otp\\s*code\\s*[:=]\\s*\\d{6})|\\botpauth://\\S+"
    );

    /**
     * Inspects a string for prohibited credential patterns.
     * Throws an IllegalArgumentException with a clear, safe message if prohibited credentials are detected.
     */
    public static void validateNoProhibitedCredentials(String fieldName, String value) {
        if (value == null || value.isBlank()) {
            return;
        }

        if (PEM_PRIVATE_KEY.matcher(value).find()) {
            throw new IllegalArgumentException(
                    "Field [" + fieldName + "] contains a cryptographic private key. Do not store private keys in Digital Will records."
            );
        }

        if (SEED_PHRASE_PATTERN.matcher(value).find()) {
            throw new IllegalArgumentException(
                    "Field [" + fieldName + "] contains cryptocurrency recovery seed phrase patterns. Do not store recovery/seed phrases in Digital Will records."
            );
        }

        if (PASSWORD_PATTERN.matcher(value).find()) {
            throw new IllegalArgumentException(
                    "Field [" + fieldName + "] appears to contain a password or login secret. Do not store plaintext passwords in Digital Will records."
            );
        }

        if (PIN_CVV_PATTERN.matcher(value).find()) {
            throw new IllegalArgumentException(
                    "Field [" + fieldName + "] contains a bank PIN or card CVV. Do not store payment or authentication PINs in Digital Will records."
            );
        }

        if (API_SECRET_PATTERN.matcher(value).find() || BEARER_OR_COOKIE_PATTERN.matcher(value).find()) {
            throw new IllegalArgumentException(
                    "Field [" + fieldName + "] contains API secrets, session cookies, or authorization tokens. Do not store active session secrets in Digital Will records."
            );
        }

        if (MFA_SECRET_PATTERN.matcher(value).find()) {
            throw new IllegalArgumentException(
                    "Field [" + fieldName + "] contains MFA / 2FA secrets or codes. Do not store multi-factor authentication secrets in Digital Will records."
            );
        }
    }
}
