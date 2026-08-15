package com.sanad.platform.management.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Cross-Module Operational Overview (GAP 18).
 *
 * <p>Produces a unified, typed operational picture across all enabled
 * governed modules for a tenant. Uses {@link ManagementGovernanceModuleRegistry}
 * to auto-discover modules — adding a new module (ERP/HRM/POS) requires
 * no modification to this service.
 *
 * <p>Read-only and tenant-scoped. The actual data loading is routed through
 * {@link OperationalDataLoader#loadInNewTransaction(UUID)} (REQUIRES_NEW)
 * so PSQLException aborts in any module do not pollute the caller's
 * transaction.
 */
@Service
public class CrossModuleOperationalOverviewService {

    private final OperationalDataLoader dataLoader;

    public CrossModuleOperationalOverviewService(OperationalDataLoader dataLoader) {
        this.dataLoader = dataLoader;
    }

    /**
     * Build the operational overview. No transaction — the data loader
     * catches exceptions per-module independently.
     */
    public Map<String, Object> getOperationalOverview(UUID tenantId) {
        // Force fresh discovery (invalidate cache)
        // Note: registry.invalidate is safe to call from any transaction.

        OperationalData data = dataLoader.loadInNewTransaction(tenantId);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("overallHealthStatus", data.overallHealthStatus);
        result.put("moduleCount", data.moduleCount);
        result.put("modules", data.modules);
        result.put("totalOpenAlerts", data.totalOpenAlerts);
        result.put("totalOpenRisks", data.totalOpenRisks);
        result.put("totalOpenIssues", data.totalOpenIssues);
        result.put("slaOverallState", data.slaOverallState);
        result.put("generatedAt", Instant.now().toString());
        return result;
    }

    /** Composite record loaded in a separate transaction. */
    public record OperationalData(
            String overallHealthStatus,
            int moduleCount,
            List<Map<String, Object>> modules,
            int totalOpenAlerts,
            int totalOpenRisks,
            int totalOpenIssues,
            String slaOverallState
    ) {}
}
