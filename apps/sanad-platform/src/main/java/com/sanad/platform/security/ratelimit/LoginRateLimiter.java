package com.sanad.platform.security.ratelimit;

import java.time.Duration;

/**
 * Pluggable rate-limiter abstraction for authentication attempts.
 *
 * <p>Prior to EXEC-PROMPT-SANAD-FULLSTACK-REMEDIATION-010, login throttling was
 * implemented inline in {@code AuthService} as a Caffeine cache keyed only on
 * the user's email. That had two operational problems:
 * <ol>
 *   <li>The limiter was hard-wired to a single-node in-memory store, so any
 *       multi-instance deployment lost protection on restart and could be
 *       bypassed by sending each request to a different instance.</li>
 *   <li>The key was email-only, with no IP component and no trusted-proxy
 *       handling, leaving accounts vulnerable to either lockout-by-email or
 *       unlimited guessing from rotating IPs.</li>
 * </ol>
 *
 * <p>This interface provides a single seam so a production deployment can supply
 * a distributed adapter (e.g. Redis-backed) without modifying {@code AuthService}.
 * Implementations MUST be thread-safe.
 *
 * <p>Implementations MUST NOT reveal whether a specific account exists. The
 * decision returned to callers must be the same shape for unknown accounts as
 * for known ones (so brute-force probing cannot enumerate accounts via timing
 * or counter deltas).
 */
public interface LoginRateLimiter {

    /**
     * Inspect the current state for the given key without incrementing.
     *
     * <p>This is used to fast-fail a request BEFORE doing any password work,
     * so that locked-out accounts never trigger a DB lookup or hash check
     * (which also avoids leaking "this account exists" via response timing).
     */
    Decision check(String compositeKey);

    /**
     * Record a failed attempt for the given key. Implementations are
     * responsible for expiring counters when the configured window elapses.
     */
    void recordFailure(String compositeKey);

    /**
     * Reset the counter for the given key. Called on successful login so the
     * legitimate account is not penalised for past failures, while keeping
     * the per-IP counters intact (those are tracked under a different key).
     */
    void recordSuccess(String compositeKey);

    /** Immutable rate-limit decision. */
    final class Decision {
        private final boolean allowed;
        private final Duration retryAfter;

        private Decision(boolean allowed, Duration retryAfter) {
            this.allowed = allowed;
            this.retryAfter = retryAfter;
        }

        public static Decision allow() {
            return new Decision(true, Duration.ZERO);
        }

        public static Decision deny(Duration retryAfter) {
            return new Decision(false, retryAfter == null ? Duration.ZERO : retryAfter);
        }

        public boolean isAllowed() {
            return allowed;
        }

        public Duration getRetryAfter() {
            return retryAfter;
        }
    }
}
