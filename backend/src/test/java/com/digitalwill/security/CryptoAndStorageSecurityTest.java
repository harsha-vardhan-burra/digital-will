package com.digitalwill.security;

import com.digitalwill.common.TestTimeProvider;
import com.digitalwill.config.TestTimeConfig;
import com.digitalwill.crypto.exception.DecryptionFailedException;
import com.digitalwill.crypto.exception.UnsupportedCryptoVersionException;
import com.digitalwill.crypto.model.EncryptedData;
import com.digitalwill.crypto.service.AesGcmEnvelopeEncryptionService;
import com.digitalwill.crypto.storage.LocalDocumentStorageService;
import com.digitalwill.estate.model.Asset;
import com.digitalwill.estate.model.AssetCategory;
import com.digitalwill.estate.model.Beneficiary;
import com.digitalwill.estate.service.EstateService;
import com.digitalwill.release.exception.DisclosureNotReadyException;
import com.digitalwill.release.exception.DisclosureTokenConsumedException;
import com.digitalwill.release.model.DisclosureToken;
import com.digitalwill.release.repository.DisclosureTokenRepository;
import com.digitalwill.release.service.DisclosureService;
import com.digitalwill.release.service.ReleaseExecutionService;
import com.digitalwill.state.model.WillStateEntity;
import com.digitalwill.state.service.WillStateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestTimeConfig.class)
public class CryptoAndStorageSecurityTest {

    @Autowired MockMvc mockMvc;
    @Autowired AesGcmEnvelopeEncryptionService encryptionService;
    @Autowired LocalDocumentStorageService storageService;
    @Autowired WillStateService willStateService;
    @Autowired EstateService estateService;
    @Autowired ReleaseExecutionService releaseExecutionService;
    @Autowired DisclosureService disclosureService;
    @Autowired DisclosureTokenRepository disclosureTokenRepository;
    @Autowired TestTimeProvider timeProvider;

    private final Instant baseTime = Instant.parse("2026-10-01T10:00:00Z");

    @BeforeEach
    void setUp() {
        timeProvider.setNow(baseTime);
    }

    // --- Cryptographic Tampering ---

    @Test
    @DisplayName("ATK-15: Modifying ciphertext bits triggers fail-closed DecryptionFailedException")
    void ciphertextTampering_failsClosed() {
        byte[] plaintext = "Highly sensitive digital will testament".getBytes(StandardCharsets.UTF_8);
        EncryptedData enc = encryptionService.encrypt(plaintext);

        // Tamper with ciphertext by flipping a bit
        byte[] tamperedCiphertext = enc.ciphertext().clone();
        tamperedCiphertext[0] ^= 0x01;

        assertThrows(DecryptionFailedException.class, () -> {
            encryptionService.decrypt(tamperedCiphertext, enc.encryptedDek(), enc.iv(), enc.version());
        });
    }

    @Test
    @DisplayName("ATK-15: Modifying AES-GCM authentication tag triggers fail-closed DecryptionFailedException")
    void gcmAuthTagTampering_failsClosed() {
        byte[] plaintext = "Confidential testament details".getBytes(StandardCharsets.UTF_8);
        EncryptedData enc = encryptionService.encrypt(plaintext);

        // In Java AES/GCM/NoPadding, the 128-bit authentication tag is appended at the end of ciphertext
        byte[] tamperedTagCiphertext = enc.ciphertext().clone();
        tamperedTagCiphertext[tamperedTagCiphertext.length - 1] ^= 0x01;

        assertThrows(DecryptionFailedException.class, () -> {
            encryptionService.decrypt(tamperedTagCiphertext, enc.encryptedDek(), enc.iv(), enc.version());
        });
    }

    @Test
    @DisplayName("ATK-16: Tampered wrapped DEK triggers fail-closed DecryptionFailedException")
    void wrappedDekTampering_failsClosed() {
        byte[] plaintext = "Will annexure with crypto credentials".getBytes(StandardCharsets.UTF_8);
        EncryptedData enc = encryptionService.encrypt(plaintext);

        byte[] dekBytes = Base64.getDecoder().decode(enc.encryptedDek());
        dekBytes[0] ^= 0x55;
        String tamperedDek = Base64.getEncoder().encodeToString(dekBytes);

        assertThrows(DecryptionFailedException.class, () -> {
            encryptionService.decrypt(enc.ciphertext(), tamperedDek, enc.iv(), enc.version());
        });
    }

    @Test
    @DisplayName("ATK-15: Tampered IV triggers fail-closed DecryptionFailedException")
    void ivTampering_failsClosed() {
        byte[] plaintext = "Private assets list".getBytes(StandardCharsets.UTF_8);
        EncryptedData enc = encryptionService.encrypt(plaintext);

        byte[] ivBytes = Base64.getDecoder().decode(enc.iv());
        ivBytes[0] ^= 0xFF;
        String tamperedIv = Base64.getEncoder().encodeToString(ivBytes);

        assertThrows(DecryptionFailedException.class, () -> {
            encryptionService.decrypt(enc.ciphertext(), enc.encryptedDek(), tamperedIv, enc.version());
        });
    }

