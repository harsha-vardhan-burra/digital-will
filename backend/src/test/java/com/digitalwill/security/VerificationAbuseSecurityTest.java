package com.digitalwill.security;

import com.digitalwill.common.TestTimeProvider;
import com.digitalwill.config.TestTimeConfig;
import com.digitalwill.state.model.WillState;
import com.digitalwill.state.model.WillStateEntity;
import com.digitalwill.state.service.WillStateService;
import com.digitalwill.verification.exception.AlreadyConfirmedException;
import com.digitalwill.verification.exception.InvalidVerificationTokenException;
import com.digitalwill.verification.exception.VerificationRequestRevokedException;
import com.digitalwill.verification.exception.VerificationTokenExpiredException;
import com.digitalwill.verification.model.TrustedContact;
import com.digitalwill.verification.repository.ContactConfirmationRepository;
import com.digitalwill.verification.repository.VerificationRequestRepository;
import com.digitalwill.verification.service.VerificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@Import(TestTimeConfig.class)
public class VerificationAbuseSecurityTest {

    @Autowired VerificationService verificationService;
    @Autowired WillStateService willStateService;
    @Autowired ContactConfirmationRepository confirmationRepository;
    @Autowired VerificationRequestRepository verificationRequestRepository;
    @Autowired TestTimeProvider timeProvider;

    private final Instant baseTime = Instant.parse("2026-10-01T10:00:00Z");
    private UUID willId;
    private TrustedContact contact1;
    private TrustedContact contact2;
    private TrustedContact contact3;

    @BeforeEach
    void setUp() {
        timeProvider.setNow(baseTime);

        WillStateEntity will = willStateService.createWill(UUID.randomUUID(), "Verification Security Will");
        willId = will.getId();

        contact1 = verificationService.createTrustedContact("Alice Contact", "alice_" + UUID.randomUUID() + "@example.com");
        contact2 = verificationService.createTrustedContact("Bob Contact", "bob_" + UUID.randomUUID() + "@example.com");
        contact3 = verificationService.createTrustedContact("Charlie Contact", "charlie_" + UUID.randomUUID() + "@example.com");

        verificationService.associateContactWithWill(willId, contact1.getId());
        verificationService.associateContactWithWill(willId, contact2.getId());
        verificationService.associateContactWithWill(willId, contact3.getId());

        // Fast-forward to VERIFICATION_PENDING
        timeProvider.advance(Duration.ofDays(181));
        willStateService.triggerInactivityWarning(willId, Duration.ofDays(180));
        timeProvider.advance(Duration.ofDays(8));
        willStateService.triggerFinalWarning(willId, Duration.ofDays(7));
        timeProvider.advance(Duration.ofDays(8));
        willStateService.triggerVerificationPending(willId, Duration.ofDays(7));
    }

    @Test
    @DisplayName("ATK-12: Same contact cannot confirm twice within the same verification cycle")
    void sameContact_confirmTwice_fails() {
        String token1a = verificationService.createVerificationRequest(willId, contact1.getId());
        String token1b = verificationService.createVerificationRequest(willId, contact1.getId());

        var res1 = verificationService.confirm(token1a);
        assertThat(res1.confirmed()).isTrue();
        assertThat(res1.quorumReached()).isFalse();

        // Second confirmation by same contact using different token in same cycle
        assertThrows(AlreadyConfirmedException.class, () -> {
            verificationService.confirm(token1b);
        });

        // Confirmations count must remain 1
        assertThat(verificationService.countDistinctConfirmations(willId)).isEqualTo(1);
    }

    @Test
    @DisplayName("ATK-12: Idempotent confirmation with the same token returns consistent state")
    void sameToken_confirmIdempotent_succeedsDeterministically() {
        String token = verificationService.createVerificationRequest(willId, contact1.getId());

        var res1 = verificationService.confirmIdempotent(token);
        assertThat(res1.confirmed()).isTrue();
        assertThat(res1.alreadyConfirmed()).isFalse();

        // Repeat submission of exact same token
        var res2 = verificationService.confirmIdempotent(token);
        assertThat(res2.confirmed()).isTrue();
        assertThat(res2.alreadyConfirmed()).isTrue();

        assertThat(verificationService.countDistinctConfirmations(willId)).isEqualTo(1);
    }

