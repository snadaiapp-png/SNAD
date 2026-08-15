package com.sanad.platform.website.application;

import com.sanad.platform.management.health.SystemHealthContributor;
import com.sanad.platform.management.health.SystemHealthModel;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Website System Health Contributor (v20260816.3).
 *
 * Auto-discovers via SystemHealthContributorRegistry (Spring List injection).
 * No modification to SystemHealthAggregationService needed.
 */
@Component
public class WebsiteSystemHealthContributor implements SystemHealthContributor {

    private final JdbcTemplate jdbc;

    public WebsiteSystemHealthContributor(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override public String componentId() { return "websites"; }
    @Override public String componentType() { return "MODULE"; }
    @Override public String displayName() { return "Website Platform"; }

    @Override
    public SystemHealthModel.SystemHealthComponent checkHealth(UUID tenantId) {
        long start = System.currentTimeMillis();
        Map<String, Object> details = new LinkedHashMap<>();
        try {
            Integer totalWebsites = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM websites WHERE tenant_id = ?", Integer.class, tenantId);
            Integer activeWebsites = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM websites WHERE tenant_id = ? AND status = 'ACTIVE'", Integer.class, tenantId);
            Integer publishedPages = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM website_pages WHERE tenant_id = ? AND status = 'PUBLISHED'", Integer.class, tenantId);
            Integer activeDomains = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM website_domains WHERE tenant_id = ? AND activation_status = 'ACTIVE'", Integer.class, tenantId);
            details.put("totalWebsites", totalWebsites != null ? totalWebsites : 0);
            details.put("activeWebsites", activeWebsites != null ? activeWebsites : 0);
            details.put("publishedPages", publishedPages != null ? publishedPages : 0);
            details.put("activeDomains", activeDomains != null ? activeDomains : 0);
            long latency = System.currentTimeMillis() - start;
            return new SystemHealthModel.SystemHealthComponent(
                    componentId(), componentType(), displayName(),
                    SystemHealthModel.SystemHealthStatus.HEALTHY,
                    "Website platform operational",
                    Instant.now(), latency, details, Instant.now(), null,
                    SystemHealthModel.SystemHealthComponent.Severity.INFO);
        } catch (Exception e) {
            long latency = System.currentTimeMillis() - start;
            return new SystemHealthModel.SystemHealthComponent(
                    componentId(), componentType(), displayName(),
                    SystemHealthModel.SystemHealthStatus.UNHEALTHY,
                    "Website health check failed: " + e.getClass().getSimpleName(),
                    Instant.now(), latency, Map.of(), null, "WEBSITE_CHECK_FAILED",
                    SystemHealthModel.SystemHealthComponent.Severity.ERROR);
        }
    }
}
