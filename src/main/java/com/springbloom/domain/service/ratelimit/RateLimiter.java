package com.springbloom.domain.service.ratelimit;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A token bucket per key. Pure and clock-driven, so the arithmetic is
 * assertable with a fixed clock. In memory: exact for one instance, and it
 * forgets on restart.
 */
public final class RateLimiter {

    private static final Duration SWEEP_EVERY = Duration.ofSeconds(10);

    private final int capacity;
    private final double refillPerSecond;
    private final int maxKeys;
    private final Clock clock;
    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();
    private volatile Instant lastSweep;

    public RateLimiter(int capacity, double refillPerMinute, int maxKeys, Clock clock) {

        if (capacity < 1 || refillPerMinute <= 0 || maxKeys < 1) {
            throw new IllegalArgumentException("capacity, refill and max keys must be positive");
        }
        this.capacity = capacity;
        this.refillPerSecond = refillPerMinute / 60.0;
        this.maxKeys = maxKeys;
        this.clock = clock;
        this.lastSweep = clock.instant();
    }

    /** Takes one token for the key, or says how long until one exists. */
    public Decision tryAcquire(String key) {

        Instant now = clock.instant();
        sweepIfCrowded(now);

        Bucket bucket = buckets.computeIfAbsent(key, k -> new Bucket(capacity, now));
        return bucket.take(now);
    }

    public int trackedKeys() {
        return buckets.size();
    }

    /** A full bucket is indistinguishable from no bucket, so it can be dropped. */
    private void sweepIfCrowded(Instant now) {

        if (buckets.size() <= maxKeys || Duration.between(lastSweep, now).compareTo(SWEEP_EVERY) < 0) {
            return;
        }
        lastSweep = now;
        buckets.entrySet().removeIf(entry -> entry.getValue().isFull(now));
    }

    public record Decision(boolean allowed, Duration retryAfter) {

        static Decision granted() {
            return new Decision(true, Duration.ZERO);
        }

        static Decision denied(Duration retryAfter) {
            return new Decision(false, retryAfter);
        }
    }

    private final class Bucket {

        private double tokens;
        private Instant updated;

        Bucket(double tokens, Instant now) {
            this.tokens = tokens;
            this.updated = now;
        }

        synchronized Decision take(Instant now) {

            refill(now);
            if (tokens >= 1.0) {
                tokens -= 1.0;
                return Decision.granted();
            }
            double missing = 1.0 - tokens;
            long millis = (long) Math.ceil(missing / refillPerSecond * 1000.0);
            return Decision.denied(Duration.ofMillis(millis));
        }

        synchronized boolean isFull(Instant now) {
            refill(now);
            return tokens >= capacity;
        }

        private void refill(Instant now) {

            long elapsedMillis = Duration.between(updated, now).toMillis();
            if (elapsedMillis > 0) {
                tokens = Math.min(capacity, tokens + elapsedMillis / 1000.0 * refillPerSecond);
                updated = now;
            }
        }
    }
}
