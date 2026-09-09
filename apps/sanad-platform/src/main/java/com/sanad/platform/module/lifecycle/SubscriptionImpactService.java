package com.sanad.platform.module.lifecycle;

import com.sanad.platform.module.entitlement.EntitlementResolver;
import com.sanad.platform.module.entitlement.ModuleCapabilityContext;
import com.sanad.platform.module.registry.*;
import com.sanad.platform.subscription.lifecycle.SubscriptionResolutionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

/**
 * Service for generating upgrade/downgrade impact previews.
 *
 * <p>This service does NOT modify subscriptions — it only computes what would change
 * if a tenant's plan were changed. The actual plan change is handled by
 * {@link com.sanad.platform.admin.service.SaasAdministrationService#changePlan}.
 */
@Service
public class SubscriptionImpactService {

    private final EntitlementResolver entitlementResolver;
    private final ModuleRepository moduleRepository;
    private final ModuleCapabilityRepository moduleCapabilityRepository;
    private final PlanModuleEntitlementRepository planModuleEntitlementRepository;
    private final JdbcTemplate jdbc;
    private final SubscriptionResolutionService resolution;

    @Autowired
    public SubscriptionImpactService(EntitlementResolver entitlementResolver,
                                       ModuleRepository moduleRepository,
                                       ModuleCapabilityRepository moduleCapabilityRepository,
                                       PlanModuleEntitlementRepository planModuleEntitlementRepository,
                                       JdbcTemplate jdbc,
                                       SubscriptionResolutionService resolution) {
        this.entitlementResolver = entitlementResolver;
        this.moduleRepository = moduleRepository;
        this.moduleCapabilityRepository = moduleCapabilityRepository;
        this.planModuleEntitlementRepository = planModuleEntitlementRepository;
        this.jdbc = jdbc;
        this.resolution = resolution;
    }

    /**
     * Backward-compatible constructor (tests): self-wires the R0C-10
     * effective-resolution authority from the same JdbcTemplate.
     */
    public SubscriptionImpactService(EntitlementResolver entitlementResolver,
                                       ModuleRepository moduleRepository,
                                       ModuleCapabilityRepository moduleCapabilityRepository,
                                       PlanModuleEntitlementRepository planModuleEntitlementRepository,
                                       JdbcTemplate jdbc) {
        this(entitlementResolver, moduleRepository, moduleCapabilityRepository,
                planModuleEntitlementRepository, jdbc, new SubscriptionResolutionService(jdbc));
    }

    /**
     * Generate a preview of what an upgrade/downgrade would change.
     *
     * @param tenantId       the tenant UUID
     * @param targetPlanId    the target plan UUID
     * @return preview with module-level changes
     */
    @Transactional(readOnly = true)
    public SubscriptionImpactPreview previewPlanChange(UUID tenantId, UUID targetPlanId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(targetPlanId, "targetPlanId must not be null");

        // Get current entitlements for all enabled modules
        List<ModuleEntity> allModules = moduleRepository.findAllEnabled();
        List<ModuleImpact> moduleImpacts = new ArrayList<>();

        for (ModuleEntity module : allModules) {
            ModuleImpact impact = computeModuleImpact(tenantId, module, targetPlanId);
            moduleImpacts.add(impact);
        }

        // Get plan names
        String currentPlanCode = getCurrentPlanCode(tenantId);
        String targetPlanCode = getPlanCode(targetPlanId);

        boolean isUpgrade = isUpgrade(currentPlanCode, targetPlanCode);

        return new SubscriptionImpactPreview(
                tenantId,
                currentPlanCode,
                targetPlanCode,
                isUpgrade ? "UPGRADE" : "DOWNGRADE",
                moduleImpacts,
                "NO DATA WILL BE DELETED. Only entitlements, limits, and capabilities will change.",
                Instant.now()
        );
    }

    private ModuleImpact computeModuleImpact(UUID tenantId, ModuleEntity module, UUID targetPlanId) {
        // Current entitlements
        ModuleCapabilityContext currentCtx = entitlementResolver.getEffectiveEntitlements(tenantId, module.getCode());

        // Target plan entitlements for this module
        List<PlanModuleEntitlementEntity> targetEntitlements =
                planModuleEntitlementRepository.findByPlanIdAndModuleId(targetPlanId, module.getId());

        boolean targetEnabled = !targetEntitlements.isEmpty();
        String status;
        if (currentCtx.isModuleEnabled() && targetEnabled) {
            status = "UNCHANGED";
        } else if (currentCtx.isModuleEnabled() && !targetEnabled) {
            status = "DISABLED";
        } else if (!currentCtx.isModuleEnabled() && targetEnabled) {
            status = "ENABLED";
        } else {
            status = "STILL_DISABLED";
        }

        // Compute changes in capabilities
        List<CapabilityChange> changes = new ArrayList<>();
        List<ModuleCapabilityEntity> moduleCaps = moduleCapabilityRepository.findByModuleId(module.getId());

        for (ModuleCapabilityEntity cap : moduleCaps) {
            if (cap.getStatus() == null || !"ACTIVE".equals(cap.getStatus())) continue;

            // Current value
            String currentValue = getCurrentValue(currentCtx, cap);
            // Target value
            String targetValue = getTargetValue(targetEntitlements, cap);

            if (!Objects.equals(currentValue, targetValue)) {
                String changeType;
                if (cap.getCapabilityType().equals("NUMERIC_LIMIT")) {
                    long curr = parseLong(currentValue);
                    long target = parseLong(targetValue);
                    changeType = target > curr ? "INCREASED" : target < curr ? "DECREASED" : "UNCHANGED";
                } else if (cap.getCapabilityType().equals("QUOTA")) {
                    long curr = parseLong(currentValue);
                    long target = parseLong(targetValue);
                    changeType = target > curr ? "QUOTA_INCREASED" : target < curr ? "QUOTA_DECREASED" : "UNCHANGED";
                } else {
                    changeType = "CHANGED";
                }
                changes.add(new CapabilityChange(cap.getCode(), cap.getCapabilityType(),
                        currentValue, targetValue, changeType));
            }
        }

        return new ModuleImpact(
                module.getCode(),
                module.getName(),
                currentCtx.isModuleEnabled(),
                targetEnabled,
                status,
                changes
        );
    }

