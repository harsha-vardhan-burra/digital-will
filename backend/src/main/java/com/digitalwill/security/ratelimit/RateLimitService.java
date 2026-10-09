package com.digitalwill.security.ratelimit;

import com.digitalwill.common.TimeProvider;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe in-memory sliding-window rate limiter for sensitive unauthenticated endpoints (Workstream 7).
 * Avoids distributed infrastructure (Redis) while providing robust single-instance abuse prevention.
 */
@Service
public class RateLimitService {

    private final RateLimitProperties properties;
    private final TimeProvider timeProvider;
    private final Map<String, Deque<Long>> requestWindows = new ConcurrentHashMap<>();

    public RateLimitService(RateLimitProperties properties, TimeProvider timeProvider) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.timeProvider = Objects.requireNonNull(timeProvider, "timeProvider must not be null");
    }

    public record RateLimitDecision(boolean allowed, long retryAfterSeconds) {}

    /**
     * Checks if a request from the given key is allowed under the configured limit and window.
     * Synchronized per key to guarantee thread-safe sliding window tracking.
     */
    public RateLimitDecision checkLimit(String key, int maxRequests, long windowSeconds) {
        if (!properties.isEnabled()) {
            return new RateLimitDecision(true, 0);
        }

        long nowEpochMilli = timeProvider.now().toEpochMilli();
        long windowMillis = windowSeconds * 1000L;
        long windowStart = nowEpochMilli - windowMillis;

        // Synchronize on the internal window for this specific key
        Deque<Long> window = requestWindows.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (window) {
            // Evict expired timestamps outside the sliding window
            while (!window.isEmpty() && window.peekFirst() < windowStart) {
                window.pollFirst();
            }

            if (window.size() >= maxRequests) {
                Long oldest = window.peekFirst();
                long oldestTime = oldest != null ? oldest : nowEpochMilli;
                long retryAfter = Math.max(1, ((oldestTime + windowMillis) - nowEpochMilli) / 1000L + 1);
                return new RateLimitDecision(false, retryAfter);
            }

            window.addLast(nowEpochMilli);
            return new RateLimitDecision(true, 0);
        }
    }

    /**
     * Resets all tracking state. Intended for test fixtures and maintenance.
     */
    public void reset() {
        requestWindows.clear();
    }
}
