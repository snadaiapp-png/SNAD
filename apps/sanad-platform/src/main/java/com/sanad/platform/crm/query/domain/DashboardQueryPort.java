package com.sanad.platform.crm.query.domain;

import java.util.Map;
import java.util.UUID;

/**
 * Read-only dashboard query port.
 * Returns aggregate KPIs for the CRM dashboard.
 */
public interface DashboardQueryPort {
    Map<String, Object> getDashboardKpis(UUID tenantId);
}
