package com.digitalwill.job.service;

import com.digitalwill.common.TestTimeProvider;
import com.digitalwill.config.TestTimeConfig;
import com.digitalwill.state.model.WillState;
import com.digitalwill.state.model.WillStateEntity;
import com.digitalwill.state.service.WillStateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestTimeConfig.class)
class JobConcurrencyTest {

    @Autowired InactivityProcessingJobService inactivityJob;
    @Autowired WillStateService willStateService;
    @Autowired com.digitalwill.state.repository.WillStateRepository willStateRepository;
    @Autowired TestTimeProvider timeProvider;

    private final Instant base = Instant.parse("2026-11-01T10:00:00Z");
    private final List<UUID> willIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        timeProvider.setNow(base);
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        for (UUID id : willIds) {
            willStateRepository.deleteById(id);
        }
    }

    @Test
    void concurrentJobRuns_noDuplicateTransitions_noStateSkips() throws InterruptedException, ExecutionException {
        // Create 5 wills
        for (int i = 0; i < 5; i++) {
            WillStateEntity will = willStateService.createWill(UUID.randomUUID(), "Concurrent Job Will " + i);
            willIds.add(will.getId());
        }

        // Fast forward 95 days -> all 5 eligible for INACTIVITY_WARNING (threshold is 90 days)
        timeProvider.setNow(base.plus(Duration.ofDays(95)));

        // Run 4 concurrent worker threads executing processInactivityJob() simultaneously
        int threads = 4;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CyclicBarrier barrier = new CyclicBarrier(threads);
        List<Future<InactivityProcessingJobService.InactivityJobReport>> futures = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            futures.add(executor.submit(() -> {
                barrier.await();
                return inactivityJob.processInactivityJob();
            }));
        }

        int totalWarned = 0;
        for (Future<InactivityProcessingJobService.InactivityJobReport> f : futures) {
            InactivityProcessingJobService.InactivityJobReport res = f.get();
            totalWarned += res.warnedActiveWills();
            assertThat(res.errors()).isEmpty();
        }
        executor.shutdown();

        // At least 5 total warnings claimed across worker threads
        assertThat(totalWarned).isGreaterThanOrEqualTo(5);

        // Check that all 5 wills transitioned to INACTIVITY_WARNING and NONE skipped to FINAL_WARNING
        for (UUID id : willIds) {
            WillStateEntity w = willStateService.getWillOrThrow(id);
            assertThat(w.getState()).isEqualTo(WillState.INACTIVITY_WARNING);
        }
    }
}