    @Test
    @DisplayName("ATK-15: Unsupported crypto version triggers UnsupportedCryptoVersionException")
    void unsupportedVersion_rejected() {
        byte[] plaintext = "Valid payload".getBytes(StandardCharsets.UTF_8);
        EncryptedData enc = encryptionService.encrypt(plaintext);

        assertThrows(UnsupportedCryptoVersionException.class, () -> {
            encryptionService.decrypt(enc.ciphertext(), enc.encryptedDek(), enc.iv(), 99);
        });
    }

    // --- Document Storage Traversal ---

    @Test
    @DisplayName("ATK-17: Relative path traversal attempts are rejected with SecurityException")
    void pathTraversal_relativeAttempts_rejected() {
        assertThrows(SecurityException.class, () -> storageService.retrieve("../secret.enc"));
        assertThrows(SecurityException.class, () -> storageService.retrieve("..\\secret.enc"));
        assertThrows(SecurityException.class, () -> storageService.retrieve("documents/../../secret.enc"));
    }

    @Test
    @DisplayName("ATK-17: Absolute path traversal attempts are rejected with SecurityException")
    void pathTraversal_absoluteAttempts_rejected() {
        assertThrows(SecurityException.class, () -> storageService.retrieve("/etc/passwd"));
        assertThrows(SecurityException.class, () -> storageService.retrieve("C:\\Windows\\system.ini"));
        assertThrows(SecurityException.class, () -> storageService.retrieve("\\secret.enc"));
    }

    @Autowired com.digitalwill.verification.service.VerificationTokenService tokenService;

    // --- Controlled Disclosure Security ---

    @Test
    @DisplayName("ATK-19: Pre-execution disclosure access is rejected with 409 Conflict")
    void preExecutionDisclosure_rejected() {
        UUID willId = willStateService.createWill(UUID.randomUUID(), "Pre-execution will").getId();
        Beneficiary ben = estateService.addBeneficiary(willId, "Ben", "ben_" + UUID.randomUUID() + "@example.com", "Friend");

        // Create a dummy token directly for test
        String rawToken = "test-raw-token-" + UUID.randomUUID();
        String tokenHash = tokenService.hashToken(rawToken);
        DisclosureToken token = new DisclosureToken(
                UUID.randomUUID(), willId, ben.getId(), tokenHash,
                com.digitalwill.release.model.DisclosureTokenStatus.ACTIVE,
                baseTime, baseTime.plus(Duration.ofDays(30))
        );
        disclosureTokenRepository.save(token);

        // Accessing disclosure while will is still ACTIVE
        assertThrows(DisclosureNotReadyException.class, () -> {
            disclosureService.accessDisclosure(rawToken);
        });
    }

    @Test
    @DisplayName("ATK-20: Single-use disclosure token cannot be replayed once consumed")
    void singleUseDisclosureToken_replayRejected() {
        UUID willId = willStateService.createWill(UUID.randomUUID(), "Execution Will").getId();
        Asset asset = estateService.addAsset(willId, "House", AssetCategory.REAL_ESTATE, "Desc", null, null);
        Beneficiary ben = estateService.addBeneficiary(willId, "Child", "child_" + UUID.randomUUID() + "@example.com", "Child");
        estateService.allocateAsset(willId, asset.getId(), ben.getId(), 100, "All to child");

        // Progress will to EXECUTED
        timeProvider.advance(Duration.ofDays(181));
        willStateService.triggerInactivityWarning(willId, Duration.ofDays(180));
        timeProvider.advance(Duration.ofDays(8));
        willStateService.triggerFinalWarning(willId, Duration.ofDays(7));
        timeProvider.advance(Duration.ofDays(8));
        willStateService.triggerVerificationPending(willId, Duration.ofDays(7));
        willStateService.triggerVerified(willId, 2, 2);
        Instant releaseAfter = timeProvider.now().plus(Duration.ofDays(3));
        willStateService.scheduleRelease(willId, releaseAfter);
        timeProvider.setNow(releaseAfter.plusSeconds(60));

        // Claim and execute release
        var execResult = releaseExecutionService.claimAndExecuteRelease(willId);
        assertThat(execResult.executed()).isTrue();

        // Retrieve generated disclosure token
        DisclosureToken token = disclosureTokenRepository.findByWillId(willId).stream().findFirst().orElseThrow();
        // Since raw token was hashed on creation, we can test consumeDisclosure directly with a known test token
        String knownRawToken = "known-token-" + UUID.randomUUID();
        String knownHash = tokenService.hashToken(knownRawToken);
        token.setTokenHash(knownHash);
        token.setStatus(com.digitalwill.release.model.DisclosureTokenStatus.ACTIVE);
        disclosureTokenRepository.save(token);

        // First consumption succeeds
        var firstAccess = disclosureService.consumeDisclosure(knownRawToken);
        assertThat(firstAccess).isNotNull();

        // Second consumption must fail with DisclosureTokenConsumedException (replay blocked)
        assertThrows(DisclosureTokenConsumedException.class, () -> {
            disclosureService.consumeDisclosure(knownRawToken);
        });
    }
}
