package com.quickprint.guest;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GuestUploadRateLimiterTest {

    @Test
    void allowsFivePerHourThenBlocksTheSixth() {
        GuestUploadRateLimiter limiter = new GuestUploadRateLimiter();

        for (int i = 0; i < 5; i++) {
            assertThat(limiter.tryAcquire("1.2.3.4")).as("attempt %d", i + 1).isTrue();
        }
        assertThat(limiter.tryAcquire("1.2.3.4")).isFalse();
    }

    @Test
    void limitsAreTrackedPerClient() {
        GuestUploadRateLimiter limiter = new GuestUploadRateLimiter();

        for (int i = 0; i < 6; i++) {
            limiter.tryAcquire("1.2.3.4");
        }
        assertThat(limiter.tryAcquire("5.6.7.8")).isTrue();
    }
}
