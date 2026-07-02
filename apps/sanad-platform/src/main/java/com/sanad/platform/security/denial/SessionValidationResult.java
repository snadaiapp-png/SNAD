package com.sanad.platform.security.denial;

import com.sanad.platform.security.tenant.JwtSessionValidationService.ValidatedSession;

/**
 * Stage 05A.2.9.1 §7 — Typed result of session validation, replacing
 * the previous {@code ValidatedSession validate(claims) -> null on any
 * failure} signature.
 *
 * <p>Sealed interface: the only permitted implementations are
 * {@link Valid} and {@link Invalid}. A {@code switch} over this type is
 * exhaustive without a default branch.</p>
 *
 * <p>The {@link Invalid#reason()} category is the <strong>exact</strong>
 * value persisted to {@code platform_security_audit_events.failure_category}.
 * The classifier never leaks which DB lookup failed, never reveals the
 * user's status, and never discloses whether the tenant exists.</p>
 */
public sealed interface SessionValidationResult {

    /**
     * Successful validation. Carries the verified session data.
     */
    record Valid(ValidatedSession session) implements SessionValidationResult {
        public Valid {
            if (session == null) {
                throw new IllegalArgumentException("session must not be null for Valid");
            }
        }
    }

    /**
     * Failed validation. Carries the canonical denial category —
     * one of {@link SecurityDenialCategory#UNKNOWN_SESSION},
     * {@link SecurityDenialCategory#REVOKED_SESSION}, or
     * {@link SecurityDenialCategory#UNVERIFIED_TENANT}.
     */
    record Invalid(SecurityDenialCategory reason) implements SessionValidationResult {
        public Invalid {
            if (reason == null) {
                throw new IllegalArgumentException("reason must not be null for Invalid");
            }
            switch (reason) {
                case UNKNOWN_SESSION, REVOKED_SESSION, UNVERIFIED_TENANT -> {}
                default -> throw new IllegalArgumentException(
                        "SessionValidationResult.Invalid cannot carry category: " + reason);
            }
        }
    }
}
