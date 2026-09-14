package com.sanad.platform.workflow.application;

import com.sanad.platform.access.evaluation.CapabilityEvaluationService;
import com.sanad.platform.module.entitlement.EntitlementResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * R1 GATES R1.12/R1.13 — domain action authorization at action time.
 *
 * <p>Every module-owned system action execution revalidates:</p>
 * <ol>
 *   <li>tenant binding (adapter input is tenant-scoped by construction),</li>
 *   <li>the OWNING MODULE's entitlement for the tenant (fail closed on any
 *       resolution failure; a module that lost its entitlement cannot have
 *       new domain actions executed against it),</li>
 *   <li>actor authorization for human-triggered actions: assignment is not
 *       authorization — the actor's CURRENT domain capability is revalidated
 *       live (never the capability snapshot captured at task creation),</li>
 *   <li>a null {@code moduleCode} in the action metadata fails closed.</li>
 * </ol>
 *
 * <p>Unknown actions never reach this service —
 * {@link WorkflowSystemActionAdapterRegistry#require} already fails closed.</p>
 */
@Service
public class WorkflowDomainActionAuthorizer {

    private static final Logger log = LoggerFactory.getLogger(WorkflowDomainActionAuthorizer.class);

    private final EntitlementResolver entitlementResolver;
    private final CapabilityEvaluationService capabilityEvaluationService;

    public WorkflowDomainActionAuthorizer(EntitlementResolver entitlementResolver,
                                          CapabilityEvaluationService capabilityEvaluationService) {
        this.entitlementResolver = entitlementResolver;
        this.capabilityEvaluationService = capabilityEvaluationService;
    }

    /**
     * @param adapter     the resolved, registered adapter (never null)
     * @param actorUserId the triggering user, or null for system/platform flows
     * @throws org.springframework.security.access.AccessDeniedException when denied
     */
    public void authorizeExecution(UUID tenantId, WorkflowSystemActionAdapter adapter, UUID actorUserId) {
        WorkflowSystemActionAdapter.ActionMetadata metadata = adapter.metadata();
        String type = adapter.type();

        if (metadata.moduleCode() == null || metadata.moduleCode().isBlank()) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "DOMAIN_ACTION_FAIL_CLOSED: action '" + type + "' declares no owning module");
        }

        // Platform-internal WORKFLOW actions are governed at the Workflow
        // boundaries (design/start/drain policy); module-owned actions must
        // additionally hold the source module's tenant entitlement.
        if (!"WORKFLOW".equals(metadata.moduleCode())) {
            boolean moduleEntitled;
            try {
                moduleEntitled = entitlementResolver.isModuleEnabled(tenantId, metadata.moduleCode());
            } catch (Exception e) {
                log.warn("Domain action {}: source module entitlement resolution failed -> denied: {}",
                        type, e.getMessage());
                moduleEntitled = false;
            }
            if (!moduleEntitled) {
                throw new org.springframework.security.access.AccessDeniedException(
                        "DOMAIN_ACTION_MODULE_DISABLED: source module '" + metadata.moduleCode()
                                + "' is not entitled for this tenant");
            }
        }

        // Assignment != authorization: revalidate the actor's LIVE domain
        // capability (human-triggered actions only).
        if (actorUserId != null && metadata.requiredCapability() != null
                && !metadata.requiredCapability().isBlank()) {
            var decision = capabilityEvaluationService.evaluate(
                    tenantId, actorUserId, metadata.requiredCapability(), null);
            if (decision == null || !decision.allowed()) {
                throw new org.springframework.security.access.AccessDeniedException(
                        "DOMAIN_ACTION_CAPABILITY_DENIED: actor lacks current '"
                                + metadata.requiredCapability() + "' required by action '" + type + "'");
            }
        }
    }
}
