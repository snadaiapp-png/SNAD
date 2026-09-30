package com.sanad.platform.access.api;

import com.sanad.platform.access.override.UserPermissionOverrideService;
import com.sanad.platform.access.override.dto.CreateOverrideRequest;
import com.sanad.platform.access.override.dto.OverrideResponse;
import com.sanad.platform.security.authorization.RequireCapability;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/access/overrides")
public class UserOverrideController {
    private final UserPermissionOverrideService service;

    public UserOverrideController(UserPermissionOverrideService service) {
        this.service = service;
    }

    @RequireCapability("AUTHORIZATION.OVERRIDE.MANAGE")
    @PostMapping
    public ResponseEntity<OverrideResponse> create(
            Authentication authentication, @RequestBody CreateOverrideRequest request) {
        UUID tenantId = AccessPrincipalContext.requireTenantId(authentication);
        UUID actorUserId = AccessPrincipalContext.requireUserId(authentication);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.create(tenantId, actorUserId, request));
    }

    @RequireCapability("AUTHORIZATION.OVERRIDE.MANAGE")
    @GetMapping
    public ResponseEntity<List<OverrideResponse>> list(
            Authentication authentication, @RequestParam UUID userId) {
        return ResponseEntity.ok(service.list(
                AccessPrincipalContext.requireTenantId(authentication), userId));
    }

    @RequireCapability("AUTHORIZATION.OVERRIDE.MANAGE")
    @PatchMapping("/{overrideId}/revoke")
    public ResponseEntity<Void> revoke(
            Authentication authentication, @PathVariable UUID overrideId) {
        service.revoke(AccessPrincipalContext.requireTenantId(authentication),
                AccessPrincipalContext.requireUserId(authentication), overrideId);
        return ResponseEntity.noContent().build();
    }
}
