package com.digitalwill.verification.service;

import com.digitalwill.common.TestTimeProvider;
import com.digitalwill.state.model.WillState;
import com.digitalwill.state.model.WillStateEntity;
import com.digitalwill.state.repository.WillStateRepository;
import com.digitalwill.state.service.WillStateService;
import com.digitalwill.verification.model.TrustedContact;
import com.digitalwill.verification.repository.ContactConfirmationRepository;
import com.digitalwill.verification.repository.TrustedContactRepository;
import com.digitalwill.verification.repository.VerificationRequestRepository;
import com.digitalwill.verification.repository.WillContactRepository;
import com.digitalwill.config.TestTimeConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.annotation.DirtiesContext;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Import(TestTimeConfig.class)
class VerificationServiceIntegrationTest {

    @Autowired WillStateService willStateService;
    @Autowired VerificationService verificationService;
    @Autowired WillStateRepository willStateRepository;
    @Autowired TrustedContactRepository trustedContactRepository;
    @Autowired WillContactRepository willContactRepository;
    @Autowired VerificationRequestRepository verificationRequestRepository;
    @Autowired ContactConfirmationRepository confirmationRepository;
    @Autowired TestTimeProvider timeProvider;

    private UUID willId;
    private TrustedContact contactA, contactB, contactC;
    private final Instant base = Instant.parse("2026-03-10T10:00:00Z");
    private final Duration inactivity = Duration.ofDays(30);
    private final Duration warn = Duration.ofDays(7);
    private final Duration finalWarn = Duration.ofDays(3);

    @BeforeEach
    void setUp() {
        timeProvider.setNow(base);
        WillStateEntity will = willStateService.createWill(UUID.randomUUID(), "Test Will");
        willId = will.getId();
        // Drive to VERIFICATION_PENDING via State Engine (PR1)
        timeProvider.setNow(base.plus(inactivity).plus(Duration.ofHours(1)));
        willStateService.triggerInactivityWarning(willId, inactivity);
        timeProvider.setNow(base.plus(inactivity).plus(warn).plus(Duration.ofHours(1)));
        willStateService.triggerFinalWarning(willId, warn);
        timeProvider.setNow(base.plus(inactivity).plus(warn).plus(finalWarn).plus(Duration.ofHours(1)));
        willStateService.triggerVerificationPending(willId, finalWarn);
        assertThat(willStateRepository.findById(willId).orElseThrow().getState()).isEqualTo(WillState.VERIFICATION_PENDING);

        contactA = verificationService.createTrustedContact("Alice", "alice-" + UUID.randomUUID() + "@example.com");
        contactB = verificationService.createTrustedContact("Bob", "bob-" + UUID.randomUUID() + "@example.com");
        contactC = verificationService.createTrustedContact("Carol", "carol-" + UUID.randomUUID() + "@example.com");
        verificationService.associateContactWithWill(willId, contactA.getId());
        verificationService.associateContactWithWill(willId, contactB.getId());
        verificationService.associateContactWithWill(willId, contactC.getId());
    }

    @Test
    void oneOfThree_doesNotReachQuorum() {
        String tokenA = verificationService.createVerificationRequest(willId, contactA.getId());
        VerificationService.ConfirmationResult r = verificationService.confirm(tokenA);
        assertThat(r.confirmed()).isTrue();
        assertThat(r.quorumReached()).isFalse();
        assertThat(willStateRepository.findById(willId).orElseThrow().getState()).isEqualTo(WillState.VERIFICATION_PENDING);
        assertThat(verificationService.countDistinctConfirmations(willId)).isEqualTo(1);
    }

    @Test
    void twoOfThree_reachesQuorumAndTransitionsToVerified() {
        String tokenA = verificationService.createVerificationRequest(willId, contactA.getId());
        String tokenB = verificationService.createVerificationRequest(willId, contactB.getId());
        verificationService.confirm(tokenA);
        VerificationService.ConfirmationResult r2 = verificationService.confirm(tokenB);
        assertThat(r2.quorumReached()).isTrue();
        assertThat(willStateRepository.findById(willId).orElseThrow().getState()).isEqualTo(WillState.VERIFIED);
    }

    @Test
    void duplicateContactCannotCountTwice() {
        String tokenA1 = verificationService.createVerificationRequest(willId, contactA.getId());
        verificationService.confirm(tokenA1);
        // Second request for same contact in same cycle
        String tokenA2 = verificationService.createVerificationRequest(willId, contactA.getId());
        assertThatThrownBy(() -> verificationService.confirm(tokenA2))
                .isInstanceOf(Exception.class);
        assertThat(verificationService.countDistinctConfirmations(willId)).isEqualTo(1);
        assertThat(willStateRepository.findById(willId).orElseThrow().getState()).isEqualTo(WillState.VERIFICATION_PENDING);
    }

    @Test
    void invalidToken_rejected() {
        assertThatThrownBy(() -> verificationService.confirm("bogus-invalid-token-12345"))
                .isInstanceOf(Exception.class);
        assertThat(verificationService.countDistinctConfirmations(willId)).isEqualTo(0);
    }

    @Test
    void expiredToken_rejected() {
        String token = verificationService.createVerificationRequest(willId, contactA.getId(), Duration.ofMinutes(5));
        timeProvider.setNow(timeProvider.now().plus(Duration.ofMinutes(10)));
        assertThatThrownBy(() -> verificationService.confirm(token))
                .isInstanceOf(Exception.class);
        assertThat(verificationService.countDistinctConfirmations(willId)).isEqualTo(0);
    }

