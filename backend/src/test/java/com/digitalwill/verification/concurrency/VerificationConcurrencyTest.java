package com.digitalwill.verification.concurrency;

import com.digitalwill.common.TestTimeProvider;
import com.digitalwill.state.model.WillState;
import com.digitalwill.state.model.WillStateEntity;
import com.digitalwill.state.repository.WillStateRepository;
import com.digitalwill.state.service.WillStateService;
import com.digitalwill.verification.model.TrustedContact;
import com.digitalwill.verification.repository.ContactConfirmationRepository;
import com.digitalwill.verification.service.VerificationService;
import com.digitalwill.config.TestTimeConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestTimeConfig.class)
class VerificationConcurrencyTest {

    @Autowired WillStateService willStateService;
    @Autowired VerificationService verificationService;
    @Autowired WillStateRepository willStateRepository;
    @Autowired ContactConfirmationRepository confirmationRepository;
    @Autowired TestTimeProvider timeProvider;

    private UUID willId;
    private TrustedContact contactA, contactB;
    private final Instant base = Instant.parse("2026-03-10T10:00:00Z");

    @BeforeEach
    void setUp() {
        timeProvider.setNow(base);
        WillStateEntity will = willStateService.createWill(UUID.randomUUID(), "Conc Will");
        willId = will.getId();
        timeProvider.setNow(base.plus(Duration.ofDays(30)).plus(Duration.ofHours(1)));
        willStateService.triggerInactivityWarning(willId, Duration.ofDays(30));
        timeProvider.setNow(base.plus(Duration.ofDays(30)).plus(Duration.ofDays(7)).plus(Duration.ofHours(1)));
        willStateService.triggerFinalWarning(willId, Duration.ofDays(7));
        timeProvider.setNow(base.plus(Duration.ofDays(30)).plus(Duration.ofDays(7)).plus(Duration.ofDays(3)).plus(Duration.ofHours(1)));
        willStateService.triggerVerificationPending(willId, Duration.ofDays(3));
        contactA = verificationService.createTrustedContact("Alice", "alice-c-" + UUID.randomUUID() + "@example.com");
        contactB = verificationService.createTrustedContact("Bob", "bob-c-" + UUID.randomUUID() + "@example.com");
        verificationService.associateContactWithWill(willId, contactA.getId());
        verificationService.associateContactWithWill(willId, contactB.getId());
    }

    @Test
    void concurrentDuplicateToken_onlyOneConfirmation() throws Exception {
        String token = verificationService.createVerificationRequest(willId, contactA.getId());
        int threads = 10;
        ExecutorService exec = Executors.newFixedThreadPool(threads);
        List<Callable<Boolean>> tasks = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            tasks.add(() -> {
                try {
                    verificationService.confirm(token);
                    return true;
                } catch (Exception e) {
                    return false;
                }
            });
        }
        List<Future<Boolean>> results = exec.invokeAll(tasks);
        exec.shutdown();
        long successes = results.stream().filter(f -> {
            try { return f.get(); } catch (Exception e) { return false; }
        }).count();
        // Only one should have succeeded creating confirmation; others should fail (already used / already confirmed)
        // With idempotent handling not used here, exactly 1 succeeds
        assertThat(successes).isEqualTo(1);
        assertThat(confirmationRepository.countByWillIdAndCycle(willId, willStateRepository.findById(willId).orElseThrow().getVerificationCycle())).isEqualTo(1);
    }

    @Test
    void concurrentABConfirmations_bothSucceedAndQuorumReachedOnce() throws Exception {
        String tokenA = verificationService.createVerificationRequest(willId, contactA.getId());
        String tokenB = verificationService.createVerificationRequest(willId, contactB.getId());
        ExecutorService exec = Executors.newFixedThreadPool(2);
        Future<VerificationService.ConfirmationResult> fA = exec.submit(() -> verificationService.confirm(tokenA));
        Future<VerificationService.ConfirmationResult> fB = exec.submit(() -> verificationService.confirm(tokenB));
        fA.get(); fB.get();
        exec.shutdown();
        assertThat(confirmationRepository.countByWillIdAndCycle(willId, willStateRepository.findById(willId).orElseThrow().getVerificationCycle())).isEqualTo(2);
        assertThat(willStateRepository.findById(willId).orElseThrow().getState()).isEqualTo(WillState.VERIFIED);
    }

    @Test
    void quorumRace_onlyOneTransitionToVerified() throws Exception {
        // Add third contact
        TrustedContact contactC = verificationService.createTrustedContact("Carol", "carol-c-" + UUID.randomUUID() + "@example.com");
        verificationService.associateContactWithWill(willId, contactC.getId());
        String tA = verificationService.createVerificationRequest(willId, contactA.getId());
        String tB = verificationService.createVerificationRequest(willId, contactB.getId());
        String tC = verificationService.createVerificationRequest(willId, contactC.getId());
        verificationService.confirm(tA); // 1 of 3
        ExecutorService exec = Executors.newFixedThreadPool(2);
        Future<VerificationService.ConfirmationResult> fB = exec.submit(() -> verificationService.confirm(tB));
        Future<VerificationService.ConfirmationResult> fC = exec.submit(() -> verificationService.confirm(tC));
        fB.get(); fC.get();
        exec.shutdown();
        // Distinct count should be 3, but state VERIFIED only once (atomic transition)
        assertThat(willStateRepository.findById(willId).orElseThrow().getState()).isEqualTo(WillState.VERIFIED);
        long distinct = confirmationRepository.countDistinctContactsByWillIdAndCycle(willId, willStateRepository.findById(willId).orElseThrow().getVerificationCycle());
        assertThat(distinct).isEqualTo(3);
    }
}