package com.sanad.platform.workflow.application;

import com.sanad.platform.module.entitlement.EntitlementResolver;
import com.sanad.platform.module.registry.ModuleRepository;
import com.sanad.platform.workflow.integration.WorkflowModuleIntegrationContract;
import com.sanad.platform.workflow.integration.WorkflowModuleIntegrationRegistry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * R1 GATE R1.10 — authoritative backend Designer/Definitions module catalog.
 *
 * <p>Replaces the R0 static production module list. The effective catalog
 * derives from the GLOBAL MODULE REGISTRY (catalog registration) + the
 * WORKFLOW MODULE INTEGRATION REGISTRY (contract presence) + the TENANT'S
 * WORKFLOW ENTITLEMENT + the TENANT'S SOURCE-MODULE ENTITLEMENTS. The
 * frontend consumes metadata only and never duplicates module business
 * rules.</p>
 *
 * <p>Classification per module (states are NOT equivalent — R1.8):
 * REGISTERED_AND_WORKFLOW_READY modules are actionable Designer choices;
 * REGISTERED_NO_WORKFLOW_CONTRACT modules are exposed only as non-actionable
 * registry facts; DISABLED_FOR_TENANT modules are excluded from actionable
 * choices for that tenant; UNAVAILABLE modules never appear.</p>
 */
@Service
public class WorkflowModuleCatalogService {

    private final WorkflowModuleIntegrationRegistry integrationRegistry;
    private final ModuleRepository moduleRepository;
    private final EntitlementResolver entitlementResolver;
    private final WorkflowEntitlementGuard workflowEntitlementGuard;

    public WorkflowModuleCatalogService(WorkflowModuleIntegrationRegistry integrationRegistry,
                                        ModuleRepository moduleRepository,
                                        EntitlementResolver entitlementResolver,
                                        WorkflowEntitlementGuard workflowEntitlementGuard) {
        this.integrationRegistry = integrationRegistry;
        this.moduleRepository = moduleRepository;
        this.entitlementResolver = entitlementResolver;
        this.workflowEntitlementGuard = workflowEntitlementGuard;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> effectiveCatalog(UUID tenantId) {
        // Designer catalog serves definition design (NEW PRODUCT USE): require
        // the tenant's paid Workflow entitlement (fail closed, R1.4).
        workflowEntitlementGuard.requireWorkflowEnabled(tenantId);

        List<Map<String, Object>> modules = integrationRegistry.list().stream()
                .map(contract -> toCatalogEntry(tenantId, contract))
                .toList();

        boolean workflowEnabled = true; // guard passed above
        return Map.of(
                "workflowEnabled", workflowEnabled,
                "modules", modules,
                "generatedAt", java.time.Instant.now().toString());
    }

    private Map<String, Object> toCatalogEntry(UUID tenantId, WorkflowModuleIntegrationContract contract) {
        String code = contract.moduleCode();
        boolean globallyRegistered = moduleRepository.findByCode(code).isPresent();
        boolean tenantEntitled;
        try {
            tenantEntitled = entitlementResolver.isModuleEnabled(tenantId, code);
        } catch (Exception e) {
            tenantEntitled = false; // fail closed per module
        }
        WorkflowModuleIntegrationRegistry.WorkflowReadyStatus status =
                integrationRegistry.classify(code, globallyRegistered, tenantEntitled);

        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("moduleCode", code);
        entry.put("displayName", contract.displayName());
        entry.put("moduleVersion", contract.moduleVersion());
        entry.put("status", status.name());
        entry.put("actionable", status == WorkflowModuleIntegrationRegistry.WorkflowReadyStatus.REGISTERED_AND_WORKFLOW_READY);
        entry.put("entities", descriptors(contract.entities()));
        entry.put("events", descriptors(contract.events()));
        entry.put("triggers", descriptors(contract.triggers()));
        entry.put("actions", descriptors(contract.actions()));
        entry.put("queries", descriptors(contract.queries()));
        entry.put("capabilities", descriptors(contract.capabilities()));
        entry.put("deepLinks", descriptors(contract.deepLinks()));
        entry.put("attachmentCapabilities", descriptors(contract.attachmentCapabilities()));
        return entry;
    }

    private static List<Map<String, Object>> descriptors(Collection<?> collection) {
        // Records serialize via their accessors; map them into ordered maps so
        // the JSON surface stays stable and explicit.
        return collection.stream()
                .map(d -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    for (var component : d.getClass().getRecordComponents()) {
                        try {
                            m.put(component.getName(), component.getAccessor().invoke(d));
                        } catch (Exception ignored) {
                            m.put(component.getName(), null);
                        }
                    }
                    return m;
                })
                .toList();
    }
}
