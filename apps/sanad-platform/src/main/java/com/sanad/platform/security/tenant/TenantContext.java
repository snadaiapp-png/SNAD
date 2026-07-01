package com.sanad.platform.security.tenant;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Stage 04 §7 — Central, immutable tenant context.
 *
 * <p>This record is the single source of truth for the active tenant scope
 * of a request. It is established AFTER authentication by
 * {@link TenantContextFilter} and is consumed by:</p>
 * <ul>
 *   <li>Controllers (via {@link TenantContextProvider#requireContext()})</li>
 *   <li>Services (via constructor injection or {@code TenantContextProvider})</li>
 *   <li>Repository helpers (via {@code TenantContextProvider})</li>
 *   <li>RLS helpers (via {@code TenantContextProvider})</li>
 * </ul>
 *
 * <p>The {@code tenantId} is NEVER sourced from the client. It comes from the
 * verified JWT claims, validated against active session state and (when
 * relevant) the user's authorized memberships.</p>
 *
 * <p>{@code source} records WHERE the tenant binding came from so that
 * audits can distinguish a JWT-derived binding from an explicit selector
 * validated against memberships.</p>
 *
 * <p>Immutable by design — once created, the context cannot be mutated.
 * This prevents accidental context drift within a request.</p>
 */
public record TenantContext(
        UUID tenantId,
        UUID userId,
        String sessionId,
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
        // sessionId and requestId may be null in test/seed contexts.
    }

    /** Source of the tenant binding. */
    public enum TenantContextSource {
        /** Tenant binding came directly from the verified JWT claim. */
        JWT_CLAIM,
        /** Tenant binding came from a server-side membership lookup after selector validation. */
        MEMBERSHIP_SELECTOR,
        /** Tenant binding was established by a privileged background job with explicit tenantId. */
        BACKGROUND_JOB,
        /** Tenant binding was established by a test fixture. */
        TEST_FIXTURE
    }

    /**
     * Returns {@code true} if the given capability is granted in this
     * tenant context.
     */
    public boolean hasCapability(String capability) {
        return capabilities != null && capabilities.contains(capability);
    }

    /**
     * Returns {@code true} if the supplied tenantId matches this context's
     * tenantId. Used to validate client-supplied selectors for backwards
     * compatibility (per Stage 04 §9 transitional strategy).
     */
    public boolean matchesTenant(UUID otherTenantId) {
        return otherTenantId != null && tenantId.equals(otherTenantId);
    }
}