    @Test
    void consumedToken_cannotBeReused() {
        String token = verificationService.createVerificationRequest(willId, contactA.getId());
        verificationService.confirm(token);
        assertThatThrownBy(() -> verificationService.confirm(token))
                .isInstanceOf(Exception.class);
        assertThat(verificationService.countDistinctConfirmations(willId)).isEqualTo(1);
    }

    @Test
    void idempotentRetry_returnsQuorumStatus() {
        String tokenA = verificationService.createVerificationRequest(willId, contactA.getId());
        verificationService.confirm(tokenA);
        String tokenB = verificationService.createVerificationRequest(willId, contactB.getId());
        verificationService.confirm(tokenB);
        // retry tokenB via idempotent
        VerificationService.ConfirmationResult r = verificationService.confirmIdempotent(tokenB);
        assertThat(r.confirmed()).isTrue();
        assertThat(r.alreadyConfirmed()).isTrue();
        assertThat(r.quorumReached()).isTrue();
    }

    @Test
    void wrongState_active_cannotConfirm() {
        // Create new will in ACTIVE, associate contact, try to confirm
        timeProvider.setNow(base);
        WillStateEntity other = willStateService.createWill(UUID.randomUUID(), "Other Will");
        TrustedContact c = verificationService.createTrustedContact("Dave", "dave-" + UUID.randomUUID() + "@example.com");
        verificationService.associateContactWithWill(other.getId(), c.getId());
        // Force create request bypassing state check via direct save? Instead test that createVerificationRequest rejects ACTIVE
        assertThatThrownBy(() -> verificationService.createVerificationRequest(other.getId(), c.getId()))
                .isInstanceOf(Exception.class);
    }

    @Test
    void wrongWillAssociation_rejected() {
        WillStateEntity other = willStateService.createWill(UUID.randomUUID(), "Other");
        // Drive other to VERIFICATION_PENDING as well
        timeProvider.setNow(timeProvider.now().plus(inactivity).plus(Duration.ofHours(2)));
        // Need to get other through states: but simpler - test token for willId cannot affect other will's state
        // Create token for willId then try to use contact not associated with willId
        TrustedContact outsider = verificationService.createTrustedContact("Outsider", "outsider-" + UUID.randomUUID() + "@example.com");
        // outsider not associated with willId - createVerificationRequest should fail
        assertThatThrownBy(() -> verificationService.createVerificationRequest(willId, outsider.getId()))
                .isInstanceOf(Exception.class);
    }

    @Test
    void ownerReset_invalidatesOldConfirmationsAndTokens() {
        String tokenA = verificationService.createVerificationRequest(willId, contactA.getId());
        verificationService.confirm(tokenA);
        assertThat(verificationService.countDistinctConfirmations(willId)).isEqualTo(1);
        // Owner reset VERIFICATION_PENDING -> ACTIVE (increments verification_cycle and revokes requests)
        willStateService.recordVerifiedActivity(willId, timeProvider.now());
        assertThat(willStateRepository.findById(willId).orElseThrow().getState()).isEqualTo(WillState.ACTIVE);
        // Old token should be revoked
        String tokenA2 = tokenA; // already consumed but also cycle stale
        assertThatThrownBy(() -> verificationService.confirm(tokenA2)).isInstanceOf(Exception.class);

        // Drive back to VERIFICATION_PENDING: need new cycle
        timeProvider.setNow(timeProvider.now().plus(inactivity).plus(Duration.ofHours(1)));
        willStateService.triggerInactivityWarning(willId, inactivity);
        timeProvider.setNow(timeProvider.now().plus(warn).plus(Duration.ofHours(1)));
        willStateService.triggerFinalWarning(willId, warn);
        timeProvider.setNow(timeProvider.now().plus(finalWarn).plus(Duration.ofHours(1)));
        willStateService.triggerVerificationPending(willId, finalWarn);
        // Old confirmation must not count toward new cycle
        assertThat(verificationService.countDistinctConfirmations(willId)).isEqualTo(0);
        String newTokenA = verificationService.createVerificationRequest(willId, contactA.getId());
        verificationService.confirm(newTokenA);
        assertThat(verificationService.countDistinctConfirmations(willId)).isEqualTo(1);
        assertThat(willStateRepository.findById(willId).orElseThrow().getState()).isEqualTo(WillState.VERIFICATION_PENDING);
    }

    @Test
    void tokenStorage_isHashed() {
        String raw = verificationService.createVerificationRequest(willId, contactA.getId());
        var reqs = verificationRequestRepository.findByWillIdAndVerificationCycle(willId, willStateRepository.findById(willId).orElseThrow().getVerificationCycle());
        assertThat(reqs).isNotEmpty();
        String storedHash = reqs.get(0).getTokenHash();
        assertThat(storedHash).isNotEqualTo(raw);
        assertThat(storedHash).hasSize(64); // SHA-256 hex
    }

    @Test
    void databaseUniquenessConstraint_enforced() {
        // Directly test DB constraint: try to insert duplicate confirmation via repository
        String tokenA = verificationService.createVerificationRequest(willId, contactA.getId());
        verificationService.confirm(tokenA);
        // Try to manually insert second confirmation same will/contact/cycle
        var will = willStateRepository.findById(willId).orElseThrow();
        var req = verificationRequestRepository.findByWillIdAndVerificationCycle(willId, will.getVerificationCycle()).get(0);
        var dup = new com.digitalwill.verification.model.ContactConfirmation(
                UUID.randomUUID(), willId, contactA.getId(), req.getId(), will.getVerificationCycle(), timeProvider.now());
        assertThatThrownBy(() -> {
            confirmationRepository.saveAndFlush(dup);
        }).isInstanceOf(DataIntegrityViolationException.class);
    }
}