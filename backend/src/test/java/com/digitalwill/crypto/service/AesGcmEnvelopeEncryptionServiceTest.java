package com.digitalwill.crypto.service;

import com.digitalwill.crypto.config.CryptoProperties;
import com.digitalwill.crypto.exception.DecryptionFailedException;
import com.digitalwill.crypto.exception.InvalidMasterKeyException;
import com.digitalwill.crypto.exception.UnsupportedCryptoVersionException;
import com.digitalwill.crypto.model.EncryptedData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AesGcmEnvelopeEncryptionServiceTest {

    private AesGcmEnvelopeEncryptionService encryptionService;
    private CryptoProperties cryptoProperties;

    // 256-bit valid test master key
    private static final String VALID_MASTER_KEY = "k8vS4w1bY8Z3tqX9LmP0rTuVwXyZ1234567890ABCDE=";

    @BeforeEach
    void setUp() {
        cryptoProperties = new CryptoProperties();
        cryptoProperties.setMasterKey(VALID_MASTER_KEY);
        encryptionService = new AesGcmEnvelopeEncryptionService(cryptoProperties);
    }

    @Test
    @DisplayName("Envelope encryption encrypts and decrypts accurately back to original plaintext")
    void encryptAndDecryptSuccess() {
        byte[] plaintext = "Confidential Digital Will Estate Instructions 2026".getBytes(StandardCharsets.UTF_8);

        EncryptedData encrypted = encryptionService.encrypt(plaintext);

        assertThat(encrypted).isNotNull();
        assertThat(encrypted.ciphertext()).isNotEqualTo(plaintext);
        assertThat(encrypted.algorithm()).isEqualTo("AES/GCM/NoPadding");
        assertThat(encrypted.keyWrapAlgorithm()).isEqualTo("AESWrap");
        assertThat(encrypted.version()).isEqualTo(1);
        assertThat(encrypted.iv()).isNotBlank();
        assertThat(encrypted.encryptedDek()).isNotBlank();

        byte[] decrypted = encryptionService.decrypt(
                encrypted.ciphertext(),
                encrypted.encryptedDek(),
                encrypted.iv(),
                encrypted.version()
        );

        assertThat(decrypted).isEqualTo(plaintext);
    }

    @Test
    @DisplayName("Encrypting the same plaintext twice produces distinct IVs, wrapped DEKs, and ciphertexts")
    void encryptionRandomnessAndDistinctIvs() {
        byte[] plaintext = "Identical Payload Across Calls".getBytes(StandardCharsets.UTF_8);

        EncryptedData enc1 = encryptionService.encrypt(plaintext);
        EncryptedData enc2 = encryptionService.encrypt(plaintext);

        assertThat(enc1.iv()).isNotEqualTo(enc2.iv());
        assertThat(enc1.encryptedDek()).isNotEqualTo(enc2.encryptedDek());
        assertThat(enc1.ciphertext()).isNotEqualTo(enc2.ciphertext());
    }

    @Test
    @DisplayName("Decryption fails closed when ciphertext is tampered (auth tag failure)")
    void decryptionFailsOnTamperedCiphertext() {
        byte[] plaintext = "Sensitive Financial Ledger".getBytes(StandardCharsets.UTF_8);
        EncryptedData encrypted = encryptionService.encrypt(plaintext);

        byte[] tamperedCiphertext = encrypted.ciphertext().clone();
        tamperedCiphertext[0] ^= 0xFF; // Flip bits in ciphertext

        assertThatThrownBy(() -> encryptionService.decrypt(
                tamperedCiphertext,
                encrypted.encryptedDek(),
                encrypted.iv(),
                encrypted.version()
        )).isInstanceOf(DecryptionFailedException.class);
    }

    @Test
    @DisplayName("Decryption fails closed when IV is corrupted")
    void decryptionFailsOnCorruptedIv() {
        byte[] plaintext = "Sensitive Medical Directive".getBytes(StandardCharsets.UTF_8);
        EncryptedData encrypted = encryptionService.encrypt(plaintext);

        byte[] ivBytes = Base64.getDecoder().decode(encrypted.iv());
        ivBytes[0] ^= 0xAA;
        String corruptedIv = Base64.getEncoder().encodeToString(ivBytes);

        assertThatThrownBy(() -> encryptionService.decrypt(
                encrypted.ciphertext(),
                encrypted.encryptedDek(),
                corruptedIv,
                encrypted.version()
        )).isInstanceOf(DecryptionFailedException.class);
    }

    @Test
    @DisplayName("Decryption fails closed when wrapped DEK is tampered")
    void decryptionFailsOnTamperedDek() {
        byte[] plaintext = "Private Cryptographic Seed".getBytes(StandardCharsets.UTF_8);
        EncryptedData encrypted = encryptionService.encrypt(plaintext);

        byte[] dekBytes = Base64.getDecoder().decode(encrypted.encryptedDek());
        dekBytes[2] ^= 0x55;
        String corruptedDek = Base64.getEncoder().encodeToString(dekBytes);

        assertThatThrownBy(() -> encryptionService.decrypt(
                encrypted.ciphertext(),
                corruptedDek,
                encrypted.iv(),
                encrypted.version()
        )).isInstanceOf(DecryptionFailedException.class);
    }

    @Test
    @DisplayName("Decryption fails closed when attempted with a different master key")
    void decryptionFailsWithWrongMasterKey() {
        byte[] plaintext = "Family Asset Allocation Map".getBytes(StandardCharsets.UTF_8);
        EncryptedData encrypted = encryptionService.encrypt(plaintext);

        // Different 256-bit key
        CryptoProperties otherProps = new CryptoProperties();
        otherProps.setMasterKey("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=");
        AesGcmEnvelopeEncryptionService otherService = new AesGcmEnvelopeEncryptionService(otherProps);

        assertThatThrownBy(() -> otherService.decrypt(
                encrypted.ciphertext(),
                encrypted.encryptedDek(),
                encrypted.iv(),
                encrypted.version()
        )).isInstanceOf(DecryptionFailedException.class);
    }

    @Test
    @DisplayName("Decryption rejects unsupported crypto version")
    void decryptionRejectsUnsupportedVersion() {
        byte[] plaintext = "Legacy Document".getBytes(StandardCharsets.UTF_8);
        EncryptedData encrypted = encryptionService.encrypt(plaintext);

        assertThatThrownBy(() -> encryptionService.decrypt(
                encrypted.ciphertext(),
                encrypted.encryptedDek(),
                encrypted.iv(),
                999
        )).isInstanceOf(UnsupportedCryptoVersionException.class);
    }

    @Test
    @DisplayName("Initialization rejects invalid master key length")
    void invalidMasterKeyLengthRejected() {
        CryptoProperties props = new CryptoProperties();
        props.setMasterKey(Base64.getEncoder().encodeToString("too-short".getBytes(StandardCharsets.UTF_8)));

        assertThatThrownBy(() -> new AesGcmEnvelopeEncryptionService(props))
                .isInstanceOf(InvalidMasterKeyException.class);
    }

    @Test
    @DisplayName("Initialization rejects blank or non-Base64 master keys")
    void blankAndMalformedMasterKeysRejected() {
        CryptoProperties blankProps = new CryptoProperties();
        blankProps.setMasterKey("   ");
        assertThatThrownBy(() -> new AesGcmEnvelopeEncryptionService(blankProps))
                .isInstanceOf(InvalidMasterKeyException.class);

        CryptoProperties malformedProps = new CryptoProperties();
        malformedProps.setMasterKey("not-valid-base64!@#$%^");
        assertThatThrownBy(() -> new AesGcmEnvelopeEncryptionService(malformedProps))
                .isInstanceOf(InvalidMasterKeyException.class);
    }

    @Test
    @DisplayName("Decryption fails closed on auth tag tampering specifically (last 16 bytes)")
    void decryptionFailsOnAuthTagTampering() {
        byte[] plaintext = "Private Will Instruction".getBytes(StandardCharsets.UTF_8);
        EncryptedData encrypted = encryptionService.encrypt(plaintext);

        byte[] tamperedCiphertext = encrypted.ciphertext().clone();
        // The last 16 bytes (128 bits) are the GCM authentication tag
        int tagOffset = tamperedCiphertext.length - 1;
        tamperedCiphertext[tagOffset] ^= 0x01;

        assertThatThrownBy(() -> encryptionService.decrypt(
                tamperedCiphertext,
                encrypted.encryptedDek(),
                encrypted.iv(),
                encrypted.version()
        )).isInstanceOf(DecryptionFailedException.class);
    }

    @Test
    @DisplayName("Repeated encryptions generate strictly unique nonces across 100 runs")
    void nonceUniquenessAcrossMultipleRuns() {
        byte[] plaintext = "Constant Payload".getBytes(StandardCharsets.UTF_8);
        java.util.Set<String> ivs = new java.util.HashSet<>();

        for (int i = 0; i < 100; i++) {
            EncryptedData data = encryptionService.encrypt(plaintext);
            assertThat(ivs.add(data.iv())).isTrue();
        }
        assertThat(ivs).hasSize(100);
    }

    @Test
    @DisplayName("Decryption failure never leaks plaintext in exception messages")
    void decryptionFailureDoesNotLeakPlaintext() {
        String sensitiveSecret = "SUPER_SECRET_UNENCRYPTED_TEXT_123";
        byte[] plaintext = sensitiveSecret.getBytes(StandardCharsets.UTF_8);
        EncryptedData encrypted = encryptionService.encrypt(plaintext);

        byte[] corrupted = encrypted.ciphertext().clone();
        corrupted[0] ^= 0x01;

        assertThatThrownBy(() -> encryptionService.decrypt(
                corrupted,
                encrypted.encryptedDek(),
                encrypted.iv(),
                encrypted.version()
        )).isInstanceOf(DecryptionFailedException.class)
          .satisfies(e -> assertThat(e.getMessage()).doesNotContain(sensitiveSecret));
    }
}

