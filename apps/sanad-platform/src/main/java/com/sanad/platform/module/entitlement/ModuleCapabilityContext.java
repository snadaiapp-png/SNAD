package com.sanad.platform.module.entitlement;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable contract representing the effective capabilities for a tenant's
 * subscription at a specific point in time.
 *
 * <p>This is the ONLY object that modules (CRM, AI, Workflow, etc.) should
 * consume to determine their entitlements. Modules must NEVER read
 * {@code saas_plans}, {@code tenant_subscriptions}, or
 * {@code plan_module_entitlements} directly.
 *
 * <p>Use the factory methods to construct instances:
 * <ul>
 *   <li>{@link #denied(UUID, String, String)} — when entitlements cannot be resolved</li>
 *   <li>{@link #allowed(UUID, UUID, UUID, Map, Map, Map)} — when entitlements are resolved</li>
 * </ul>
 *
 * @param tenantId       the tenant this context belongs to
 * @param subscriptionId the active subscription (null if no subscription)
 * @param planId         the plan governing the subscription (null if no plan)
 * @param moduleCode     the module this context is for
 * @param moduleEnabled  whether the module is enabled at all
 * @param capabilities   map of capability code → boolean value
 * @param limits         map of capability code → numeric limit
 * @param quotas         map of capability code → quota value (with period encoded in QuotaValue)
 * @param effectiveAt    when this context became effective
 */
public record ModuleCapabilityContext(
        UUID tenantId,
        UUID subscriptionId,
        UUID planId,
        String moduleCode,
        boolean moduleEnabled,
        Map<String, Boolean> capabilities,
        Map<String, Long> limits,
        Map<String, QuotaValue> quotas,
        Instant effectiveAt
) {
    public ModuleCapabilityContext {
        capabilities = capabilities != null ? Collections.unmodifiableMap(capabilities) : Collections.emptyMap();
        limits = limits != null ? Collections.unmodifiableMap(limits) : Collections.emptyMap();
        quotas = quotas != null ? Collections.unmodifiableMap(quotas) : Collections.emptyMap();
    }

    /**
     * Factory for a denied context (no entitlements — module is disabled).
     */
    public static ModuleCapabilityContext denied(UUID tenantId, UUID subscriptionId, String moduleCode) {
        return new ModuleCapabilityContext(
                tenantId, subscriptionId, null, moduleCode, false,
                Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap(),
                Instant.now()
        );
    }

    /**
     * Factory for an allowed context with full entitlements.
     */
    public static ModuleCapabilityContext allowed(
            UUID tenantId,
            UUID subscriptionId,
            UUID planId,
            String moduleCode,
            Map<String, Boolean> capabilities,
            Map<String, Long> limits,
            Map<String, QuotaValue> quotas) {
        return new ModuleCapabilityContext(
                tenantId, subscriptionId, planId, moduleCode, true,
                capabilities, limits, quotas, Instant.now()
        );
    }

    /**
     * Check if a boolean capability is enabled.
     *
     * @param capabilityCode the capability code (e.g., "CRM.ADVANCED_PIPELINE")
     * @return true if the capability is explicitly enabled; false otherwise
     */
    public boolean hasCapability(String capabilityCode) {
        return Boolean.TRUE.equals(capabilities.get(capabilityCode));
    }

    /**
     * Get a numeric limit.
     *
     * @param capabilityCode the capability code (e.g., "CRM.MAX_CONTACTS")
     * @return the limit value, or {@link Long#MAX_VALUE} if unlimited (null), or 0 if module disabled
     */
    public long getLimit(String capabilityCode) {
        if (!moduleEnabled) return 0L;
        Long val = limits.get(capabilityCode);
        return val != null ? val : Long.MAX_VALUE;
    }

    /**
     * Get a quota value.
     *
     * @param capabilityCode the capability code (e.g., "AI.MONTHLY_OPERATIONS")
     * @return the quota value, or null if not set
     */
    public QuotaValue getQuota(String capabilityCode) {
        if (!moduleEnabled) return null;
        return quotas.get(capabilityCode);
    }

    /**
     * Check if this context represents an enabled module.
     */
    public boolean isModuleEnabled() {
        return moduleEnabled;
    }

    /**
     * Immutable holder for quota value + period.
     *
     * @param value  the numeric quota
     * @param period the period (DAILY, MONTHLY, YEARLY, TOTAL)
     */
    public record QuotaValue(long value, String period) {
        public QuotaValue {
            Objects.requireNonNull(period, "quota period must not be null");
        }

        public static QuotaValue of(long value, String period) {
            return new QuotaValue(value, period);
        }
    }
}
