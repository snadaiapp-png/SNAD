package com.sanad.platform.subscription.api;

import com.sanad.platform.admin.service.PlatformAuditService;
import com.sanad.platform.security.authorization.ControlPlaneAccessGuard;
import com.sanad.platform.security.authorization.RequireCapability;
import com.sanad.platform.subscription.plan.PlanVersionEntity;
import com.sanad.platform.subscription.plan.PlanVersionService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Executive API for plan versions.
 *
 * <p>Creating a new version never changes what existing subscribers
 * contracted — activation retires the previous version for NEW subscribers
 * only; existing subscribers stay pinned (dual-compatible read through
 * {@code tenant_subscriptions.plan_version_id} / subscription items).
 */
@RestController
@RequestMapping("/api/v1/executive")
public class PlanVersionController {

    private final ControlPlaneAccessGuard accessGuard;
    private final PlanVersionService planVersionService;
    private final PlatformAuditService auditService;

    public PlanVersionController(ControlPlaneAccessGuard accessGuard,
                                 PlanVersionService planVersionService,
                                 PlatformAuditService auditService) {
        this.accessGuard = accessGuard;
        this.planVersionService = planVersionService;
        this.auditService = auditService;
    }

    @GetMapping("/plans/{planId}/versions")
    @RequireCapability("EXECUTIVE_VIEW")
    public ResponseEntity<List<ScpDtos.PlanVersionResponse>> listVersions(
            @PathVariable UUID planId,
            Authentication authentication) {
        accessGuard.require(authentication);
        return ResponseEntity.ok(planVersionService.listForPlan(planId).stream()
                .map(ScpDtos.PlanVersionResponse::from).toList());
    }

    @PostMapping("/plans/{planId}/versions")
    @RequireCapability("EXECUTIVE_MANAGE")
    public ResponseEntity<ScpDtos.PlanVersionResponse> createVersion(
            @PathVariable UUID planId,
            @Valid @RequestBody ScpDtos.CreatePlanVersionRequest request,
            Authentication authentication) {
        accessGuard.require(authentication);
        PlanVersionEntity created = planVersionService.createDraft(
                planId,
                request.currencyCode(),
                request.monthlyPriceMinor() == null ? 0 : request.monthlyPriceMinor(),
                request.annualPriceMinor() == null ? 0 : request.annualPriceMinor(),
                request.trialDays() == null ? 0 : request.trialDays(),
                request.maxUsers() == null ? 1 : request.maxUsers(),
                request.maxOrganizations() == null ? 1 : request.maxOrganizations(),
                request.storageMb() == null ? 0 : request.storageMb());
        auditService.success(authentication, null, "PLAN_VERSION_CREATE",
                "plan_version", created.getId().toString(), null, null, created);
        return ResponseEntity.ok(ScpDtos.PlanVersionResponse.from(created));
    }

    @PostMapping("/plans/{planId}/versions/{versionId}/activate")
    @RequireCapability("EXECUTIVE_MANAGE")
    public ResponseEntity<ScpDtos.PlanVersionResponse> activateVersion(
            @PathVariable UUID planId,
            @PathVariable UUID versionId,
            Authentication authentication) {
        accessGuard.require(authentication);
        PlanVersionEntity before = planVersionService.findVersion(versionId).orElse(null);
        PlanVersionEntity activated = planVersionService.activate(versionId);
        auditService.success(authentication, null, "PLAN_VERSION_ACTIVATE",
                "plan_version", versionId.toString(), null, before, activated);
        return ResponseEntity.ok(ScpDtos.PlanVersionResponse.from(activated));
    }
}
