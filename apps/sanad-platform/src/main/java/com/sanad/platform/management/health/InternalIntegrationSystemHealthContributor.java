package com.sanad.platform.management.health;

import com.sanad.platform.management.application.CrmManagementIntegrationService;
import com.sanad.platform.management.application.FinanceManagementIntegrationService;
import com.sanad.platform.management.application.AnalyticsManagementIntegrationService;
import com.sanad.platform.management.application.WorkflowSystemHealthService;
import com.sanad.platform.management.application.ModuleGovernanceService;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Internal Integration health contributor (v20260816.1).
 *
 * <p>Validates the governance/integration wiring between modules and Senior
 * Management. Does NOT invoke destructive operations — only checks that each
 * integration service bean is wired and can produce a basic response.
 */
@Component
public class InternalIntegrationSystemHealthContributor implements SystemHealthContributor {

    private final CrmManagementIntegrationService crmService;
    private final FinanceManagementIntegrationService financeService;
    private final AnalyticsManagementIntegrationService analyticsService;
    private final WorkflowSystemHealthService workflowService;
    private final ModuleGovernanceService moduleGovernanceService;

    public InternalIntegrationSystemHealthContributor(
            @Lazy CrmManagementIntegrationService crmService,
            @Lazy FinanceManagementIntegrationService financeService,
            @Lazy AnalyticsManagementIntegrationService analyticsService,
            @Lazy WorkflowSystemHealthService workflowService,
            @Lazy ModuleGovernanceService moduleGovernanceService) {
        this.crmService = crmService;
        this.financeService = financeService;
        this.analyticsService = analyticsService;
        this.workflowService = workflowService;
        this.moduleGovernanceService = moduleGovernanceService;
    }

    @Override public String componentId() { return "integrations"; }
    @Override public String componentType() { return "OPERATIONS"; }
    @Override public String displayName() { return "Internal Integrations"; }

    @Override
    public SystemHealthModel.SystemHealthComponent checkHealth(UUID tenantId) {
        long start = System.currentTimeMillis();
        Map<String, Object> details = new LinkedHashMap<>();
        int healthyCount = 0;
        int total = 5;
        String failureCode = null;

        // Check each integration — each is independently try/caught
        try { crmService.getCrmOverview(tenantId); healthyCount++; details.put("crm", "OK"); }
        catch (Exception e) { details.put("crm", "FAILED: " + e.getClass().getSimpleName()); failureCode = "CRM_INTEGRATION_FAILED"; }

        try { financeService.getOverview(tenantId); healthyCount++; details.put("finance", "OK"); }
        catch (Exception e) { details.put("finance", "FAILED: " + e.getClass().getSimpleName()); if (failureCode == null) failureCode = "FINANCE_INTEGRATION_FAILED"; }

        try { analyticsService.getAnalyticsOverview(tenantId); healthyCount++; details.put("analytics", "OK"); }
        catch (Exception e) { details.put("analytics", "FAILED: " + e.getClass().getSimpleName()); if (failureCode == null) failureCode = "ANALYTICS_INTEGRATION_FAILED"; }

        try { workflowService.getWorkflowHealth(tenantId); healthyCount++; details.put("workflow", "OK"); }
        catch (Exception e) { details.put("workflow", "FAILED: " + e.getClass().getSimpleName()); if (failureCode == null) failureCode = "WORKFLOW_INTEGRATION_FAILED"; }

        try { moduleGovernanceService.getModuleStatuses(); healthyCount++; details.put("moduleRegistry", "OK"); }
        catch (Exception e) { details.put("moduleRegistry", "FAILED: " + e.getClass().getSimpleName()); if (failureCode == null) failureCode = "REGISTRY_INTEGRATION_FAILED"; }

        long latency = System.currentTimeMillis() - start;
        details.put("healthyIntegrations", healthyCount);
        details.put("totalIntegrations", total);

        SystemHealthModel.SystemHealthStatus status;
        String message;
        var severity = SystemHealthModel.SystemHealthComponent.Severity.INFO;
        if (healthyCount == total) {
            status = SystemHealthModel.SystemHealthStatus.HEALTHY;
            message = "All integrations operational";
        } else if (healthyCount == 0) {
            status = SystemHealthModel.SystemHealthStatus.UNHEALTHY;
            message = "All integrations failed";
            severity = SystemHealthModel.SystemHealthComponent.Severity.ERROR;
        } else {
            status = SystemHealthModel.SystemHealthStatus.DEGRADED;
            message = healthyCount + "/" + total + " integrations operational";
            severity = SystemHealthModel.SystemHealthComponent.Severity.WARN;
        }
        return new SystemHealthModel.SystemHealthComponent(
                componentId(), componentType(), displayName(), status, message,
                Instant.now(), latency, details, Instant.now(), failureCode, severity);
    }
}
