package com.sanad.platform.executive.api;

import com.sanad.platform.admin.api.TenantDomainDtos.CreateDomainRequest;
import com.sanad.platform.admin.api.TenantDomainDtos.DomainResponse;
import com.sanad.platform.admin.api.TenantDomainDtos.DomainSummary;
import com.sanad.platform.admin.api.TenantDomainDtos.DomainType;
import com.sanad.platform.admin.api.TenantDomainDtos.UpdateDomainRequest;
import com.sanad.platform.admin.api.TenantDomainDtos.VerifyDomainRequest;
import com.sanad.platform.admin.service.TenantDomainService;
import com.sanad.platform.security.authorization.ControlPlaneAccessGuard;
import com.sanad.platform.security.authorization.RequireCapability;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Executive Management — Tenant Domain Management API.
 *
 * <p>Endpoints mounted under {@code /api/v1/executive/tenants/{tenantId}/domains}.
 * Backed by {@link TenantDomainService}. Tenant-scoped; every operation
 * requires {@code DOMAIN_MANAGEMENT.*} capability (seeded by V20260815_23).
 *
 * <p>This controller does NOT perform DNS resolution, SSL provisioning,
 * or hostname-to-tenant routing — those concerns are external. It
 * persists tenant domain claims and the verification lifecycle.
 */
@RestController
@RequestMapping("/api/v1/executive/tenants/{tenantId}/domains")
public class TenantDomainController {

    private final ControlPlaneAccessGuard accessGuard;
    private final TenantDomainService domainService;

    public TenantDomainController(ControlPlaneAccessGuard accessGuard,
                                  TenantDomainService domainService) {
        this.accessGuard = accessGuard;
        this.domainService = domainService;
    }

    @GetMapping
    @RequireCapability("DOMAIN_MANAGEMENT.VIEW")
    public ResponseEntity<List<DomainResponse>> listDomains(
            Authentication authentication,
            @PathVariable UUID tenantId,
            @RequestParam(name = "type", required = false) DomainType filterType
    ) {
        accessGuard.require(authentication);
        return ResponseEntity.ok(domainService.listDomains(tenantId, filterType));
    }

    @GetMapping("/summary")
    @RequireCapability("DOMAIN_MANAGEMENT.VIEW")
    public ResponseEntity<DomainSummary> summarize(
            Authentication authentication,
            @PathVariable UUID tenantId
    ) {
        accessGuard.require(authentication);
        return ResponseEntity.ok(domainService.summarize(tenantId));
    }

    @PostMapping
    @RequireCapability("DOMAIN_MANAGEMENT.WRITE")
    public ResponseEntity<DomainResponse> createDomain(
            Authentication authentication,
            @PathVariable UUID tenantId,
            @Valid @RequestBody CreateDomainRequest request
    ) {
        accessGuard.require(authentication);
        return ResponseEntity.ok(domainService.createDomain(tenantId, request, authentication));
    }

    @GetMapping("/{domainId}")
    @RequireCapability("DOMAIN_MANAGEMENT.VIEW")
    public ResponseEntity<DomainResponse> getDomain(
            Authentication authentication,
            @PathVariable UUID tenantId,
            @PathVariable UUID domainId
    ) {
        accessGuard.require(authentication);
        return ResponseEntity.ok(domainService.getDomain(tenantId, domainId));
    }

    @PutMapping("/{domainId}")
    @RequireCapability("DOMAIN_MANAGEMENT.WRITE")
    public ResponseEntity<DomainResponse> updateDomain(
            Authentication authentication,
            @PathVariable UUID tenantId,
            @PathVariable UUID domainId,
            @Valid @RequestBody UpdateDomainRequest request
    ) {
        accessGuard.require(authentication);
        return ResponseEntity.ok(domainService.updateDomain(tenantId, domainId, request, authentication));
    }

    @PostMapping("/{domainId}/verify")
    @RequireCapability("DOMAIN_MANAGEMENT.VERIFY")
    public ResponseEntity<DomainResponse> verifyDomain(
            Authentication authentication,
            @PathVariable UUID tenantId,
            @PathVariable UUID domainId,
            @RequestBody VerifyDomainRequest request
    ) {
        accessGuard.require(authentication);
        return ResponseEntity.ok(domainService.verifyDomain(tenantId, domainId, request, authentication));
    }

    @PostMapping("/{domainId}/activate")
    @RequireCapability("DOMAIN_MANAGEMENT.ADMIN")
    public ResponseEntity<DomainResponse> activateDomain(
            Authentication authentication,
            @PathVariable UUID tenantId,
            @PathVariable UUID domainId
    ) {
        accessGuard.require(authentication);
        return ResponseEntity.ok(domainService.activateDomain(tenantId, domainId, authentication));
    }

    @PostMapping("/{domainId}/deactivate")
    @RequireCapability("DOMAIN_MANAGEMENT.ADMIN")
    public ResponseEntity<DomainResponse> deactivateDomain(
            Authentication authentication,
            @PathVariable UUID tenantId,
            @PathVariable UUID domainId,
            @RequestBody(required = false) java.util.Map<String, String> body
    ) {
        accessGuard.require(authentication);
        String reason = body == null ? null : body.get("reason");
        return ResponseEntity.ok(domainService.deactivateDomain(tenantId, domainId, reason, authentication));
    }

    @DeleteMapping("/{domainId}")
    @RequireCapability("DOMAIN_MANAGEMENT.ADMIN")
    public ResponseEntity<Void> deleteDomain(
            Authentication authentication,
            @PathVariable UUID tenantId,
            @PathVariable UUID domainId
    ) {
        accessGuard.require(authentication);
        domainService.deleteDomain(tenantId, domainId, authentication);
        return ResponseEntity.noContent().build();
    }
}
