package com.digitalwill.security.ratelimit;

import com.digitalwill.common.TestTimeProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitServiceTest {

    private RateLimitProperties properties;
    private TestTimeProvider timeProvider;
    private RateLimitService rateLimitService;

    private final Instant baseTime = Instant.parse("2026-06-01T12:00:00Z");

    @BeforeEach
    void setUp() {
        properties = new RateLimitProperties();
        properties.setEnabled(true);
        timeProvider = new TestTimeProvider(baseTime);
        rateLimitService = new RateLimitService(properties, timeProvider);
    }

    @Test
    @DisplayName("Concurrency: Many threads contesting single key allow exactly N requests and reject remainder")
    void concurrentRequests_singleKey_exactlyNAllowed() throws Exception {
        int threads = 50;
        int maxRequests = 10;
        long windowSeconds = 60;
        String key = "test-concurrent-key";

        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch readyLatch = new CountDownLatch(threads);
        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicInteger allowedCount = new AtomicInteger(0);
        AtomicInteger rejectedCount = new AtomicInteger(0);

        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    RateLimitService.RateLimitDecision decision =
                            rateLimitService.checkLimit(key, maxRequests, windowSeconds);
                    if (decision.allowed()) {
                        allowedCount.incrementAndGet();
                    } else {
                        rejectedCount.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }));
        }

        readyLatch.await();
        startLatch.countDown();

        for (Future<?> f : futures) {
            f.get();
        }
        executor.shutdown();

        assertThat(allowedCount.get()).isEqualTo(maxRequests);
        assertThat(rejectedCount.get()).isEqualTo(threads - maxRequests);
    }

    @Test
    @DisplayName("Clock jump backward: Future-dated entries from a clock jump are evicted safely")
    void clockJumpBackward_futureEntriesEvicted() {
        String key = "test-clock-jump-key";
        int maxRequests = 3;
        long windowSeconds = 60;

        // Record 3 requests at base time
        for (int i = 0; i < 3; i++) {
            RateLimitService.RateLimitDecision decision =
                    rateLimitService.checkLimit(key, maxRequests, windowSeconds);
            assertThat(decision.allowed()).isTrue();
        }

        // 4th request at base time is rejected
        assertThat(rateLimitService.checkLimit(key, maxRequests, windowSeconds).allowed()).isFalse();

        // Advance clock into the future and record request
        timeProvider.setNow(baseTime.plus(Duration.ofMinutes(10)));
        RateLimitService.RateLimitDecision futureDecision =
                rateLimitService.checkLimit(key, maxRequests, windowSeconds);
        assertThat(futureDecision.allowed()).isTrue();

        // Simulate clock jump backwards (e.g., NTP adjustment or test fixture reset to earlier time)
        timeProvider.setNow(baseTime.plus(Duration.ofMinutes(5)));

        // The future entry (from +10 mins) must be evicted so it cannot pollute earlier timestamps
        RateLimitService.RateLimitDecision backwardDecision =
                rateLimitService.checkLimit(key, maxRequests, windowSeconds);
        assertThat(backwardDecision.allowed()).isTrue();
    }

    @Test
    @DisplayName("Sliding window: Expired entries outside window are evicted and capacity is restored")
    void slidingWindow_expiredEntriesEvicted() {
        String key = "test-sliding-window-key";
        int maxRequests = 2;
        long windowSeconds = 30;

        assertThat(rateLimitService.checkLimit(key, maxRequests, windowSeconds).allowed()).isTrue();
        assertThat(rateLimitService.checkLimit(key, maxRequests, windowSeconds).allowed()).isTrue();
        assertThat(rateLimitService.checkLimit(key, maxRequests, windowSeconds).allowed()).isFalse();

        // Advance 31 seconds (past 30s window)
        timeProvider.setNow(baseTime.plusSeconds(31));

        RateLimitService.RateLimitDecision afterExpiry =
                rateLimitService.checkLimit(key, maxRequests, windowSeconds);
        assertThat(afterExpiry.allowed()).isTrue();
    }
}