    private String getCurrentValue(ModuleCapabilityContext ctx, ModuleCapabilityEntity cap) {
        CapabilityType type = CapabilityType.fromString(cap.getCapabilityType());
        return switch (type) {
            case MODULE_ENABLED, FEATURE_ENABLED, BOOLEAN_CAPABILITY ->
                    String.valueOf(ctx.hasCapability(cap.getCode()));
            case NUMERIC_LIMIT -> String.valueOf(ctx.getLimit(cap.getCode()));
            case QUOTA -> {
                ModuleCapabilityContext.QuotaValue q = ctx.getQuota(cap.getCode());
                yield q != null ? String.valueOf(q.value()) : "0";
            }
        };
    }

    private String getTargetValue(List<PlanModuleEntitlementEntity> entitlements, ModuleCapabilityEntity cap) {
        for (PlanModuleEntitlementEntity e : entitlements) {
            if (cap.getCode().equals(e.getCapabilityCode())) {
                if (e.getLimitValue() != null) return String.valueOf(e.getLimitValue());
                if (e.getQuotaValue() != null) return String.valueOf(e.getQuotaValue());
                if (e.getCapabilityValue() != null) return e.getCapabilityValue();
            }
        }
        return cap.getDefaultValue() != null ? cap.getDefaultValue() : "0";
    }

    private long parseLong(String value) {
        if (value == null || value.isBlank()) return 0;
        try { return Long.parseLong(value); }
        catch (NumberFormatException e) { return 0; }
    }

    /**
     * R0C-10 MODEL_B convergence: resolve the tenant's unique EFFECTIVE
     * subscription and keep the legacy ACTIVE lifecycle gate — replacing the
     * arbitrary {@code status = 'ACTIVE' LIMIT 1} tenant-only selection.
     * Observable behavior is unchanged; the resolution is now
     * multiplicity-safe and deterministic.
     */
    private String getCurrentPlanCode(UUID tenantId) {
        try {
            return resolution.findEffectiveSubscription(tenantId)
                    .filter(sub -> "ACTIVE".equals(sub.status()))
                    .map(sub -> jdbc.queryForObject(
                            "SELECT code FROM saas_plans WHERE id = ?", String.class, sub.planId()))
                    .orElse("NONE");
        } catch (Exception e) {
            return "NONE";
        }
    }

    private String getPlanCode(UUID planId) {
        try {
            return jdbc.queryForObject(
                    "SELECT code FROM saas_plans WHERE id = ?",
                    String.class, planId);
        } catch (Exception e) {
            return "UNKNOWN";
        }
    }

    private boolean isUpgrade(String currentCode, String targetCode) {
        int currentRank = getPlanRank(currentCode);
        int targetRank = getPlanRank(targetCode);
        return targetRank > currentRank;
    }

    private int getPlanRank(String code) {
        return switch (code) {
            case "STARTER" -> 1;
            case "GROWTH" -> 2;
            case "ENTERPRISE" -> 3;
            default -> 0;
        };
    }

    // === Records ===

    public record SubscriptionImpactPreview(
            UUID tenantId,
            String currentPlanCode,
            String targetPlanCode,
            String changeType,             // UPGRADE | DOWNGRADE
            List<ModuleImpact> moduleImpacts,
            String dataSafetyNote,
            Instant previewGeneratedAt
    ) {}

    public record ModuleImpact(
            String moduleCode,
            String moduleName,
            boolean currentlyEnabled,
            boolean targetEnabled,
            String status,                 // ENABLED | DISABLED | UNCHANGED | STILL_DISABLED
            List<CapabilityChange> capabilityChanges
    ) {}

    public record CapabilityChange(
            String capabilityCode,
            String capabilityType,
            String currentValue,
            String targetValue,
            String changeType              // INCREASED | DECREASED | CHANGED | QUOTA_INCREASED | QUOTA_DECREASED
    ) {}
}
