package com.example.productionmvp.service;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// PINs are 4 digits (10,000 combinations) and the un-targeted login endpoint checks a
// submitted PIN against every worker's hash, so without this an attacker (or a script hitting
// a deployed instance) can brute-force any account - including MANAGER/ADMIN - in minutes.
// Keyed by both source IP (stops single-source flooding of any account) and target workerId
// when known (stops a distributed attempt spread across many IPs against one account).
@Service
public class LoginRateLimiterService {

    private static final int MAX_ATTEMPTS = 5;
    private static final Duration WINDOW = Duration.ofMinutes(5);
    private static final Duration LOCKOUT = Duration.ofMinutes(15);

    private final Map<String, Attempts> attemptsByKey = new ConcurrentHashMap<>();

    private static final class Attempts {
        int count;
        Instant windowStart;
        Instant lockedUntil;
    }

    public static class TooManyAttemptsException extends RuntimeException {
        public TooManyAttemptsException(String message) {
            super(message);
        }
    }

    public void checkAllowed(String key) {
        Attempts a = attemptsByKey.get(key);
        if (a == null) return;
        synchronized (a) {
            if (a.lockedUntil != null && Instant.now().isBefore(a.lockedUntil)) {
                throw new TooManyAttemptsException("Забагато невдалих спроб входу. Спробуйте пізніше.");
            }
        }
    }

    public void recordFailure(String key) {
        attemptsByKey.compute(key, (k, existing) -> {
            Instant now = Instant.now();
            Attempts a = existing;
            if (a == null || now.isAfter(a.windowStart.plus(WINDOW))) {
                a = new Attempts();
                a.windowStart = now;
            }
            a.count++;
            if (a.count >= MAX_ATTEMPTS) {
                a.lockedUntil = now.plus(LOCKOUT);
            }
            return a;
        });
    }

    public void recordSuccess(String key) {
        attemptsByKey.remove(key);
    }
}
