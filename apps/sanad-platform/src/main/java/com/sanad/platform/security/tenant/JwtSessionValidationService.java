package com.sanad.platform.security.tenant;

import com.sanad.platform.security.denial.SessionValidationResult;
import com.sanad.platform.security.tenant.JwtSessionValidationService.VerifiedJwtClaims;
import com.sanad.platform.security.tenant.JwtSessionValidationService.ValidatedSession;

import java.util.UUID;

/**
 * Stage 04A.1 §4 — Service that validates a JWT session inside a
 * tenant-bound transaction.
 *
 * <p>The {@link com.sanad.platform.security.filter.JwtAuthenticationFilter}
 * calls this service AFTER cryptographically validating the JWT and
 * extracting the claims, but BEFORE creating the Spring Security
 * Authentication.</p>
 *
 * <p>The validation runs inside a {@code @Transactional(readOnly = true)}
 * method with a provisional TenantContext established from the JWT claims.
 * This ensures the session-version query against the RLS-protected
 * {@code users} table runs with the correct tenant setting.</p>
 *
 * <p>Stage 05A.2.9.1 §7 — The signature was changed from
 * {@code ValidatedSession validate(claims)} (returning null on failure)
 * to {@code SessionValidationResult validateAndClassify(claims)} so the
 * caller can persist the exact denial category
 * (UNKNOWN_SESSION / REVOKED_SESSION / UNVERIFIED_TENANT) instead of
 * collapsing every failure to a single bucket.</p>
 */
public interface JwtSessionValidationService {

    /**
     * Validates the session for the given verified JWT claims, returning
     * a typed result that classifies the failure (if any).
     *
     * @param claims the cryptographically verified JWT claims
     * @return {@link SessionValidationResult.Valid} on success, or
     *         {@link SessionValidationResult.Invalid} carrying the
     *         canonical denial category
     */
    SessionValidationResult validateAndClassify(VerifiedJwtClaims claims);

    /**
     * Verified JWT claims extracted after cryptographic validation.
     */
    record VerifiedJwtClaims(
            UUID tenantId,
            UUID userId,
            String tokenId,
            String email,
            long sessionVersion,
            boolean rotationRequired
    ) {}

    /**
     * Result of successful session validation.
     */
    record ValidatedSession(
            UUID tenantId,
            UUID userId,
            String tokenId,
            String email,
            long currentSessionVersion,
            boolean rotationRequired,
            boolean userActive,
            boolean tenantActive
    ) {}
}
