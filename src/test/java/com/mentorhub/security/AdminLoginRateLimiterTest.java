package com.mentorhub.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AdminLoginRateLimiterTest {
    @Test
    void throttlesAfterTenAttemptsAndAllowsRetryAfterOneMinute() {
        long[] now = {1_000};
        AdminLoginRateLimiter limiter = new AdminLoginRateLimiter(() -> now[0]);

        for (int attempt = 0; attempt < 10; attempt++) {
            assertThat(limiter.tryAcquire("192.0.2.10")).isTrue();
        }
        assertThat(limiter.tryAcquire("192.0.2.10")).isFalse();
        assertThat(limiter.tryAcquire("192.0.2.11")).isTrue();

        now[0] += 60_000;
        assertThat(limiter.tryAcquire("192.0.2.10")).isTrue();
    }
}
