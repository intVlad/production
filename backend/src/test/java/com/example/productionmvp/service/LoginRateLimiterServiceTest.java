package com.example.productionmvp.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class LoginRateLimiterServiceTest {

    @Test
    void checkAllowed_NoPriorAttempts_DoesNotThrow() {
        LoginRateLimiterService limiter = new LoginRateLimiterService();
        assertDoesNotThrow(() -> limiter.checkAllowed("ip:1.2.3.4"));
    }

    @Test
    void checkAllowed_LocksOutAfterFiveFailures() {
        LoginRateLimiterService limiter = new LoginRateLimiterService();
        String key = "ip:1.2.3.4";

        for (int i = 0; i < 5; i++) {
            limiter.recordFailure(key);
        }

        assertThrows(LoginRateLimiterService.TooManyAttemptsException.class, () -> limiter.checkAllowed(key));
    }

    @Test
    void checkAllowed_UnderThreshold_DoesNotLockOut() {
        LoginRateLimiterService limiter = new LoginRateLimiterService();
        String key = "ip:1.2.3.4";

        for (int i = 0; i < 4; i++) {
            limiter.recordFailure(key);
        }

        assertDoesNotThrow(() -> limiter.checkAllowed(key));
    }

    @Test
    void recordSuccess_ResetsFailureCount() {
        LoginRateLimiterService limiter = new LoginRateLimiterService();
        String key = "ip:1.2.3.4";

        for (int i = 0; i < 4; i++) {
            limiter.recordFailure(key);
        }
        limiter.recordSuccess(key);
        limiter.recordFailure(key);

        assertDoesNotThrow(() -> limiter.checkAllowed(key));
    }

    @Test
    void checkAllowed_DifferentKeysAreIndependent() {
        LoginRateLimiterService limiter = new LoginRateLimiterService();

        for (int i = 0; i < 5; i++) {
            limiter.recordFailure("ip:1.2.3.4");
        }

        assertDoesNotThrow(() -> limiter.checkAllowed("ip:5.6.7.8"));
    }
}
