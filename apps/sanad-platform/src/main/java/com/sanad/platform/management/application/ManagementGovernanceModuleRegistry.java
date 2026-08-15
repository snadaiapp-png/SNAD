package com.sanad.platform.management.application;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Auto-discovery registry of all {@link ManagementGovernanceModuleContract}
 * beans in the Spring context (GAP 24).
 *
 * <p>Spring injects a {@code List<ManagementGovernanceModuleContract>}
 * automatically — every {@code @Service} that implements the contract
 * is collected here. This means:
 *
 * <ul>
 *   <li>Adding a new module (e.g. ERP) requires only one new
 *       {@code @Service} class implementing {@link ManagementGovernanceModuleContract}.</li>
 *   <li>The Senior Management core (Command Center, Executive Report,
 *       Cross-Module Operational Overview) needs NO modification when
 *       a new module is added — it auto-discovers the new contract bean.</li>
 *   <li>Unknown / future modules are gracefully tolerated: if no
 *       contract implementation exists for a module code, the registry
 *       returns an empty list (no error, no NPE).</li>
 * </ul>
 *
 * <p>The registry caches the discovery result per tenantId for 30 seconds
 * to avoid repeated Spring bean lookups in tight loops.
 */
@Component
public class ManagementGovernanceModuleRegistry {

    private final List<ManagementGovernanceModuleContract> modules;
    private final ConcurrentHashMap<UUID, CachedDiscovery> cache = new ConcurrentHashMap<>();
    private static final long CACHE_TTL_MS = 30_000L;

    public ManagementGovernanceModuleRegistry(List<ManagementGovernanceModuleContract> modules) {
        this.modules = modules != null ? modules : List.of();
    }

    /**
     * List all registered contract implementations.
     * Spring autowires the full list at construction time.
     */
    public List<ManagementGovernanceModuleContract> allModules() {
        return modules;
    }

    /**
     * Discover modules enabled for the given tenant. Returns a snapshot
     * cached for 30 seconds to amortize the discovery cost.
     */
    public List<ManagementGovernanceModuleContract> modulesForTenant(UUID tenantId) {
        CachedDiscovery cached = cache.get(tenantId);
        if (cached != null && (System.currentTimeMillis() - cached.timestamp) < CACHE_TTL_MS) {
            return cached.modules;
        }
        List<ManagementGovernanceModuleContract> enabled = new ArrayList<>();
        for (ManagementGovernanceModuleContract m : modules) {
            try {
                if (m.isEnabled(tenantId)) {
                    enabled.add(m);
                }
            } catch (Exception ignored) {
                // a module that throws on isEnabled() is treated as disabled
            }
        }
        enabled.sort(Comparator.comparing(ManagementGovernanceModuleContract::moduleCode));
        List<ManagementGovernanceModuleContract> immutable = List.copyOf(enabled);
        cache.put(tenantId, new CachedDiscovery(immutable, System.currentTimeMillis()));
        return immutable;
    }

    /**
     * Find a specific module by code. Returns empty if not registered
     * or not enabled for the tenant.
     */
    public java.util.Optional<ManagementGovernanceModuleContract> find(UUID tenantId, String moduleCode) {
        if (moduleCode == null) return java.util.Optional.empty();
        return modulesForTenant(tenantId).stream()
                .filter(m -> moduleCode.equals(m.moduleCode()))
                .findFirst();
    }

    /**
     * Composite health status across all enabled modules for a tenant.
     * Returns the worst status found (UNHEALTHY > DEGRADED > HEALTHY > UNAVAILABLE).
     * Returns {@code UNAVAILABLE} if no modules are enabled.
     */
    public ManagementGovernanceModuleContract.ModuleHealthStatus compositeHealthStatus(UUID tenantId) {
        List<ManagementGovernanceModuleContract> enabled = modulesForTenant(tenantId);
        if (enabled.isEmpty()) {
            return ManagementGovernanceModuleContract.ModuleHealthStatus.UNAVAILABLE;
        }
        ManagementGovernanceModuleContract.ModuleHealthStatus worst =
                ManagementGovernanceModuleContract.ModuleHealthStatus.HEALTHY;
        for (ManagementGovernanceModuleContract m : enabled) {
            try {
                ManagementGovernanceModuleContract.ModuleHealthStatus s = m.healthStatus(tenantId);
                if (s == ManagementGovernanceModuleContract.ModuleHealthStatus.UNHEALTHY) {
                    return ManagementGovernanceModuleContract.ModuleHealthStatus.UNHEALTHY;
                }
                if (s.ordinal() > worst.ordinal()) {
                    worst = s;
                }
            } catch (Exception ignored) {
                worst = ManagementGovernanceModuleContract.ModuleHealthStatus.DEGRADED;
            }
        }
        return worst;
    }

    /**
     * Composite summary used by the Executive Command Center dashboard.
     * Returns one row per enabled module with the full contract surface.
     */
    public List<Map<String, Object>> compositeSummary(UUID tenantId) {
        List<ManagementGovernanceModuleContract> enabled = modulesForTenant(tenantId);
        List<Map<String, Object>> result = new ArrayList<>(enabled.size());
        for (ManagementGovernanceModuleContract m : enabled) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("moduleCode", m.moduleCode());
            row.put("displayName", m.displayName());
            row.put("enabled", true);
            try { row.put("healthStatus", m.healthStatus(tenantId)); }
            catch (Exception e) { row.put("healthStatus", ManagementGovernanceModuleContract.ModuleHealthStatus.UNAVAILABLE); }
            try { row.put("capabilities", m.capabilities(tenantId)); }
            catch (Exception e) { row.put("capabilities", List.of()); }
            try { row.put("kpiSummary", m.kpiSummary(tenantId)); }
            catch (Exception e) { row.put("kpiSummary", Map.of()); }
            try { row.put("operationalSummary", m.operationalSummary(tenantId)); }
            catch (Exception e) { row.put("operationalSummary", Map.of()); }
            try { row.put("openAlertsCount", m.openAlertsCount(tenantId)); }
            catch (Exception e) { row.put("openAlertsCount", 0); }
            try { row.put("openRisksCount", m.openRisksCount(tenantId)); }
            catch (Exception e) { row.put("openRisksCount", 0); }
            try { row.put("openIssuesCount", m.openIssuesCount(tenantId)); }
            catch (Exception e) { row.put("openIssuesCount", 0); }
            try { row.put("slaState", m.slaState(tenantId)); }
            catch (Exception e) { row.put("slaState", ManagementGovernanceModuleContract.SlaState.NOT_APPLICABLE); }
            try { row.put("metadata", m.metadata()); }
            catch (Exception e) { row.put("metadata", Map.of()); }
            result.add(row);
        }
        return result;
    }

    /** Invalidate the per-tenant cache (used by tests after creating new entitlements). */
    public void invalidate(UUID tenantId) {
        cache.remove(tenantId);
    }

    private record CachedDiscovery(List<ManagementGovernanceModuleContract> modules, long timestamp) {}
}
