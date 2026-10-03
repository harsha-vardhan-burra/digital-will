package com.digitalwill.state.concurrency;

import com.digitalwill.common.TestTimeProvider;
import com.digitalwill.state.model.WillState;
import com.digitalwill.state.model.WillStateEntity;
import com.digitalwill.state.repository.AtomicWillTransitionRepository;
import com.digitalwill.state.repository.WillStateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Concurrency test proving the critical atomicity invariant (Section 45).
 *
 * Visible Proof:
 * RELEASE_PENDING
 *        |
 *        +------ Worker A ------+
 *        |                      |
 *        |                EXECUTING
 *        |                (1 row)
 *        |
 *        +------ Worker B ------+
 *                               |
 *                            0 rows
 *                            no execution
 */
@SpringBootTest
class WillStateConcurrencyTest {

    @Autowired
    private WillStateRepository willStateRepository;

    @Autowired
    private AtomicWillTransitionRepository atomicTransitionRepository;

    private final Instant baseTime = Instant.parse("2026-10-01T12:00:00Z");

    @Test
    @DisplayName("Critical Proof: Concurrent workers race to claim execution; exactly 1 succeeds (1 row), all others fail (0 rows)")
    void concurrentExecutionClaimAtomicity() throws InterruptedException {
        // Setup: Create a Will in RELEASE_PENDING where release delay has passed
        UUID willId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        Instant releaseAfter = baseTime.minus(Duration.ofMinutes(5)); // eligible 5 mins ago

        WillStateEntity will = new WillStateEntity(willId, ownerId, "Concurrency Proof Will", WillState.RELEASE_PENDING, baseTime, baseTime);
        will.setReleaseAfter(releaseAfter);
        willStateRepository.save(will);

        int workerCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(workerCount);
        CountDownLatch readyLatch = new CountDownLatch(workerCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicInteger successfulClaims = new AtomicInteger(0);
        AtomicInteger failedClaims = new AtomicInteger(0);

        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < workerCount; i++) {
            futures.add(executor.submit(() -> {
                readyLatch.countDown();
                try {
                    // Synchronize threads so all fire simultaneously
                    startLatch.await();

                    Instant now = baseTime;
                    boolean claimed = atomicTransitionRepository.claimExecuting(willId, now, now);
                    if (claimed) {
                        successfulClaims.incrementAndGet();
                    } else {
                        failedClaims.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }));
        }

        // Wait for all workers to be ready, then trigger race
        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown();

        for (Future<?> f : futures) {
            try {
                f.get(5, TimeUnit.SECONDS);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        executor.shutdown();

        // INVARIANT PROOF:
        // Exactly 1 worker claimed the transition (1 row updated)
        // All other 9 workers received 0 rows updated
        assertThat(successfulClaims.get())
                .as("Exactly one worker must successfully claim EXECUTING")
                .isEqualTo(1);

        assertThat(failedClaims.get())
                .as("All competing workers must fail to claim (0 rows updated)")
                .isEqualTo(workerCount - 1);

        // Final state in DB must be EXECUTING
        WillStateEntity finalWill = willStateRepository.findById(willId).orElseThrow();
        assertThat(finalWill.getState()).isEqualTo(WillState.EXECUTING);
        assertThat(finalWill.getExecutingAt()).isNotNull();
    }

    @Test
    @DisplayName("Race condition between Worker claiming execution and Owner resetting to ACTIVE")
    void concurrentClaimVsOwnerReset() throws InterruptedException {
        UUID willId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        Instant releaseAfter = baseTime.minus(Duration.ofMinutes(1));

        WillStateEntity will = new WillStateEntity(willId, ownerId, "Race Test Will", WillState.RELEASE_PENDING, baseTime, baseTime);
        will.setReleaseAfter(releaseAfter);
        willStateRepository.save(will);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch readyLatch = new CountDownLatch(2);
        CountDownLatch startLatch = new CountDownLatch(1);

        List<String> results = Collections.synchronizedList(new ArrayList<>());

        // Worker thread
        executor.submit(() -> {
            readyLatch.countDown();
            try {
                startLatch.await();
                boolean claimed = atomicTransitionRepository.claimExecuting(willId, baseTime, baseTime);
                if (claimed) {
                    results.add("WORKER_CLAIMED");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        // Owner activity thread
        executor.submit(() -> {
            readyLatch.countDown();
            try {
                startLatch.await();
                boolean reset = atomicTransitionRepository.resetToActive(willId, baseTime, baseTime, null);
                if (reset) {
                    results.add("OWNER_RESET");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown();

        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);

        // Invariant: Exactly one operation succeeds, never both!
        // Either the worker claimed EXECUTING before reset, or owner reset to ACTIVE before claim.
        assertThat(results).hasSize(1);

        WillStateEntity finalWill = willStateRepository.findById(willId).orElseThrow();
        if (results.contains("WORKER_CLAIMED")) {
            assertThat(finalWill.getState()).isEqualTo(WillState.EXECUTING);
        } else {
            assertThat(finalWill.getState()).isEqualTo(WillState.ACTIVE);
        }
    }
}
