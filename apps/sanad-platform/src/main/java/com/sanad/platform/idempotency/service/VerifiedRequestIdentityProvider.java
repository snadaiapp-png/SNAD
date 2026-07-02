package com.sanad.platform.idempotency.service;

import com.sanad.platform.shared.api.RequestIdFilter;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Stage 05A.2.6 §4 — Provides verified request identity BEFORE any
 * transaction is started.
 *
 * <p>Priority:</p>
 * <ol>
 *   <li>MDC request ID (set by RequestIdFilter)</li>
 *   <li>Verified TenantContext.requestId</li>
 *   <li>Explicit background-job execution ID</li>
 * </ol>
 *
 * <p>If no identity is found, throws {@link RequestIdentityMissingException}.</p>
 */
@Component
public class VerifiedRequestIdentityProvider {

    private final com.sanad.platform.security.tenant.TenantContextProvider contextProvider;

    public VerifiedRequestIdentityProvider(
            com.sanad.platform.security.tenant.TenantContextProvider contextProvider) {
        this.contextProvider = contextProvider;
    }

    /**
     * Returns the verified request identity. Never null.
     *
     * @throws RequestIdentityMissingException if no identity is found
     */
    public String requireCurrent() {
        // Priority 1: MDC request ID
        String mdcRequestId = MDC.get(RequestIdFilter.MDC_KEY);
        if (mdcRequestId != null && !mdcRequestId.isBlank()) {
            return mdcRequestId;
        }

        // Priority 2: TenantContext.requestId
        var ctx = contextProvider.currentContext().orElse(null);
        if (ctx != null && ctx.requestId() != null && !ctx.requestId().isBlank()) {
            return ctx.requestId();
        }

        throw new RequestIdentityMissingException(
                "No verified request identity found. MDC request ID is null and "
                + "TenantContext has no requestId. Cannot reserve or complete "
                + "idempotency without a verified identity.");
    }

    /**
     * Returns the verified request identity, or a generated background-job ID
     * if no HTTP request context is active. Used by background jobs.
     */
    public String requireCurrentOrBackground(String backgroundJobName) {
        try {
            return requireCurrent();
        } catch (RequestIdentityMissingException e) {
            return "background-" + backgroundJobName + "-" + UUID.randomUUID();
        }
    }
}
