package com.sanad.platform.security.tenant;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Stage 04 §7 — Central, immutable tenant context.
 *
 * <p>Stage 04A §6: sessionId is now the JWT jti (token ID), not the email.
 * sessionVersion is carried from the JWT session_version claim.</p>
 *
 * <p>This record is the single source of truth for the active tenant scope
 * of a request. It is established AFTER authentication by
 * {@link TenantContextFilter} and is consumed by controllers, services,
 * repositories, and the RLS binder.</p>
 */
public record TenantContext(
        UUID tenantId,
        UUID userId,
        String sessionId,
        long sessionVersion,
        Set<String> capabilities,
        TenantContextSource source,
        String requestId
) {

    public TenantContext {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(source, "source must not be null");
        if (capabilities == null) {
            capabilities = Set.of();
        }
    }

    public enum TenantContextSource {
        JWT_CLAIM,
        MEMBERSHIP_SELECTOR,
        BACKGROUND_JOB,
        TEST_FIXTURE,
        PROVISIONAL_TOKEN_VALIDATION
    }

    public boolean hasCapability(String capability) {
        return capabilities != null && capabilities.contains(capability);
    }

    /**
     * Returns true if capabilities have been verified (non-empty set loaded
     * from the database). An empty set means capabilities have NOT been
     * loaded — do not treat the user as having zero capabilities.
     */
    public boolean capabilitiesVerified() {
        return capabilities != null && !capabilities.isEmpty();
    }

    public boolean matchesTenant(UUID otherTenantId) {
        return otherTenantId != null && tenantId.equals(otherTenantId);
    }
}