    @Test
    @DisplayName("ATK-13: Verification token from a stale cycle is rejected after owner activity reset")
    void staleCycleToken_isRejectedAfterReset() {
        String tokenCycle0 = verificationService.createVerificationRequest(willId, contact1.getId());

        // Owner activity resets will back to ACTIVE and invalidates pending verification cycle
        willStateService.recordVerifiedActivity(willId, timeProvider.now());
        verificationService.invalidateVerificationForWill(willId);

        WillStateEntity willAfterReset = willStateService.getWillOrThrow(willId);
        assertThat(willAfterReset.getState()).isEqualTo(WillState.ACTIVE);

        // Advance to VERIFICATION_PENDING again (Cycle 1)
        timeProvider.advance(Duration.ofDays(181));
        willStateService.triggerInactivityWarning(willId, Duration.ofDays(180));
        timeProvider.advance(Duration.ofDays(8));
        willStateService.triggerFinalWarning(willId, Duration.ofDays(7));
        timeProvider.advance(Duration.ofDays(8));
        willStateService.triggerVerificationPending(willId, Duration.ofDays(7));

        // Attempting to confirm with cycle 0 token must be rejected
        assertThrows(VerificationRequestRevokedException.class, () -> {
            verificationService.confirm(tokenCycle0);
        });
    }

    @Test
    @DisplayName("ATK-14: Expired verification token is rejected with VerificationTokenExpiredException")
    void expiredToken_isRejected() {
        String token = verificationService.createVerificationRequest(willId, contact1.getId(), Duration.ofDays(7));

        // Advance past 7 days TTL
        timeProvider.advance(Duration.ofDays(8));

        assertThrows(VerificationTokenExpiredException.class, () -> {
            verificationService.confirm(token);
        });
    }

    @Test
    @DisplayName("ATK-14: Forged or non-existent token is rejected with InvalidVerificationTokenException")
    void forgedToken_isRejected() {
        assertThrows(InvalidVerificationTokenException.class, () -> {
            verificationService.confirm("forged-token-xyz-12345");
        });
    }

    @Test
    @DisplayName("ATK-12: Exactly two distinct trusted contacts reach quorum and transition to VERIFIED")
    void twoDistinctContacts_reachQuorumSuccessfully() {
        String token1 = verificationService.createVerificationRequest(willId, contact1.getId());
        String token2 = verificationService.createVerificationRequest(willId, contact2.getId());

        var res1 = verificationService.confirm(token1);
        assertThat(res1.confirmed()).isTrue();
        assertThat(res1.quorumReached()).isFalse();
        assertThat(willStateService.getWillOrThrow(willId).getState()).isEqualTo(WillState.VERIFICATION_PENDING);

        var res2 = verificationService.confirm(token2);
        assertThat(res2.confirmed()).isTrue();
        assertThat(res2.quorumReached()).isTrue();
        assertThat(res2.resultingState()).isEqualTo(WillState.VERIFIED);
        assertThat(willStateService.getWillOrThrow(willId).getState()).isEqualTo(WillState.VERIFIED);
    }

    @Test
    @DisplayName("ATK-14: Third trusted contact confirming after quorum does not trigger redundant transitions")
    void thirdContact_afterQuorum_handledCleanly() {
        String token1 = verificationService.createVerificationRequest(willId, contact1.getId());
        String token2 = verificationService.createVerificationRequest(willId, contact2.getId());
        String token3 = verificationService.createVerificationRequest(willId, contact3.getId());

        verificationService.confirm(token1);
        verificationService.confirm(token2);

        // Quorum is satisfied, state is VERIFIED
        assertThat(willStateService.getWillOrThrow(willId).getState()).isEqualTo(WillState.VERIFIED);

        // Third contact confirms
        var res3 = verificationService.confirm(token3);
        assertThat(res3.confirmed()).isTrue();
        assertThat(res3.quorumReached()).isTrue();
        assertThat(willStateService.getWillOrThrow(willId).getState()).isEqualTo(WillState.VERIFIED);
        assertThat(verificationService.countDistinctConfirmations(willId)).isEqualTo(3);
    }
}
