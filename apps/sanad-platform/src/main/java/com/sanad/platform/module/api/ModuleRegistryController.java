package com.sanad.platform.module.api;

import com.sanad.platform.module.dto.*;
import com.sanad.platform.module.entitlement.EntitlementResolver;
import com.sanad.platform.module.entitlement.ModuleCapabilityContext;
import com.sanad.platform.module.registry.*;
import com.sanad.platform.security.authorization.ControlPlaneAccessGuard;
import com.sanad.platform.security.authorization.RequireCapability;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Executive API for Module Registry and Entitlement management.
 *
 * <p>All endpoints are mounted under {@code /api/v1/executive} (existing namespace)
 * and require:
 * <ul>
 *   <li>{@code EXECUTIVE_VIEW} or {@code EXECUTIVE_MANAGE} capability</li>
 *   <li>Control-plane tenant authorization ({@link ControlPlaneAccessGuard})</li>
 * </ul>
 *
 * <p>Endpoints:
 * <ul>
 *   <li>{@code GET  /modules} — list all registered modules</li>
 *   <li>{@code GET  /modules/{moduleCode}} — get a single module</li>
 *   <li>{@code GET  /plans/{planId}/modules} — list plan-module entitlements for a plan</li>
 *   <li>{@code PUT  /plans/{planId}/modules/{moduleCode}} — update a plan-module entitlement</li>
 *   <li>{@code GET  /tenants/{tenantId}/entitlements} — get effective entitlements for a tenant</li>
 *   <li>{@code GET  /tenants/{tenantId}/modules} — list enabled modules for a tenant</li>
 *   <li>{@code POST /tenants/{tenantId}/entitlements/recalculate} — recompute entitlement cache</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/executive")
public class ModuleRegistryController {

    private static final Logger log = LoggerFactory.getLogger(ModuleRegistryController.class);

    private final ControlPlaneAccessGuard accessGuard;
    private final ModuleRepository moduleRepository;
    private final ModuleCapabilityRepository moduleCapabilityRepository;
    private final PlanModuleEntitlementRepository planModuleEntitlementRepository;
    private final EntitlementResolver entitlementResolver;

    public ModuleRegistryController(ControlPlaneAccessGuard accessGuard,
                                     ModuleRepository moduleRepository,
                                     ModuleCapabilityRepository moduleCapabilityRepository,
                                     PlanModuleEntitlementRepository planModuleEntitlementRepository,
                                     EntitlementResolver entitlementResolver) {
        this.accessGuard = accessGuard;
        this.moduleRepository = moduleRepository;
        this.moduleCapabilityRepository = moduleCapabilityRepository;
        this.planModuleEntitlementRepository = planModuleEntitlementRepository;
        this.entitlementResolver = entitlementResolver;
    }

    // ============================================================
    // Module Registry Endpoints
    // ============================================================

