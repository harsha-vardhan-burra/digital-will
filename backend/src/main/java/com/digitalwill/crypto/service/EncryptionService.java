package com.digitalwill.crypto.service;

import com.digitalwill.crypto.model.EncryptedData;

/**
 * Service abstraction for cryptographic operations.
 * Modules perform encryption/decryption through this boundary without direct exposure
 * to underlying AES-GCM or key wrapping primitives.
 */
public interface EncryptionService {

    /**
     * Performs envelope encryption on the provided plaintext.
     * Generates a random 256-bit DEK, encrypts plaintext using AES-256-GCM with a unique 96-bit IV,
     * wraps the DEK using the configured master key, and returns the encrypted data payload.
     *
     * @param plaintext data to encrypt
     * @return EncryptedData containing ciphertext, wrapped DEK, IV, and metadata
     */
    EncryptedData encrypt(byte[] plaintext);

    /**
     * Decrypts ciphertext using envelope decryption.
     * Unwraps the DEK using the master key, authenticates the ciphertext with GCM tag verification,
     * and returns the original plaintext.
     *
     * @param ciphertext encrypted data (including auth tag)
     * @param encryptedDek Base64-encoded wrapped data encryption key
     * @param iv Base64-encoded initialization vector
     * @param version crypto metadata version
     * @return original plaintext bytes
     */
    byte[] decrypt(byte[] ciphertext, String encryptedDek, String iv, int version);
}
