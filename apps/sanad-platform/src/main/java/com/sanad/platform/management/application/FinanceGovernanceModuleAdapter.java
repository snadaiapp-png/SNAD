package com.sanad.platform.management.application;

import com.sanad.platform.module.entitlement.EntitlementResolver;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Adapter wrapping {@link FinanceManagementIntegrationService} for the governance contract (GAP 24). */
@Service
public class FinanceGovernanceModuleAdapter implements ManagementGovernanceModuleContract {

    private final FinanceManagementIntegrationService financeService;
    private final EntitlementResolver entitlementResolver;

    public FinanceGovernanceModuleAdapter(
            @Lazy FinanceManagementIntegrationService financeService,
            @Lazy EntitlementResolver entitlementResolver) {
        this.financeService = financeService;
        this.entitlementResolver = entitlementResolver;
    }

    @Override public String moduleCode() { return "FINANCE"; }
    @Override public String displayName() { return "Finance Management"; }

    @Override
    public boolean isEnabled(UUID tenantId) {
        try { return entitlementResolver.isModuleEnabled(tenantId, "FINANCE"); }
        catch (Exception e) { return false; }
    }

    @Override
    public ModuleHealthStatus healthStatus(UUID tenantId) {
        try { financeService.getOverview(tenantId); return ModuleHealthStatus.HEALTHY; }
        catch (Exception e) { return ModuleHealthStatus.UNAVAILABLE; }
    }

    @Override
    public List<String> capabilities(UUID tenantId) {
        return List.of("FINANCE.VIEW", "FINANCE.WRITE", "FINANCE.ADMIN", "FINANCE.APPROVE");
    }

    @Override
    public Map<String, Object> kpiSummary(UUID tenantId) {
        try { return financeService.getOverview(tenantId); }
        catch (Exception e) { return Map.of("_error", e.getClass().getSimpleName()); }
    }

    @Override
    public Map<String, Object> operationalSummary(UUID tenantId) {
        try {
            Map<String, Object> o = financeService.getOverview(tenantId);
            return Map.of(
                    "invoiceTotalValue", o.getOrDefault("invoiceTotalValue", 0),
                    "collectedRevenue", o.getOrDefault("collectedRevenue", 0),
                    "outstandingAmount", o.getOrDefault("outstandingAmount", 0)
            );
        } catch (Exception e) { return Map.of("_error", e.getClass().getSimpleName()); }
    }

    @Override public int openAlertsCount(UUID tenantId) { return 0; }
    @Override public int openRisksCount(UUID tenantId) { return 0; }
    @Override public int openIssuesCount(UUID tenantId) { return 0; }
    @Override public SlaState slaState(UUID tenantId) { return SlaState.NOT_APPLICABLE; }
    @Override public Map<String, Object> metadata() {
        return Map.of("version", "1.0", "maturity", "GA", "since", "V20260815_16");
    }
}
