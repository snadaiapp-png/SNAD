package com.sanad.platform.module.entitlement;

import com.sanad.platform.module.registry.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Central service that resolves effective entitlements for a tenant.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>{@link #isModuleEnabled(UUID, String)} — check if a module is enabled for a tenant</li>
 *   <li>{@link #hasCapability(UUID, String, String)} — check a boolean capability</li>
 *   <li>{@link #getLimit(UUID, String, String)} — get a numeric limit</li>
 *   <li>{@link #getQuota(UUID, String, String)} — get a quota value</li>
 *   <li>{@link #getEffectiveEntitlements(UUID, String)} — get the full {@link ModuleCapabilityContext}</li>
 *   <li>{@link #recalculateEntitlements(UUID)} — recompute the entitlement cache after subscription changes</li>
 * </ul>
 *
 * <p>Modules MUST use this service to check entitlements — they must NEVER
 * read {@code saas_plans}, {@code tenant_subscriptions}, or
 * {@code plan_module_entitlements} directly.
 *
 * <p>The resolver follows this resolution chain:
 * <pre>
 *   Organization / Tenant
 *         ↓
 *   Active Subscription (tenant_subscriptions where status = ACTIVE)
 *         ↓
 *   Plan (saas_plans)
 *         ↓
 *   Plan Module Entitlements (plan_module_entitlements)
 *         ↓
 *   Effective Module Capabilities (tenant_entitlement_cache)
 * </pre>
 *
 * <p>If no active subscription is found, all modules are denied.
 * If a plan has no module entitlements, defaults from {@code module_capabilities}
 * are used.
 */
@Service
public class EntitlementResolver {

    private static final Logger log = LoggerFactory.getLogger(EntitlementResolver.class);

    private final JdbcTemplate jdbc;
    private final ModuleRepository moduleRepository;
    private final ModuleCapabilityRepository moduleCapabilityRepository;
    private final PlanModuleEntitlementRepository planModuleEntitlementRepository;

    public EntitlementResolver(JdbcTemplate jdbc,
                                ModuleRepository moduleRepository,
                                ModuleCapabilityRepository moduleCapabilityRepository,
                                PlanModuleEntitlementRepository planModuleEntitlementRepository) {
        this.jdbc = jdbc;
        this.moduleRepository = moduleRepository;
        this.moduleCapabilityRepository = moduleCapabilityRepository;
        this.planModuleEntitlementRepository = planModuleEntitlementRepository;
    }

    /**
     * Check if a module is enabled for a tenant.
     *
     * @param tenantId   the tenant UUID
     * @param moduleCode the module code (e.g., "CRM", "AI")
     * @return true if the module is enabled; false otherwise
     */
    @Transactional(readOnly = true)
    public boolean isModuleEnabled(UUID tenantId, String moduleCode) {
        ModuleCapabilityContext ctx = getEffectiveEntitlements(tenantId, moduleCode);
        return ctx.isModuleEnabled();
    }

    /**
     * Check if a boolean capability is enabled for a tenant's module.
     *
     * @param tenantId       the tenant UUID
     * @param moduleCode     the module code (e.g., "CRM")
     * @param capabilityCode the capability code (e.g., "CRM.ADVANCED_PIPELINE")
     * @return true if the capability is enabled; false otherwise
     */
    @Transactional(readOnly = true)
    public boolean hasCapability(UUID tenantId, String moduleCode, String capabilityCode) {
        ModuleCapabilityContext ctx = getEffectiveEntitlements(tenantId, moduleCode);
        return ctx.hasCapability(capabilityCode);
    }

    /**
     * Get a numeric limit for a tenant's module.
     *
     * @param tenantId       the tenant UUID
     * @param moduleCode     the module code
     * @param capabilityCode the capability code (e.g., "CRM.MAX_CONTACTS")
     * @return the limit value, or {@link Long#MAX_VALUE} if unlimited, or 0 if module disabled
     */
    @Transactional(readOnly = true)
    public long getLimit(UUID tenantId, String moduleCode, String capabilityCode) {
        ModuleCapabilityContext ctx = getEffectiveEntitlements(tenantId, moduleCode);
        return ctx.getLimit(capabilityCode);
    }

    /**
     * Get a quota value for a tenant's module.
     *
     * @param tenantId       the tenant UUID
     * @param moduleCode     the module code
     * @param capabilityCode the capability code (e.g., "AI.MONTHLY_OPERATIONS")
     * @return the quota value, or null if not set
     */
    @Transactional(readOnly = true)
    public ModuleCapabilityContext.QuotaValue getQuota(UUID tenantId, String moduleCode, String capabilityCode) {
        ModuleCapabilityContext ctx = getEffectiveEntitlements(tenantId, moduleCode);
        return ctx.getQuota(capabilityCode);
    }

    /**
     * Get the full effective entitlements for a tenant's module.
     *
     * <p>This is the main entry point. It:
     * <ol>
     *   <li>Finds the tenant's active subscription</li>
     *   <li>Gets the plan ID from the subscription</li>
     *   <li>Gets the plan-module entitlements for the specified module</li>
     *   <li>Falls back to module_capabilities defaults where plan overrides are absent</li>
     *   <li>Returns a {@link ModuleCapabilityContext} with all resolved values</li>
     * </ol>
     *
     * @param tenantId   the tenant UUID
     * @param moduleCode the module code (e.g., "CRM")
     * @return the effective entitlement context (never null; use {@link ModuleCapabilityContext#isModuleEnabled()})
     */
    @Transactional(readOnly = true)
    public ModuleCapabilityContext getEffectiveEntitlements(UUID tenantId, String moduleCode) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        if (moduleCode == null || moduleCode.isBlank()) {
            return ModuleCapabilityContext.denied(tenantId, null, null);
        }

        // 1. Find the module in the registry
        Optional<ModuleEntity> moduleOpt = moduleRepository.findByCode(moduleCode.trim().toUpperCase());
        if (moduleOpt.isEmpty()) {
            log.debug("Module not found in registry: {}", moduleCode);
            return ModuleCapabilityContext.denied(tenantId, null, moduleCode);
        }
        ModuleEntity module = moduleOpt.get();

        // 2. Find the tenant's active subscription + plan
        Map<String, Object> subInfo = findActiveSubscription(tenantId);
        if (subInfo == null) {
            log.debug("No active subscription for tenant: {}", tenantId);
            return ModuleCapabilityContext.denied(tenantId, null, module.getCode());
        }
        UUID subscriptionId = (UUID) subInfo.get("subscriptionId");
        UUID planId = (UUID) subInfo.get("planId");

        // 3. Get plan-module entitlements for this module
        List<PlanModuleEntitlementEntity> planEntitlements =
                planModuleEntitlementRepository.findByPlanIdAndModuleId(planId, module.getId());

        // 4. Determine if module is enabled at the plan level
        boolean moduleEnabled = planEntitlements.stream()
                .anyMatch(PlanModuleEntitlementEntity::isModuleEnabled);
        // If no plan entitlements exist, fall back to the module's default enabled flag
        if (planEntitlements.isEmpty()) {
            moduleEnabled = module.isEnabled();
        }

        if (!moduleEnabled) {
            return ModuleCapabilityContext.denied(tenantId, subscriptionId, module.getCode());
        }

        // 5. Get all module capabilities (defaults)
        List<ModuleCapabilityEntity> moduleCaps = moduleCapabilityRepository.findByModuleId(module.getId());

        // 6. Build effective maps: plan override > module default
        Map<String, Boolean> capabilities = new HashMap<>();
        Map<String, Long> limits = new HashMap<>();
        Map<String, ModuleCapabilityContext.QuotaValue> quotas = new HashMap<>();

        // Start with defaults
        for (ModuleCapabilityEntity cap : moduleCaps) {
            if (cap.getStatus() == null || !"ACTIVE".equals(cap.getStatus())) continue;
            CapabilityType type = CapabilityType.fromString(cap.getCapabilityType());
            String val = cap.getDefaultValue();
            switch (type) {
                case MODULE_ENABLED, FEATURE_ENABLED, BOOLEAN_CAPABILITY -> {
                    if (val != null) {
                        capabilities.put(cap.getCode(), Boolean.parseBoolean(val));
                    }
                }
                case NUMERIC_LIMIT -> {
                    if (val != null) {
                        try { limits.put(cap.getCode(), Long.parseLong(val)); }
                        catch (NumberFormatException ignored) { }
                    }
                }
                case QUOTA -> {
                    // Default quotas have no period in module_capabilities; skip if no value
                    if (val != null) {
                        try { quotas.put(cap.getCode(), new ModuleCapabilityContext.QuotaValue(Long.parseLong(val), "MONTHLY")); }
                        catch (NumberFormatException ignored) { }
                    }
                }
            }
        }

        // Apply plan overrides
        for (PlanModuleEntitlementEntity pme : planEntitlements) {
            if (pme.getCapabilityCode() == null) continue;
            // Find the capability type from module_capabilities
            ModuleCapabilityEntity capDef = moduleCaps.stream()
                    .filter(c -> pme.getCapabilityCode().equals(c.getCode()))
                    .findFirst().orElse(null);
            if (capDef == null) continue;
            CapabilityType type = CapabilityType.fromString(capDef.getCapabilityType());
            switch (type) {
                case MODULE_ENABLED, FEATURE_ENABLED, BOOLEAN_CAPABILITY -> {
                    String val = pme.getCapabilityValue();
                    if (val != null) {
                        capabilities.put(pme.getCapabilityCode(), Boolean.parseBoolean(val));
                    }
                }
                case NUMERIC_LIMIT -> {
                    if (pme.getLimitValue() != null) {
                        limits.put(pme.getCapabilityCode(), pme.getLimitValue());
                    } else if (pme.getCapabilityValue() != null) {
                        try { limits.put(pme.getCapabilityCode(), Long.parseLong(pme.getCapabilityValue())); }
                        catch (NumberFormatException ignored) { }
                    }
                }
                case QUOTA -> {
                    if (pme.getQuotaValue() != null) {
                        String period = pme.getQuotaPeriod() != null ? pme.getQuotaPeriod() : "MONTHLY";
                        quotas.put(pme.getCapabilityCode(), new ModuleCapabilityContext.QuotaValue(pme.getQuotaValue(), period));
                    }
                }
            }
        }

        return ModuleCapabilityContext.allowed(
                tenantId, subscriptionId, planId, module.getCode(),
                capabilities, limits, quotas);
    }

    /**
     * Recalculate and cache effective entitlements for a tenant.
     *
     * <p>Called after subscription changes (activation, upgrade, downgrade,
     * cancellation, suspension, resume).
     *
     * @param tenantId the tenant UUID
     */
    @Transactional
    public void recalculateEntitlements(UUID tenantId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        log.info("Recalculating entitlements for tenant: {}", tenantId);

        // Delete existing cache entries for this tenant
        jdbc.update("DELETE FROM tenant_entitlement_cache WHERE tenant_id = ?", tenantId);

        // Find active subscription
        Map<String, Object> subInfo = findActiveSubscription(tenantId);
        if (subInfo == null) {
            log.debug("No active subscription for tenant {}, skipping cache population", tenantId);
            return;
        }
        UUID subscriptionId = (UUID) subInfo.get("subscriptionId");
        UUID planId = (UUID) subInfo.get("planId");

        // For each enabled module, compute and cache entitlements
        List<ModuleEntity> modules = moduleRepository.findAllEnabled();
        for (ModuleEntity module : modules) {
            ModuleCapabilityContext ctx = getEffectiveEntitlements(tenantId, module.getCode());
            cacheContext(tenantId, subscriptionId, planId, module, ctx);
        }

        log.info("Entitlements recalculated for tenant: {} ({} modules cached)", tenantId, modules.size());
    }

    private void cacheContext(UUID tenantId, UUID subscriptionId, UUID planId,
                              ModuleEntity module, ModuleCapabilityContext ctx) {
        Instant now = Instant.now();
        // Cache capabilities (boolean)
        for (Map.Entry<String, Boolean> entry : ctx.capabilities().entrySet()) {
            jdbc.update(
                    "INSERT INTO tenant_entitlement_cache " +
                            "(id, tenant_id, subscription_id, plan_id, module_id, module_enabled, " +
                            "capability_code, capability_type, effective_value, effective_at, source, created_at, updated_at) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    UUID.randomUUID(), tenantId, subscriptionId, planId, module.getId(),
                    ctx.isModuleEnabled(), entry.getKey(), "BOOLEAN_CAPABILITY",
                    entry.getValue().toString(), Timestamp.from(now), "SUBSCRIPTION",
                    Timestamp.from(now), Timestamp.from(now));
        }
        // Cache limits (numeric)
        for (Map.Entry<String, Long> entry : ctx.limits().entrySet()) {
            jdbc.update(
                    "INSERT INTO tenant_entitlement_cache " +
                            "(id, tenant_id, subscription_id, plan_id, module_id, module_enabled, " +
                            "capability_code, capability_type, effective_limit, effective_at, source, created_at, updated_at) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    UUID.randomUUID(), tenantId, subscriptionId, planId, module.getId(),
                    ctx.isModuleEnabled(), entry.getKey(), "NUMERIC_LIMIT",
                    entry.getValue(), Timestamp.from(now), "SUBSCRIPTION",
                    Timestamp.from(now), Timestamp.from(now));
        }
        // Cache quotas
        for (Map.Entry<String, ModuleCapabilityContext.QuotaValue> entry : ctx.quotas().entrySet()) {
            jdbc.update(
                    "INSERT INTO tenant_entitlement_cache " +
                            "(id, tenant_id, subscription_id, plan_id, module_id, module_enabled, " +
                            "capability_code, capability_type, effective_quota, quota_period, effective_at, source, created_at, updated_at) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    UUID.randomUUID(), tenantId, subscriptionId, planId, module.getId(),
                    ctx.isModuleEnabled(), entry.getKey(), "QUOTA",
                    entry.getValue().value(), entry.getValue().period(),
                    Timestamp.from(now), "SUBSCRIPTION",
                    Timestamp.from(now), Timestamp.from(now));
        }
    }

    /**
     * Find the active subscription for a tenant.
     *
     * @return map with "subscriptionId" and "planId" keys, or null if no active subscription
     */
    private Map<String, Object> findActiveSubscription(UUID tenantId) {
        try {
            return jdbc.queryForStream(
                    "SELECT id, plan_id FROM tenant_subscriptions WHERE tenant_id = ? AND status = 'ACTIVE' LIMIT 1",
                    (rs, rowNum) -> {
                        Map<String, Object> m = new HashMap<>();
                        m.put("subscriptionId", rs.getObject("id", UUID.class));
                        m.put("planId", rs.getObject("plan_id", UUID.class));
                        return m;
                    },
                    tenantId
            ).findFirst().orElse(null);
        } catch (Exception e) {
            log.debug("Error finding active subscription for tenant {}: {}", tenantId, e.getMessage());
            return null;
        }
    }
}
