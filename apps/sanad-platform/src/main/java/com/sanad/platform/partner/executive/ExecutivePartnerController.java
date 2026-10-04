package com.sanad.platform.partner.executive;

import com.sanad.platform.security.authorization.ControlPlaneAccessGuard;
import com.sanad.platform.security.authorization.RequireCapability;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** W2-T7 executive partner control-plane HTTP boundary. */
@RestController
@RequestMapping("/api/v1/executive/partners")
public class ExecutivePartnerController {

    private final ControlPlaneAccessGuard accessGuard;
    private final ExecutivePartnerService service;

    public ExecutivePartnerController(
            ControlPlaneAccessGuard accessGuard,
            ExecutivePartnerService service) {
        this.accessGuard = accessGuard;
        this.service = service;
    }

    @GetMapping
    @RequireCapability("EXECUTIVE_VIEW")
    public ResponseEntity<List<ExecutivePartnerService.PartnerView>> list(Authentication authentication) {
        accessGuard.requireRead(authentication);
        return ResponseEntity.ok(service.listPartners());
    }

    @PostMapping
    @RequireCapability("EXECUTIVE_MANAGE")
    public ResponseEntity<ExecutivePartnerService.PartnerView> create(
            Authentication authentication,
            @Valid @RequestBody CreatePartnerRequest request) {
        accessGuard.requireWrite(authentication);
        return ResponseEntity.ok(service.createPartner(request.partnerType(), authentication));
    }

    @GetMapping("/{partnerId}")
    @RequireCapability("EXECUTIVE_VIEW")
    public ResponseEntity<ExecutivePartnerService.PartnerView> detail(
            Authentication authentication,
            @PathVariable UUID partnerId) {
        accessGuard.requireRead(authentication);
        return ResponseEntity.ok(service.getPartner(partnerId));
    }

    @PatchMapping("/{partnerId}/status")
    @RequireCapability("EXECUTIVE_MANAGE")
    public ResponseEntity<ExecutivePartnerService.PartnerView> changeStatus(
            Authentication authentication,
            @PathVariable UUID partnerId,
            @Valid @RequestBody ChangePartnerStatusRequest request) {
        accessGuard.requireWrite(authentication);
        return ResponseEntity.ok(
                service.changeStatus(partnerId, request.status(), request.reason(), authentication));
    }

    @GetMapping("/{partnerId}/users")
    @RequireCapability("EXECUTIVE_VIEW")
    public ResponseEntity<List<ExecutivePartnerService.PartnerMembershipView>> users(
            Authentication authentication,
            @PathVariable UUID partnerId) {
        accessGuard.requireRead(authentication);
        return ResponseEntity.ok(service.memberships(partnerId));
    }

    @GetMapping("/{partnerId}/tenants")
    @RequireCapability("EXECUTIVE_VIEW")
    public ResponseEntity<List<ExecutivePartnerService.PartnerTenantView>> tenants(
            Authentication authentication,
            @PathVariable UUID partnerId) {
        accessGuard.requireRead(authentication);
        return ResponseEntity.ok(service.tenants(partnerId));
    }

    @GetMapping("/{partnerId}/delegations")
    @RequireCapability("EXECUTIVE_VIEW")
    public ResponseEntity<List<ExecutivePartnerService.PartnerDelegationView>> delegations(
            Authentication authentication,
            @PathVariable UUID partnerId) {
        accessGuard.requireRead(authentication);
        return ResponseEntity.ok(service.delegations(partnerId));
    }

    public record CreatePartnerRequest(@NotBlank String partnerType) {}

    public record ChangePartnerStatusRequest(@NotBlank String status, String reason) {}
}
