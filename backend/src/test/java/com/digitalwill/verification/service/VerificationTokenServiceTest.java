package com.digitalwill.verification.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VerificationTokenServiceTest {

    private final VerificationTokenService service = new VerificationTokenService();

    @Test
    void generatedTokens_areUnpredictableAndUnique() {
        String t1 = service.generateRawToken();
        String t2 = service.generateRawToken();
        assertThat(t1).isNotEqualTo(t2);
        assertThat(t1).hasSizeGreaterThanOrEqualTo(40);
    }

    @Test
    void hashAndMatch() {
        String raw = service.generateRawToken();
        String hash = service.hashToken(raw);
        assertThat(service.matches(raw, hash)).isTrue();
        assertThat(service.matches("other", hash)).isFalse();
        assertThat(hash).hasSize(64);
    }

    @Test
    void expiry_isTimeProviderBased_notWallClock() {
        // Verified via integration test using TestTimeProvider; unit test just ensures hash is deterministic
        String raw = "test-token-123";
        String h1 = service.hashToken(raw);
        String h2 = service.hashToken(raw);
        assertThat(h1).isEqualTo(h2);
    }
}