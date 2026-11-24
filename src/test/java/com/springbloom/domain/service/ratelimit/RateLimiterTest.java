package com.springbloom.domain.service.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RateLimiterTest {

    static final class MutableClock extends Clock {

        private Instant now = Instant.parse("2026-11-23T12:00:00Z");

        void advance(Duration by) {
            now = now.plus(by);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private final MutableClock clock = new MutableClock();

    @Test
    @DisplayName("a burst up to capacity passes, then the next call is denied")
    void burstThenDeny() {
        RateLimiter limiter = new RateLimiter(3, 6, 100, clock);

        assertThat(limiter.tryAcquire("a").allowed()).isTrue();
        assertThat(limiter.tryAcquire("a").allowed()).isTrue();
        assertThat(limiter.tryAcquire("a").allowed()).isTrue();
        assertThat(limiter.tryAcquire("a").allowed()).isFalse();
    }

    @Test
    @DisplayName("a denial says how long until a token exists")
    void retryAfter() {
        RateLimiter limiter = new RateLimiter(1, 6, 100, clock);
        limiter.tryAcquire("a");

        var denied = limiter.tryAcquire("a");

        assertThat(denied.allowed()).isFalse();
        assertThat(denied.retryAfter()).isEqualTo(Duration.ofSeconds(10));
    }

    @Test
    @DisplayName("tokens refill with time, never beyond capacity")
    void refills() {
        RateLimiter limiter = new RateLimiter(2, 6, 100, clock);
        limiter.tryAcquire("a");
        limiter.tryAcquire("a");
        assertThat(limiter.tryAcquire("a").allowed()).isFalse();

        clock.advance(Duration.ofSeconds(10));
        assertThat(limiter.tryAcquire("a").allowed()).isTrue();
        assertThat(limiter.tryAcquire("a").allowed()).isFalse();

        clock.advance(Duration.ofHours(1));
        assertThat(limiter.tryAcquire("a").allowed()).isTrue();
        assertThat(limiter.tryAcquire("a").allowed()).isTrue();
        assertThat(limiter.tryAcquire("a").allowed()).isFalse();
    }

    @Test
    @DisplayName("each key has its own bucket")
    void keysAreIndependent() {
        RateLimiter limiter = new RateLimiter(1, 6, 100, clock);

        assertThat(limiter.tryAcquire("a").allowed()).isTrue();
        assertThat(limiter.tryAcquire("a").allowed()).isFalse();
        assertThat(limiter.tryAcquire("b").allowed()).isTrue();
    }

    @Test
    @DisplayName("idle full buckets are swept once the key ceiling is passed")
    void sweepsIdleKeys() {
        RateLimiter limiter = new RateLimiter(1, 60, 5, clock);
        IntStream.range(0, 20).forEach(i -> limiter.tryAcquire("ip-" + i));
        assertThat(limiter.trackedKeys()).isEqualTo(20);

        clock.advance(Duration.ofSeconds(30));
        limiter.tryAcquire("fresh");

        assertThat(limiter.trackedKeys()).isLessThanOrEqualTo(2);
    }

    @Test
    @DisplayName("concurrent callers never get more than the capacity")
    void concurrentCallersRespectCapacity() {
        RateLimiter limiter = new RateLimiter(50, 1, 100, clock);
        AtomicInteger granted = new AtomicInteger();

        IntStream.range(0, 500).parallel().forEach(i -> {
            if (limiter.tryAcquire("a").allowed()) {
                granted.incrementAndGet();
            }
        });

        assertThat(granted.get()).isEqualTo(50);
    }

    @Test
    @DisplayName("rejects nonsense configuration")
    void validates() {
        assertThatThrownBy(() -> new RateLimiter(0, 1, 1, clock)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RateLimiter(1, 0, 1, clock)).isInstanceOf(IllegalArgumentException.class);
    }
}
