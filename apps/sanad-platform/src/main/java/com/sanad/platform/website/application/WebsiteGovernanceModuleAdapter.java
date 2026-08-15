package com.sanad.platform.website.application;

import com.sanad.platform.management.application.ManagementGovernanceModuleContract;
import com.sanad.platform.module.entitlement.EntitlementResolver;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Website Governance Module Adapter (v20260816.3).
 *
 * Auto-discovers via ManagementGovernanceModuleRegistry (Spring List injection).
 * No modification to Senior Management core needed.
 */
@Service
public class WebsiteGovernanceModuleAdapter implements ManagementGovernanceModuleContract {

    private final JdbcTemplate jdbc;
    private final EntitlementResolver entitlementResolver;

    public WebsiteGovernanceModuleAdapter(JdbcTemplate jdbc,
                                           @Lazy EntitlementResolver entitlementResolver) {
        this.jdbc = jdbc;
        this.entitlementResolver = entitlementResolver;
    }

    @Override public String moduleCode() { return "WEBSITES"; }
    @Override public String displayName() { return "Website Platform"; }

    @Override
    public boolean isEnabled(UUID tenantId) {
        try { return entitlementResolver.isModuleEnabled(tenantId, "WEBSITES"); }
        catch (Exception e) { return false; }
    }

    @Override
    public ModuleHealthStatus healthStatus(UUID tenantId) {
        try {
            Integer count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM websites WHERE tenant_id = ?", Integer.class, tenantId);
            return count != null ? ModuleHealthStatus.HEALTHY : ModuleHealthStatus.UNAVAILABLE;
        } catch (Exception e) { return ModuleHealthStatus.UNAVAILABLE; }
    }

    @Override
    public List<String> capabilities(UUID tenantId) {
        return List.of("WEBSITE.VIEW", "WEBSITE.WRITE", "WEBSITE.PUBLISH", "WEBSITE.ADMIN");
    }

    @Override
    public Map<String, Object> kpiSummary(UUID tenantId) {
        Map<String, Object> m = new LinkedHashMap<>();
        try {
            Integer total = jdbc.queryForObject("SELECT COUNT(*) FROM websites WHERE tenant_id = ?", Integer.class, tenantId);
            Integer active = jdbc.queryForObject("SELECT COUNT(*) FROM websites WHERE tenant_id = ? AND status = 'ACTIVE'", Integer.class, tenantId);
            Integer pages = jdbc.queryForObject("SELECT COUNT(*) FROM website_pages WHERE tenant_id = ?", Integer.class, tenantId);
            Integer published = jdbc.queryForObject("SELECT COUNT(*) FROM website_pages WHERE tenant_id = ? AND status = 'PUBLISHED'", Integer.class, tenantId);
            Integer domains = jdbc.queryForObject("SELECT COUNT(*) FROM website_domains WHERE tenant_id = ? AND activation_status = 'ACTIVE'", Integer.class, tenantId);
            m.put("totalWebsites", total != null ? total : 0);
            m.put("activeWebsites", active != null ? active : 0);
            m.put("totalPages", pages != null ? pages : 0);
            m.put("publishedPages", published != null ? published : 0);
            m.put("activeDomains", domains != null ? domains : 0);
        } catch (Exception e) { m.put("_error", e.getClass().getSimpleName()); }
        return m;
    }

    @Override
    public Map<String, Object> operationalSummary(UUID tenantId) { return kpiSummary(tenantId); }

    @Override
    public int openAlertsCount(UUID tenantId) { return 0; }
    @Override
    public int openRisksCount(UUID tenantId) { return 0; }
    @Override
    public int openIssuesCount(UUID tenantId) { return 0; }
    @Override
    public SlaState slaState(UUID tenantId) { return SlaState.NOT_APPLICABLE; }

    @Override
    public Map<String, Object> metadata() {
        return Map.of("version", "1.0", "maturity", "GA", "since", "V20260816.2");
    }
}
