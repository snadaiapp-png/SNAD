package com.sanad.platform.workflow.integration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * R1 GATE R1.8 — Workflow module integration registry.
 *
 * <p>Spring bean discovery (pattern aligned with
 * {@code WorkflowSystemActionAdapterRegistry}): every bean implementing
 * {@link WorkflowModuleIntegrationContract} is registered at startup with
 * zero Workflow-core edits (R1.9 future-module proof).</p>
 *
 * <p>Fail-closed rules:</p>
 * <ul>
 *   <li>{@code AUTO_DISCOVERY=TRUE} — beans are found, never hand-registered.</li>
 *   <li>{@code DETERMINISTIC_REGISTRATION=TRUE} — stable sorted order.</li>
 *   <li>{@code MODULE_CODE_CASE_NORMALIZED=TRUE} — codes upper-cased.</li>
 *   <li>Blank/null module code → startup failure.</li>
 *   <li>{@code DUPLICATE_MODULE_CODE} → startup/configuration failure (no silent override).</li>
 * </ul>
 */
@Component
public class WorkflowModuleIntegrationRegistry {

    public enum WorkflowReadyStatus {
        REGISTERED_AND_WORKFLOW_READY,
        REGISTERED_NO_WORKFLOW_CONTRACT,
        DISABLED_FOR_TENANT,
        UNAVAILABLE
    }

    private static final Logger log = LoggerFactory.getLogger(WorkflowModuleIntegrationRegistry.class);

    private final Map<String, WorkflowModuleIntegrationContract> byModuleCode = new LinkedHashMap<>();

    public WorkflowModuleIntegrationRegistry(List<WorkflowModuleIntegrationContract> contracts) {
        for (WorkflowModuleIntegrationContract contract : contracts) {
            String code = normalize(contract.moduleCode());
            if (code == null) {
                throw new IllegalStateException(
                        "WorkflowModuleIntegrationContract with blank moduleCode refused at startup");
            }
            WorkflowModuleIntegrationContract existing = byModuleCode.get(code);
            if (existing != null) {
                throw new IllegalStateException(
                        "Duplicate Workflow module integration contract for module code '" + code
                                + "' (" + existing.getClass().getName() + " vs "
                                + contract.getClass().getName() + ") — fail startup, no silent override");
            }
            byModuleCode.put(code, contract);
        }
        log.info("Workflow module integration registry initialized with {} contract(s): {}",
                byModuleCode.size(), byModuleCode.keySet());
    }

    private static String normalize(String moduleCode) {
        if (moduleCode == null || moduleCode.isBlank()) return null;
        return moduleCode.trim().toUpperCase(Locale.ROOT);
    }

    /** Deterministic (sorted by module code) immutable view of all contracts. */
    public Collection<WorkflowModuleIntegrationContract> list() {
        return byModuleCode.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(Map.Entry::getValue)
                .toList();
    }

    public Optional<WorkflowModuleIntegrationContract> find(String moduleCode) {
        String code = normalize(moduleCode);
        return code == null ? Optional.empty() : Optional.ofNullable(byModuleCode.get(code));
    }

    /** Fail-closed: unknown module has no Workflow integration. */
    public WorkflowModuleIntegrationContract require(String moduleCode) {
        return find(moduleCode).orElseThrow(() ->
                new IllegalArgumentException("No Workflow module integration contract for module code '"
                        + moduleCode + "'"));
    }

    public boolean isRegistered(String moduleCode) {
        return find(moduleCode).isPresent();
    }

    /**
     * Workflow-ready classification combining the GLOBAL MODULE REGISTRY
     * (catalog registration), the WORKFLOW INTEGRATION REGISTRY (contract
     * presence) and TENANT ENTITLEMENT. These states are deliberately NOT
     * equivalent (R1.8).
     *
     * @param moduleGloballyRegistered whether the module catalog knows the module
     * @param moduleEnabledForTenant   whether the tenant holds the source-module entitlement
     */
    public WorkflowReadyStatus classify(String moduleCode, boolean moduleGloballyRegistered,
                                        boolean moduleEnabledForTenant) {
        boolean hasContract = isRegistered(moduleCode);
        if (hasContract && moduleGloballyRegistered && moduleEnabledForTenant) {
            return WorkflowReadyStatus.REGISTERED_AND_WORKFLOW_READY;
        }
        if (hasContract && moduleGloballyRegistered && !moduleEnabledForTenant) {
            return WorkflowReadyStatus.DISABLED_FOR_TENANT;
        }
        if (moduleGloballyRegistered) {
            return WorkflowReadyStatus.REGISTERED_NO_WORKFLOW_CONTRACT;
        }
        return WorkflowReadyStatus.UNAVAILABLE;
    }
}
