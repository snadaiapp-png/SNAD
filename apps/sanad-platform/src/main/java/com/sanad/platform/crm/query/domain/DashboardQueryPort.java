package com.sanad.platform.crm.query.domain;

import java.util.UUID;

/**
 * Read-only dashboard query port.
 * Returns typed KPIs — never writes.
 */
public interface DashboardQueryPort {
    DashboardKpisView getDashboardKpis(UUID tenantId);

    record DashboardKpisView(
            long totalAccounts,
            long activeAccounts,
            long totalContacts,
            long totalLeads,
            long openLeads,
            long totalOpportunities,
            long openOpportunities,
            long totalActivities,
            long openActivities,
            java.math.BigDecimal totalPipelineAmount) {}
}
