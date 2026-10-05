package com.digitalwill.release.service;

import com.digitalwill.common.TestTimeProvider;
import com.digitalwill.config.TestTimeConfig;
import com.digitalwill.estate.model.Asset;
import com.digitalwill.estate.model.AssetCategory;
import com.digitalwill.estate.model.Beneficiary;
import com.digitalwill.estate.service.EstateService;
import com.digitalwill.release.model.ExecutionStatus;
import com.digitalwill.release.model.ReleaseExecution;
import com.digitalwill.release.repository.ReleaseExecutionRepository;
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
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestTimeConfig.class)
class ReleaseExecutionConcurrencyTest {

    @Autowired WillStateService willStateService;
    @Autowired EstateService estateService;
    @Autowired ReleaseExecutionService releaseExecutionService;
    @Autowired ReleaseExecutionRepository executionRepository;
    @Autowired TestTimeProvider timeProvider;

    private UUID willId;
    private final Instant base = Instant.parse("2026-10-01T10:00:00Z");

    @BeforeEach
    void setUp() {
        timeProvider.setNow(base);
        WillStateEntity will = willStateService.createWill(UUID.randomUUID(), "Concurrency Will");
        willId = will.getId();

        Asset asset = estateService.addAsset(willId, "Bank Account", AssetCategory.BANK_ACCOUNT, "Checking", "enc-data", "Transfer balance");
        Beneficiary bene = estateService.addBeneficiary(willId, "Carol", "carol@example.com", "Daughter");
        estateService.allocateAsset(asset.getId(), bene.getId(), 100, "100% to Carol");

        // Advance through State Engine to RELEASE_PENDING
        timeProvider.setNow(base.plus(Duration.ofDays(30)).plus(Duration.ofHours(1)));
        willStateService.triggerInactivityWarning(willId, Duration.ofDays(30));
        timeProvider.setNow(base.plus(Duration.ofDays(30)).plus(Duration.ofDays(7)).plus(Duration.ofHours(1)));
        willStateService.triggerFinalWarning(willId, Duration.ofDays(7));
        timeProvider.setNow(base.plus(Duration.ofDays(30)).plus(Duration.ofDays(7)).plus(Duration.ofDays(3)).plus(Duration.ofHours(1)));
        willStateService.triggerVerificationPending(willId, Duration.ofDays(3));
        willStateService.triggerVerified(willId, 2, 2);
        Instant releaseAfter = timeProvider.now().plus(Duration.ofDays(7));
        willStateService.scheduleRelease(willId, releaseAfter);
        timeProvider.setNow(releaseAfter.plus(Duration.ofHours(1)));
    }

    @Test
    void concurrentReleaseClaims_exactlyOneExecutes_noDuplicateRecords() throws InterruptedException, ExecutionException {
        int threadCount = 8;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CyclicBarrier barrier = new CyclicBarrier(threadCount);
        List<Future<ReleaseExecutionService.ExecutionResult>> futures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            futures.add(executor.submit(() -> {
                barrier.await();
                return releaseExecutionService.claimAndExecuteRelease(willId);
            }));
        }

        AtomicInteger completedCount = new AtomicInteger(0);
        for (Future<ReleaseExecutionService.ExecutionResult> f : futures) {
            ReleaseExecutionService.ExecutionResult res = f.get();
            if (res.executed()) {
                completedCount.incrementAndGet();
            }
        }
        executor.shutdown();

        // Will must reach terminal EXECUTED state
        WillStateEntity finalWill = willStateService.getWillOrThrow(willId);
        assertThat(finalWill.getState()).isEqualTo(WillState.EXECUTED);

        // Exactly one release execution record in repository
        List<ReleaseExecution> executions = executionRepository.findAll().stream()
                .filter(e -> e.getWillId().equals(willId))
                .toList();
        assertThat(executions).hasSize(1);
        assertThat(executions.get(0).getStatus()).isEqualTo(ExecutionStatus.COMPLETED);
    }
}
