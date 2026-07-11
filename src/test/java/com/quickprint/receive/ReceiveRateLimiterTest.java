package com.quickprint.receive;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReceiveRateLimiterTest {

    @Test
    void allowsUpToTheLimitThenBlocks() {
        ReceiveRateLimiter limiter = new ReceiveRateLimiter();

        for (int i = 0; i < 20; i++) {
            assertThat(limiter.tryAcquire("1.2.3.4")).as("attempt %d", i + 1).isTrue();
        }
        assertThat(limiter.tryAcquire("1.2.3.4")).isFalse();
    }

    @Test
    void keepsASeparateBudgetPerKey() {
        ReceiveRateLimiter limiter = new ReceiveRateLimiter();

        for (int i = 0; i < 20; i++) {
            limiter.tryAcquire("attacker");
        }
        // A different client is unaffected by the attacker's exhausted budget.
        assertThat(limiter.tryAcquire("someone-else")).isTrue();
    }
}