    @GetMapping("/modules")
    @RequireCapability("EXECUTIVE_VIEW")
    public ResponseEntity<List<ModuleResponse>> listModules(Authentication authentication) {
        accessGuard.require(authentication);
        List<ModuleResponse> modules = moduleRepository.findAll().stream()
                .map(this::toModuleResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(modules);
    }

    @GetMapping("/modules/{moduleCode}")
    @RequireCapability("EXECUTIVE_VIEW")
    public ResponseEntity<ModuleResponse> getModule(
            Authentication authentication,
            @PathVariable String moduleCode) {
        accessGuard.require(authentication);
        return moduleRepository.findByCode(moduleCode)
                .map(m -> ResponseEntity.ok(toModuleResponse(m)))
                .orElse(ResponseEntity.notFound().build());
    }

    // ============================================================
    // Plan-Module Entitlement Endpoints
    // ============================================================

    @GetMapping("/plans/{planId}/modules")
    @RequireCapability("EXECUTIVE_VIEW")
    public ResponseEntity<List<PlanModuleEntitlementResponse>> listPlanModuleEntitlements(
            Authentication authentication,
            @PathVariable UUID planId) {
        accessGuard.require(authentication);
        List<PlanModuleEntitlementEntity> entitlements = planModuleEntitlementRepository.findByPlanId(planId);
        // Enrich with module codes
        Map<UUID, String> moduleCodeMap = new HashMap<>();
        for (ModuleEntity m : moduleRepository.findAll()) {
            moduleCodeMap.put(m.getId(), m.getCode());
        }
        List<PlanModuleEntitlementResponse> result = entitlements.stream()
                .map(e -> toPlanModuleEntitlementResponse(e, moduleCodeMap.getOrDefault(e.getModuleId(), "UNKNOWN")))
                .collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    @PutMapping("/plans/{planId}/modules/{moduleCode}")
    @RequireCapability("EXECUTIVE_MANAGE")
    public ResponseEntity<PlanModuleEntitlementResponse> updatePlanModuleEntitlement(
            Authentication authentication,
            @PathVariable UUID planId,
            @PathVariable String moduleCode,
            @jakarta.validation.Valid @RequestBody PlanModuleEntitlementRequest request) {
        accessGuard.require(authentication);

        ModuleEntity module = moduleRepository.findByCode(moduleCode)
                .orElseThrow(() -> new IllegalArgumentException("Module not found: " + moduleCode));

        // Find existing or create new
        Optional<PlanModuleEntitlementEntity> existing = (request.capabilityCode() != null)
                ? planModuleEntitlementRepository.findByPlanModuleCapability(planId, module.getId(), request.capabilityCode())
                : planModuleEntitlementRepository.findByPlanIdAndModuleId(planId, module.getId())
                        .stream().findFirst();

        PlanModuleEntitlementEntity entity;
        if (existing.isPresent()) {
            entity = existing.get();
            entity.setModuleEnabled(request.moduleEnabled());
            entity.setCapabilityValue(request.capabilityValue());
            entity.setLimitValue(request.limitValue());
            entity.setQuotaValue(request.quotaValue());
            entity.setQuotaPeriod(request.quotaPeriod());
            planModuleEntitlementRepository.update(entity);
        } else {
            entity = new PlanModuleEntitlementEntity();
            entity.setPlanId(planId);
            entity.setModuleId(module.getId());
            entity.setModuleEnabled(request.moduleEnabled());
            entity.setCapabilityCode(request.capabilityCode());
            entity.setCapabilityValue(request.capabilityValue());
            entity.setLimitValue(request.limitValue());
            entity.setQuotaValue(request.quotaValue());
            entity.setQuotaPeriod(request.quotaPeriod());
            planModuleEntitlementRepository.insert(entity);
        }

        log.info("Plan module entitlement updated: plan={}, module={}, capability={}, enabled={}",
                planId, moduleCode, request.capabilityCode(), request.moduleEnabled());

        return ResponseEntity.ok(toPlanModuleEntitlementResponse(entity, module.getCode()));
    }

    // ============================================================
    // Tenant Entitlement Endpoints
    // ============================================================

    @GetMapping("/tenants/{tenantId}/entitlements")
    @RequireCapability("EXECUTIVE_VIEW")
    public ResponseEntity<List<TenantEntitlementResponse>> getTenantEntitlements(
            Authentication authentication,
            @PathVariable UUID tenantId) {
        accessGuard.require(authentication);

        List<TenantEntitlementResponse> result = new ArrayList<>();
        for (ModuleEntity module : moduleRepository.findAllEnabled()) {
            ModuleCapabilityContext ctx = entitlementResolver.getEffectiveEntitlements(tenantId, module.getCode());
            result.add(toTenantEntitlementResponse(tenantId, module.getCode(), ctx));
        }
        return ResponseEntity.ok(result);
    }

    @GetMapping("/tenants/{tenantId}/modules")
    @RequireCapability("EXECUTIVE_VIEW")
    public ResponseEntity<List<ModuleResponse>> getTenantModules(
            Authentication authentication,
            @PathVariable UUID tenantId) {
        accessGuard.require(authentication);

        List<ModuleResponse> result = new ArrayList<>();
        for (ModuleEntity module : moduleRepository.findAllEnabled()) {
            if (entitlementResolver.isModuleEnabled(tenantId, module.getCode())) {
                result.add(toModuleResponse(module));
            }
        }
        return ResponseEntity.ok(result);
    }

    @PostMapping("/tenants/{tenantId}/entitlements/recalculate")
    @RequireCapability("EXECUTIVE_MANAGE")
    public ResponseEntity<Map<String, Object>> recalculateEntitlements(
            Authentication authentication,
            @PathVariable UUID tenantId) {
        accessGuard.require(authentication);
        entitlementResolver.recalculateEntitlements(tenantId);
        return ResponseEntity.ok(Map.of(
                "tenantId", tenantId,
                "status", "RECALCULATED",
                "timestamp", Instant.now().toString()
        ));
    }

    // ============================================================
    // Mappers
    // ============================================================

    private ModuleResponse toModuleResponse(ModuleEntity m) {
        return new ModuleResponse(
                m.getId(), m.getCode(), m.getName(), m.getDescription(),
                m.getStatus(), m.getDisplayOrder(), m.getVersion(),
                m.isEnabled(), m.getCreatedAt(), m.getUpdatedAt()
        );
    }

    private PlanModuleEntitlementResponse toPlanModuleEntitlementResponse(
            PlanModuleEntitlementEntity e, String moduleCode) {
        return new PlanModuleEntitlementResponse(
                e.getId(), e.getPlanId(), e.getModuleId(), moduleCode,
                e.isModuleEnabled(), e.getCapabilityCode(), e.getCapabilityValue(),
                e.getLimitValue(), e.getQuotaValue(), e.getQuotaPeriod(),
                e.getEffectiveAt(), e.getCreatedAt(), e.getUpdatedAt()
        );
    }

    private TenantEntitlementResponse toTenantEntitlementResponse(
            UUID tenantId, String moduleCode, ModuleCapabilityContext ctx) {
        Map<String, TenantEntitlementResponse.QuotaResponse> quotas = new HashMap<>();
        for (Map.Entry<String, ModuleCapabilityContext.QuotaValue> entry : ctx.quotas().entrySet()) {
            quotas.put(entry.getKey(), new TenantEntitlementResponse.QuotaResponse(
                    entry.getValue().value(), entry.getValue().period()));
        }
        return new TenantEntitlementResponse(
                tenantId, moduleCode, ctx.isModuleEnabled(),
                ctx.subscriptionId() != null ? ctx.subscriptionId().toString() : null,
                ctx.planId() != null ? ctx.planId().toString() : null,
                ctx.capabilities(), ctx.limits(), quotas,
                ctx.effectiveAt()
        );
    }
}
