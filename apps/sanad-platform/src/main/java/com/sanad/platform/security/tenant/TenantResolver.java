package com.sanad.platform.security.tenant;

import com.sanad.platform.shared.api.exceptions.TenantContextException;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Stage 04 §9 — Helper for services that need the current tenantId.
 *
 * <p>Services MUST NOT accept {@code tenantId} as a method parameter
 * from controllers (per Stage 04 §9). Instead, they inject this helper
 * and call {@link #requireTenantId()} or {@link #currentTenantId()}.</p>
 *
 * <p>This ensures the tenantId always comes from the verified
 * {@link TenantContext}, never from client input.</p>
 */
@Component
public class TenantResolver {

    private final TenantContextProvider contextProvider;

    public TenantResolver(TenantContextProvider contextProvider) {
        this.contextProvider = contextProvider;
    }

    /**
     * Returns the current tenantId, throwing if no context is set.
     */
    public UUID requireTenantId() {
        return contextProvider.requireContext().tenantId();
    }

    /**
     * Returns the current tenantId as an Optional-friendly null check.
     * Returns {@code null} if no context is set.
     */
    public UUID currentTenantId() {
        return contextProvider.currentContext()
                .map(TenantContext::tenantId)
                .orElse(null);
    }

    /**
     * Returns the current userId, throwing if no context is set.
     */
    public UUID requireUserId() {
        return contextProvider.requireContext().userId();
    }

    /**
     * Stage 04 §9 transitional strategy — validates a client-supplied
     * tenantId selector against the verified TenantContext.
     *
     * @param clientTenantId the tenantId from the request (may be null)
     * @return the verified tenantId from the context
     * @throws TenantContextException if no context is set
     * @throws com.sanad.platform.shared.api.exceptions.TenantContextException
     *         if the client tenantId doesn't match the context tenantId
     */
    public UUID validateClientSelector(UUID clientTenantId) {
        UUID contextTenantId = requireTenantId();
        if (clientTenantId != null && !clientTenantId.equals(contextTenantId)) {
            throw new TenantContextException(
                "Tenant selector does not match the authenticated tenant.");
        }
        return contextTenantId;
    }
}
