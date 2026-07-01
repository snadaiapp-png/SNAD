package com.sanad.platform.security.tenant;

import java.util.Optional;

/**
 * Stage 04 §7 — Central provider for the current {@link TenantContext}.
 *
 * <p>Implementations may store the context in a request-scoped bean, a
 * ThreadLocal, or any other mechanism that satisfies the lifecycle rules
 * in Stage 04 §8:</p>
 * <ul>
 *   <li>Created AFTER authentication.</li>
 *   <li>Cleared in {@code finally}.</li>
 *   <li>Not propagated to other threads.</li>
 *   <li>Not shared between requests.</li>
 * </ul>
 *
 * <p>Services MUST NOT extract the tenantId directly from request
 * parameters, headers, request bodies, or unverified JWT claims. They
 * MUST go through this provider.</p>
 */
public interface TenantContextProvider {

    /**
     * Returns the current {@link TenantContext}, throwing if none is set.
     *
     * @throws com.sanad.platform.shared.api.exceptions.TenantContextException
     *         if no tenant context is established for the current request
     */
    TenantContext requireContext();

    /**
     * Returns the current {@link TenantContext} as an Optional, or
     * {@link Optional#empty()} if none is set.
     */
    Optional<TenantContext> currentContext();

    /**
     * Sets the current {@link TenantContext} for the active request/thread.
     * Called by {@link TenantContextFilter} after authentication succeeds.
     */
    void setContext(TenantContext context);

    /**
     * Clears the current {@link TenantContext}. Called by
     * {@link TenantContextFilter} in a {@code finally} block.
     */
    void clear();
}
