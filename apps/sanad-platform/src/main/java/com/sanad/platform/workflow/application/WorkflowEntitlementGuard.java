package com.sanad.platform.workflow.application;

import com.sanad.platform.module.entitlement.EntitlementResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * R1 GATE R1.3 — THE authoritative Workflow product-access guard.
 *
 * <p>Single point through which Workflow's NEW PRODUCT USE (design, publish,
 * start, trigger consumption) is gated on the tenant's paid entitlement.
 * Delegates exclusively to the central {@link EntitlementResolver}; no
 * parallel licensing store, no direct subscription-table reads, no
 * controller-side subscription SQL.</p>
 *
 * <p>Fail-closed contract (R1.2): any resolver failure, unknown/blank module
 * or absent entitlement denies Workflow. Backend is authoritative — web
 * visibility of Workflow surfaces is presentation only.</p>
 *
 * <p>Enforcement granularity follows the R1 removal policy
 * (R1.6 / DRAIN_EXISTING): DESIGN + START + TRIGGER paths require an active
 * entitlement; MUTATE_RUNTIME drain operations on already-running instances
 * and GOVERNED HISTORICAL ACCESS reads stay governed by RBAC alone so a
 * downgrade never kills an in-flight business process or erases history.</p>
 */
@Service
public class WorkflowEntitlementGuard {

    /** Deny reason surfaced to clients and audit (stable machine code). */
    public static final String DENIED_REASON = "WORKFLOW_MODULE_NOT_ENTITLED";

    private static final Logger log = LoggerFactory.getLogger(WorkflowEntitlementGuard.class);

    private final EntitlementResolver entitlementResolver;

    public WorkflowEntitlementGuard(EntitlementResolver entitlementResolver) {
        this.entitlementResolver = entitlementResolver;
    }

    /**
     * Whether the tenant currently holds an explicit paid Workflow entitlement.
     * ANY failure resolves to {@code false} (fail closed).
     */
    public boolean isWorkflowEnabled(UUID tenantId) {
        if (tenantId == null) return false;
        try {
            return entitlementResolver.isModuleEnabled(tenantId, "WORKFLOW");
        } catch (Exception e) {
            log.warn("Workflow entitlement resolution failed for tenant {} -> denied: {}",
                    tenantId, e.getMessage());
            return false;
        }
    }

    /**
     * Require an active paid Workflow entitlement for NEW PRODUCT USE.
     * Throws the platform's standard {@link org.springframework.security.access.AccessDeniedException}
     * (mapped to HTTP 403 by the Workflow API) when the tenant has none.
     */
    public void requireWorkflowEnabled(UUID tenantId) {
        if (!isWorkflowEnabled(tenantId)) {
            throw new org.springframework.security.access.AccessDeniedException(
                    DENIED_REASON + ": paid Workflow entitlement is not active for this tenant");
        }
    }
}
