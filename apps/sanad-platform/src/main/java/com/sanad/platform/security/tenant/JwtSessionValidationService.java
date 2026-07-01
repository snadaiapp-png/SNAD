package com.sanad.platform.security.tenant;

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
 */
public interface JwtSessionValidationService {

    /**
     * Validates the session for the given verified JWT claims.
     *
     * @param claims the cryptographically verified JWT claims
     * @return the validated session data, or null if validation fails
     */
    ValidatedSession validate(VerifiedJwtClaims claims);

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
     * Result of session validation.
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
