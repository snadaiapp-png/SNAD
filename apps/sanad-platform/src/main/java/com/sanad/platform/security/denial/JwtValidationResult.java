package com.sanad.platform.security.denial;

import io.jsonwebtoken.Claims;

/**
 * Stage 05A.2.9.1 §6 — Typed result of JWT validation, replacing the
 * previous {@code Claims parseAndValidate(token) -> null on any failure}
 * signature that collapsed every Bearer failure into a single bucket.
 *
 * <p>Sealed interface: the only permitted implementations are
 * {@link Valid} and {@link Invalid}. A {@code switch} over this type is
 * exhaustive without a default branch.</p>
 *
 * <p>The {@link Invalid#reason()} category is the <strong>exact</strong>
 * value persisted to {@code platform_security_audit_events.failure_category}.
 * The classifier never leaks token internals, JWT library exception
 * messages, or signer diagnostics to the client.</p>
 */
public sealed interface JwtValidationResult {

    /**
     * Successful validation. Carries the parsed claims.
     */
    record Valid(Claims claims) implements JwtValidationResult {
        public Valid {
            if (claims == null) {
                throw new IllegalArgumentException("claims must not be null for Valid");
            }
        }
    }

    /**
     * Failed validation. Carries the canonical denial category — never
     * the underlying exception.
     */
    record Invalid(SecurityDenialCategory reason) implements JwtValidationResult {
        public Invalid {
            if (reason == null) {
                throw new IllegalArgumentException("reason must not be null for Invalid");
            }
            // Invalid is reserved for pre-auth JWT failure categories.
            switch (reason) {
                case MISSING_JWT, MALFORMED_JWT, INVALID_SIGNATURE,
                     EXPIRED_JWT, INVALID_SUBJECT, UNVERIFIED_TENANT -> {}
                default -> throw new IllegalArgumentException(
                        "JwtValidationResult.Invalid cannot carry category: " + reason);
            }
        }
    }
}
