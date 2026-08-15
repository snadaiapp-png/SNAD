package com.sanad.platform.management.application;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Cross-Module Operational Overview (GAP 18) — REAL data, no placeholders.
 *
 * <p>Produces a unified, typed operational picture across all enabled
 * governed modules for a tenant. Uses {@link ManagementGovernanceModuleRegistry}
 * to auto-discover modules — adding a new module (ERP/HRM/POS) requires
 * no modification to this service.
 *
 * <p>ARCHITECTURE (v20260815.9):
 * <pre>
 *   CrossModuleOperationalOverviewService.getOperationalOverview(tenantId)  [no @Transactional]
 *       │
 *       └── OperationalDataLoader.load(tenantId)  [@Transactional(REQUIRES_NEW)]
 *             → calls ManagementGovernanceModuleRegistry.compositeSummary
 *             → each adapter method is independently resilient
 *             → if exception: propagates (NOT caught inside the REQUIRES_NEW method)
 * </pre>
 *
 * <p>The loader runs in its own REQUIRES_NEW transaction so any SQL error
 * in one module's adapter does not poison the caller's transaction.
 */
@Service
public class CrossModuleOperationalOverviewService {

    private final OperationalDataLoader dataLoader;

    public CrossModuleOperationalOverviewService(@Lazy OperationalDataLoader dataLoader) {
        this.dataLoader = dataLoader;
    }

    /**
     * Build the operational overview. Delegates to the loader (REQUIRES_NEW)
     * so failures do not pollute the caller's transaction.
     */
    public Map<String, Object> getOperationalOverview(UUID tenantId) {
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            var data = dataLoader.load(tenantId);
            result.put("overallHealthStatus", data.overallHealthStatus());
            result.put("moduleCount", data.moduleCount());
            result.put("modules", data.modules());
            result.put("totalOpenAlerts", data.totalOpenAlerts());
            result.put("totalOpenRisks", data.totalOpenRisks());
            result.put("totalOpenIssues", data.totalOpenIssues());
            result.put("slaOverallState", data.slaOverallState());
        } catch (Exception e) {
            // The REQUIRES_NEW transaction was rolled back — degrade gracefully
            result.put("overallHealthStatus", "UNAVAILABLE");
            result.put("moduleCount", 0);
            result.put("modules", List.of());
            result.put("totalOpenAlerts", 0);
            result.put("totalOpenRisks", 0);
            result.put("totalOpenIssues", 0);
            result.put("slaOverallState", "NOT_APPLICABLE");
            result.put("_error", e.getClass().getSimpleName());
        }
        result.put("generatedAt", Instant.now().toString());
        return result;
    }
}
