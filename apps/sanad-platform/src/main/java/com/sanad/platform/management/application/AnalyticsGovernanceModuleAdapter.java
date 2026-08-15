package com.sanad.platform.management.application;

import com.sanad.platform.module.entitlement.EntitlementResolver;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Adapter wrapping {@link AnalyticsManagementIntegrationService} for the governance contract (GAP 24). */
@Service
public class AnalyticsGovernanceModuleAdapter implements ManagementGovernanceModuleContract {

    private final AnalyticsManagementIntegrationService analyticsService;
    private final EntitlementResolver entitlementResolver;

    public AnalyticsGovernanceModuleAdapter(
            @Lazy AnalyticsManagementIntegrationService analyticsService,
            @Lazy EntitlementResolver entitlementResolver) {
        this.analyticsService = analyticsService;
        this.entitlementResolver = entitlementResolver;
    }

    @Override public String moduleCode() { return "ANALYTICS"; }
    @Override public String displayName() { return "Analytics Platform"; }

    @Override
    public boolean isEnabled(UUID tenantId) {
        try { return entitlementResolver.isModuleEnabled(tenantId, "ANALYTICS"); }
        catch (Exception e) { return false; }
    }

    @Override
    public ModuleHealthStatus healthStatus(UUID tenantId) {
        try { analyticsService.getAnalyticsOverview(tenantId); return ModuleHealthStatus.HEALTHY; }
        catch (Exception e) { return ModuleHealthStatus.UNAVAILABLE; }
    }

    @Override
    public List<String> capabilities(UUID tenantId) {
        return List.of("ANALYTICS.VIEW", "ANALYTICS.WRITE", "ANALYTICS.ADMIN", "ANALYTICS.EXECUTE");
    }

    @Override
    public Map<String, Object> kpiSummary(UUID tenantId) {
        try { return analyticsService.getAnalyticsOverview(tenantId); }
        catch (Exception e) { return Map.of("_error", e.getClass().getSimpleName()); }
    }

    @Override
    public Map<String, Object> operationalSummary(UUID tenantId) {
        try { return analyticsService.getAnalyticsOverview(tenantId); }
        catch (Exception e) { return Map.of("_error", e.getClass().getSimpleName()); }
    }

    @Override public int openAlertsCount(UUID tenantId) { return 0; }
    @Override public int openRisksCount(UUID tenantId) { return 0; }
    @Override public int openIssuesCount(UUID tenantId) { return 0; }
    @Override public SlaState slaState(UUID tenantId) { return SlaState.NOT_APPLICABLE; }
    @Override public Map<String, Object> metadata() {
        return Map.of("version", "1.0", "maturity", "GA", "since", "V20260815_18");
    }
}
