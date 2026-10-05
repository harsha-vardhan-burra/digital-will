package com.digitalwill.crypto.service;

import com.digitalwill.crypto.config.CryptoProperties;
import com.digitalwill.crypto.exception.CryptoException;
import com.digitalwill.crypto.exception.DecryptionFailedException;
import com.digitalwill.crypto.exception.InvalidMasterKeyException;
import com.digitalwill.crypto.exception.UnsupportedCryptoVersionException;
import com.digitalwill.crypto.model.EncryptedData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Objects;

/**
 * Production-ready envelope encryption service using AES-256-GCM and AESWrap.
 * Conforms to PR3 specifications:
 * - AES-256-GCM (128-bit authentication tag, 96-bit random IV per operation)
 * - Cryptographically secure random DEK generation (256-bit AES)
 * - Master key wrapping using standard RFC 3394 AESWrap
 * - Fail-closed security with explicit typed exceptions
 */
@Service
public class AesGcmEnvelopeEncryptionService implements EncryptionService {

    private static final Logger log = LoggerFactory.getLogger(AesGcmEnvelopeEncryptionService.class);

    private static final String ENCRYPTION_ALGORITHM = "AES/GCM/NoPadding";
    private static final String KEY_WRAP_ALGORITHM = "AESWrap";
    private static final int GCM_IV_LENGTH_BYTES = 12; // 96-bit nonce
    private static final int GCM_TAG_LENGTH_BITS = 128;
    private static final int DEK_KEY_SIZE_BITS = 256;
    private static final int CURRENT_CRYPTO_VERSION = 1;

    private final SecretKey masterKey;
    private final SecureRandom secureRandom;

    public AesGcmEnvelopeEncryptionService(CryptoProperties cryptoProperties) {
        Objects.requireNonNull(cryptoProperties, "cryptoProperties must not be null");
        this.secureRandom = new SecureRandom();
        this.masterKey = parseMasterKey(cryptoProperties.getMasterKey());
    }

    private SecretKey parseMasterKey(String masterKeyBase64) {
        if (masterKeyBase64 == null || masterKeyBase64.isBlank()) {
            throw new InvalidMasterKeyException("Master key must not be blank");
        }
        try {
            byte[] keyBytes = Base64.getDecoder().decode(masterKeyBase64.trim());
            if (keyBytes.length != 32) { // 256 bits
                throw new InvalidMasterKeyException("Master key must be exactly 256 bits (32 bytes), received " + keyBytes.length + " bytes");
            }
            return new SecretKeySpec(keyBytes, "AES");
        } catch (IllegalArgumentException e) {
            throw new InvalidMasterKeyException("Master key is not valid Base64: " + e.getMessage(), e);
        }
    }

    @Override
    public EncryptedData encrypt(byte[] plaintext) {
        if (plaintext == null) {
            throw new IllegalArgumentException("Plaintext must not be null");
        }
        try {
            // 1. Generate unique 256-bit random DEK
            KeyGenerator keyGen = KeyGenerator.getInstance("AES");
            keyGen.init(DEK_KEY_SIZE_BITS, secureRandom);
            SecretKey dek = keyGen.generateKey();

            // 2. Generate unique 96-bit IV
            byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
            secureRandom.nextBytes(iv);

            // 3. Encrypt document with AES-256-GCM
            Cipher gcmCipher = Cipher.getInstance(ENCRYPTION_ALGORITHM);
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv);
            gcmCipher.init(Cipher.ENCRYPT_MODE, dek, gcmSpec);
            byte[] ciphertext = gcmCipher.doFinal(plaintext);

            // 4. Wrap DEK with master key using AESWrap
            Cipher wrapCipher = Cipher.getInstance(KEY_WRAP_ALGORITHM);
            wrapCipher.init(Cipher.WRAP_MODE, masterKey);
            byte[] wrappedDekBytes = wrapCipher.wrap(dek);

            String encryptedDek = Base64.getEncoder().encodeToString(wrappedDekBytes);
            String ivBase64 = Base64.getEncoder().encodeToString(iv);

            return new EncryptedData(
                    ciphertext,
                    encryptedDek,
                    ivBase64,
                    ENCRYPTION_ALGORITHM,
                    KEY_WRAP_ALGORITHM,
                    CURRENT_CRYPTO_VERSION
            );
        } catch (GeneralSecurityException e) {
            log.error("Envelope encryption operation failed: {}", e.getClass().getSimpleName());
            throw new CryptoException("Failed to perform envelope encryption: " + e.getMessage(), e);
        }
    }

    @Override
    public byte[] decrypt(byte[] ciphertext, String encryptedDek, String iv, int version) {
        if (ciphertext == null || ciphertext.length == 0) {
            throw new DecryptionFailedException("Ciphertext must not be null or empty");
        }
        if (encryptedDek == null || encryptedDek.isBlank()) {
            throw new DecryptionFailedException("Encrypted DEK must not be null or blank");
        }
        if (iv == null || iv.isBlank()) {
            throw new DecryptionFailedException("IV must not be null or blank");
        }
        if (version != CURRENT_CRYPTO_VERSION) {
            throw new UnsupportedCryptoVersionException("Unsupported crypto version: " + version + " (expected: " + CURRENT_CRYPTO_VERSION + ")");
        }

        try {
            // 1. Unwrap DEK using Master Key
            byte[] wrappedDekBytes = Base64.getDecoder().decode(encryptedDek.trim());
            Cipher unwrapCipher = Cipher.getInstance(KEY_WRAP_ALGORITHM);
            unwrapCipher.init(Cipher.UNWRAP_MODE, masterKey);
            SecretKey dek = (SecretKey) unwrapCipher.unwrap(wrappedDekBytes, "AES", Cipher.SECRET_KEY);

            if (dek == null) {
                throw new DecryptionFailedException("Failed to unwrap DEK: unwrapped key is null");
            }

            // 2. Decode IV
            byte[] ivBytes = Base64.getDecoder().decode(iv.trim());
            if (ivBytes.length != GCM_IV_LENGTH_BYTES) {
                throw new DecryptionFailedException("Invalid IV length: expected " + GCM_IV_LENGTH_BYTES + " bytes, got " + ivBytes.length);
            }

            // 3. Decrypt ciphertext with AES-256-GCM
            Cipher gcmCipher = Cipher.getInstance(ENCRYPTION_ALGORITHM);
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, ivBytes);
            gcmCipher.init(Cipher.DECRYPT_MODE, dek, gcmSpec);
            return gcmCipher.doFinal(ciphertext);

        } catch (GeneralSecurityException | IllegalArgumentException e) {
            log.warn("Decryption authentication or key unwrap failure: {}", e.getClass().getSimpleName());
            throw new DecryptionFailedException("Decryption failed due to authentication tag mismatch or corrupted data", e);
        }
    }
}
