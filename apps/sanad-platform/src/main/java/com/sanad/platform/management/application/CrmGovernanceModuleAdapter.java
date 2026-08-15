package com.sanad.platform.management.application;

import com.sanad.platform.module.entitlement.EntitlementResolver;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Adapter that exposes the existing {@link CrmManagementIntegrationService}
 * through the {@link ManagementGovernanceModuleContract} interface (GAP 24).
 *
 * <p>Wraps the existing service without modifying it. All cross-tenant
 * safety is provided by the wrapped service (which uses
 * {@code WHERE tenant_id = ?} filtering).
 *
 * <p>This pattern lets the existing CRM module become governed by
 * Senior Management without any rewrite. ERP/HRM/POS will follow the
 * same pattern when they are implemented.
 */
@Service
public class CrmGovernanceModuleAdapter implements ManagementGovernanceModuleContract {

    private final CrmManagementIntegrationService crmService;
    private final EntitlementResolver entitlementResolver;

    public CrmGovernanceModuleAdapter(
            @Lazy CrmManagementIntegrationService crmService,
            @Lazy EntitlementResolver entitlementResolver) {
        this.crmService = crmService;
        this.entitlementResolver = entitlementResolver;
    }

    @Override
    public String moduleCode() { return "CRM"; }

    @Override
    public String displayName() { return "Customer Relationship Management"; }

    @Override
    public boolean isEnabled(UUID tenantId) {
        try {
            return entitlementResolver.isModuleEnabled(tenantId, "CRM");
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public ModuleHealthStatus healthStatus(UUID tenantId) {
        // CRM is considered HEALTHY when its overview loads successfully
        try {
            crmService.getCrmOverview(tenantId);
            return ModuleHealthStatus.HEALTHY;
        } catch (Exception e) {
            return ModuleHealthStatus.UNAVAILABLE;
        }
    }

    @Override
    public List<String> capabilities(UUID tenantId) {
        return List.of("CRM.VIEW", "CRM.WRITE", "CRM.ADMIN", "CRM.APPROVE");
    }

    @Override
    public Map<String, Object> kpiSummary(UUID tenantId) {
        try {
            return crmService.getCrmOverview(tenantId);
        } catch (Exception e) {
            return Map.of("_error", e.getClass().getSimpleName());
        }
    }

    @Override
    public Map<String, Object> operationalSummary(UUID tenantId) {
        try {
            Map<String, Object> overview = crmService.getCrmOverview(tenantId);
            // Extract only the operational subset for the cross-module view
            return Map.of(
                    "totalAccounts", overview.getOrDefault("totalAccounts", 0),
                    "activeAccounts", overview.getOrDefault("activeAccounts", 0),
                    "totalOpportunities", overview.getOrDefault("totalOpportunities", 0),
                    "openOpportunities", overview.getOrDefault("openOpportunities", 0)
            );
        } catch (Exception e) {
            return Map.of("_error", e.getClass().getSimpleName());
        }
    }

    @Override
    public int openAlertsCount(UUID tenantId) {
        // CRM module does not own alerts directly — Senior Management owns them.
        return 0;
    }

    @Override
    public int openRisksCount(UUID tenantId) { return 0; }

    @Override
    public int openIssuesCount(UUID tenantId) { return 0; }

    @Override
    public SlaState slaState(UUID tenantId) {
        return SlaState.NOT_APPLICABLE;
    }

    @Override
    public Map<String, Object> metadata() {
        return Map.of("version", "1.0", "maturity", "GA", "since", "V20260730_1");
    }
}
