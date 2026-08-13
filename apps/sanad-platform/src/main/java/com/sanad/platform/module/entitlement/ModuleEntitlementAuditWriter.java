package com.sanad.platform.module.entitlement;

import com.sanad.platform.admin.service.PlatformAuditWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Audit writer for Module Registry and Entitlement events.
 *
 * <p>Wraps the existing {@link PlatformAuditWriter} with module-specific
 * audit actions:
 * <ul>
 *   <li>{@code PLAN_ENTITLEMENT_CHANGED} — when a plan-module entitlement is updated</li>
 *   <li>{@code MODULE_ENABLED} — when a module is enabled for a plan/tenant</li>
 *   <li>{@code MODULE_DISABLED} — when a module is disabled for a plan/tenant</li>
 *   <li>{@code CAPABILITY_CHANGED} — when a capability value changes</li>
 *   <li>{@code LIMIT_CHANGED} — when a numeric limit changes</li>
 *   <li>{@code QUOTA_CHANGED} — when a quota value changes</li>
 *   <li>{@code ENTITLEMENTS_RECALCULATED} — when the entitlement cache is refreshed</li>
 * </ul>
 *
 * <p>All writes go to the existing {@code platform_audit_logs} table —
 * no new audit infrastructure is created.
 */
@Component
public class ModuleEntitlementAuditWriter {

    private static final Logger log = LoggerFactory.getLogger(ModuleEntitlementAuditWriter.class);

    private final PlatformAuditWriter auditWriter;

    public ModuleEntitlementAuditWriter(PlatformAuditWriter auditWriter) {
        this.auditWriter = auditWriter;
    }

    public void writePlanEntitlementChanged(
            UUID actorTenantId, UUID actorUserId, UUID targetTenantId,
            UUID planId, UUID moduleId, String moduleCode,
            String capabilityCode, Object beforeState, Object afterState,
            String correlationId) {
        auditWriter.writeSuccess(
                actorTenantId, actorUserId, targetTenantId,
                "PLAN_ENTITLEMENT_CHANGED",
                "PLAN_MODULE_ENTITLEMENT",
                resourceId(planId, moduleId, capabilityCode),
                "Plan module entitlement updated for " + moduleCode,
                beforeState, afterState,
                correlationId, Instant.now());
        log.debug("Audit: PLAN_ENTITLEMENT_CHANGED plan={} module={} cap={}",
                planId, moduleCode, capabilityCode);
    }

    public void writeModuleEnabled(
            UUID actorTenantId, UUID actorUserId, UUID targetTenantId,
            UUID planId, String moduleCode, String correlationId) {
        auditWriter.writeSuccess(
                actorTenantId, actorUserId, targetTenantId,
                "MODULE_ENABLED",
                "MODULE",
                moduleCode,
                "Module " + moduleCode + " enabled",
                null, Map.of("moduleCode", moduleCode, "planId", planId != null ? planId.toString() : "null", "enabled", true),
                correlationId, Instant.now());
        log.debug("Audit: MODULE_ENABLED module={}", moduleCode);
    }

    public void writeModuleDisabled(
            UUID actorTenantId, UUID actorUserId, UUID targetTenantId,
            UUID planId, String moduleCode, String correlationId) {
        auditWriter.writeSuccess(
                actorTenantId, actorUserId, targetTenantId,
                "MODULE_DISABLED",
                "MODULE",
                moduleCode,
                "Module " + moduleCode + " disabled",
                Map.of("moduleCode", moduleCode, "planId", planId != null ? planId.toString() : "null", "enabled", true),
                Map.of("moduleCode", moduleCode, "planId", planId != null ? planId.toString() : "null", "enabled", false),
                correlationId, Instant.now());
        log.debug("Audit: MODULE_DISABLED module={}", moduleCode);
    }

    public void writeCapabilityChanged(
            UUID actorTenantId, UUID actorUserId, UUID targetTenantId,
            String capabilityCode, Object beforeValue, Object afterValue,
            String correlationId) {
        auditWriter.writeSuccess(
                actorTenantId, actorUserId, targetTenantId,
                "CAPABILITY_CHANGED",
                "CAPABILITY",
                capabilityCode,
                "Capability " + capabilityCode + " changed",
                beforeValue != null ? Map.of("value", beforeValue) : null,
                Map.of("value", afterValue != null ? afterValue : "null"),
                correlationId, Instant.now());
        log.debug("Audit: CAPABILITY_CHANGED cap={} from={} to={}",
                capabilityCode, beforeValue, afterValue);
    }

    public void writeLimitChanged(
            UUID actorTenantId, UUID actorUserId, UUID targetTenantId,
            String capabilityCode, Long beforeValue, Long afterValue,
            String correlationId) {
        auditWriter.writeSuccess(
                actorTenantId, actorUserId, targetTenantId,
                "LIMIT_CHANGED",
                "NUMERIC_LIMIT",
                capabilityCode,
                "Limit " + capabilityCode + " changed",
                beforeValue != null ? Map.of("limit", beforeValue) : null,
                Map.of("limit", afterValue != null ? afterValue : -1),
                correlationId, Instant.now());
        log.debug("Audit: LIMIT_CHANGED cap={} from={} to={}",
                capabilityCode, beforeValue, afterValue);
    }

    public void writeQuotaChanged(
            UUID actorTenantId, UUID actorUserId, UUID targetTenantId,
            String capabilityCode, Long beforeValue, Long afterValue,
            String quotaPeriod, String correlationId) {
        auditWriter.writeSuccess(
                actorTenantId, actorUserId, targetTenantId,
                "QUOTA_CHANGED",
                "QUOTA",
                capabilityCode,
                "Quota " + capabilityCode + " changed",
                beforeValue != null ? Map.of("quota", beforeValue, "period", quotaPeriod) : null,
                Map.of("quota", afterValue != null ? afterValue : -1, "period", quotaPeriod),
                correlationId, Instant.now());
        log.debug("Audit: QUOTA_CHANGED cap={} from={} to={} period={}",
                capabilityCode, beforeValue, afterValue, quotaPeriod);
    }

    public void writeEntitlementsRecalculated(
            UUID actorTenantId, UUID actorUserId, UUID targetTenantId,
            int modulesProcessed, String correlationId) {
        auditWriter.writeSuccess(
                actorTenantId, actorUserId, targetTenantId,
                "ENTITLEMENTS_RECALCULATED",
                "TENANT_ENTITLEMENTS",
                targetTenantId != null ? targetTenantId.toString() : "unknown",
                "Entitlements recalculated for " + modulesProcessed + " modules",
                null, Map.of("modulesProcessed", modulesProcessed),
                correlationId, Instant.now());
        log.debug("Audit: ENTITLEMENTS_RECALCULATED tenant={} modules={}",
                targetTenantId, modulesProcessed);
    }

    private String resourceId(UUID planId, UUID moduleId, String capabilityCode) {
        StringBuilder sb = new StringBuilder();
        if (planId != null) sb.append(planId);
        if (moduleId != null) sb.append(sb.length() > 0 ? ":" : "").append(moduleId);
        if (capabilityCode != null) sb.append(sb.length() > 0 ? ":" : "").append(capabilityCode);
        return sb.length() > 0 ? sb.toString() : "unknown";
    }
}
