package com.sanad.platform.management.application;

import com.sanad.platform.module.entitlement.EntitlementResolver;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Adapter wrapping {@link WorkflowSystemHealthService} for the governance contract (GAP 24). */
@Service
public class WorkflowGovernanceModuleAdapter implements ManagementGovernanceModuleContract {

    private final WorkflowSystemHealthService workflowService;
    private final EntitlementResolver entitlementResolver;

    public WorkflowGovernanceModuleAdapter(
            @Lazy WorkflowSystemHealthService workflowService,
            @Lazy EntitlementResolver entitlementResolver) {
        this.workflowService = workflowService;
        this.entitlementResolver = entitlementResolver;
    }

    @Override public String moduleCode() { return "WORKFLOW"; }
    @Override public String displayName() { return "Workflow Engine"; }

    @Override
    public boolean isEnabled(UUID tenantId) {
        try { return entitlementResolver.isModuleEnabled(tenantId, "WORKFLOW"); }
        catch (Exception e) { return false; }
    }

    @Override
    public ModuleHealthStatus healthStatus(UUID tenantId) {
        try {
            Map<String, Object> health = workflowService.getWorkflowHealth(tenantId);
            String status = String.valueOf(health.getOrDefault("status", "HEALTHY"));
            return switch (status) {
                case "HEALTHY" -> ModuleHealthStatus.HEALTHY;
                case "DEGRADED" -> ModuleHealthStatus.DEGRADED;
                case "UNHEALTHY" -> ModuleHealthStatus.UNHEALTHY;
                default -> ModuleHealthStatus.UNAVAILABLE;
            };
        } catch (Exception e) {
            return ModuleHealthStatus.UNAVAILABLE;
        }
    }

    @Override
    public List<String> capabilities(UUID tenantId) {
        return List.of("WORKFLOW.VIEW", "WORKFLOW.WRITE", "WORKFLOW.ADMIN", "WORKFLOW.APPROVE");
    }

    @Override
    public Map<String, Object> kpiSummary(UUID tenantId) {
        try { return workflowService.getWorkflowHealth(tenantId); }
        catch (Exception e) { return Map.of("_error", e.getClass().getSimpleName()); }
    }

    @Override
    public Map<String, Object> operationalSummary(UUID tenantId) {
        try { return workflowService.getWorkflowHealth(tenantId); }
        catch (Exception e) { return Map.of("_error", e.getClass().getSimpleName()); }
    }

    @Override
    public int openAlertsCount(UUID tenantId) {
        try {
            Map<String, Object> health = workflowService.getWorkflowHealth(tenantId);
            Object slaBreaches = health.get("slaBreaches");
            return slaBreaches instanceof Number ? ((Number) slaBreaches).intValue() : 0;
        } catch (Exception e) { return 0; }
    }

    @Override public int openRisksCount(UUID tenantId) { return 0; }
    @Override public int openIssuesCount(UUID tenantId) { return 0; }

    @Override
    public SlaState slaState(UUID tenantId) {
        try {
            Map<String, Object> health = workflowService.getWorkflowHealth(tenantId);
            Object slaBreaches = health.get("slaBreaches");
            int breaches = slaBreaches instanceof Number ? ((Number) slaBreaches).intValue() : 0;
            if (breaches > 0) return SlaState.BREACHED;
            return SlaState.OK;
        } catch (Exception e) {
            return SlaState.NOT_APPLICABLE;
        }
    }

    @Override
    public Map<String, Object> metadata() {
        return Map.of("version", "1.0", "maturity", "GA", "since", "V20260815_10");
    }
}
