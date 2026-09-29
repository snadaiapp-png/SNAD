package com.sanad.platform.platformiam.api;

import com.sanad.platform.access.capability.CapabilityResponse;
import com.sanad.platform.access.role.RoleAccessResponse;
import com.sanad.platform.platformiam.dto.CreatePlatformRoleRequest;
import com.sanad.platform.platformiam.dto.PlatformRoleResponse;
import com.sanad.platform.platformiam.dto.ReplaceRoleCapabilitiesRequest;
import com.sanad.platform.platformiam.dto.UpdatePlatformRoleRequest;
import com.sanad.platform.platformiam.service.PlatformRoleService;
import com.sanad.platform.security.authorization.PlatformMembershipGuard;
import com.sanad.platform.security.authorization.RequireCapability;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/executive")
public class PlatformAccessController {

    private final PlatformRoleService roles;
    private final PlatformMembershipGuard membershipGuard;

    public PlatformAccessController(PlatformRoleService roles, PlatformMembershipGuard membershipGuard) {
        this.roles = roles;
        this.membershipGuard = membershipGuard;
    }

    @GetMapping("/roles")
    @RequireCapability("PLATFORM.ROLE.READ")
    public List<PlatformRoleResponse> listRoles(Authentication authentication) {
        membershipGuard.requireActive(authentication);
        return roles.list(authentication);
    }

    @GetMapping("/roles/{roleId}")
    @RequireCapability("PLATFORM.ROLE.READ")
    public PlatformRoleResponse getRole(Authentication authentication, @PathVariable UUID roleId) {
        membershipGuard.requireActive(authentication);
        return roles.get(authentication, roleId);
    }

    @PostMapping("/roles")
    @RequireCapability("PLATFORM.ROLE.CREATE")
    public ResponseEntity<PlatformRoleResponse> createRole(
            Authentication authentication,
            @Valid @RequestBody CreatePlatformRoleRequest request) {
        membershipGuard.requireActive(authentication);
        return ResponseEntity.status(HttpStatus.CREATED).body(roles.create(authentication, request));
    }

    @PatchMapping("/roles/{roleId}")
    @RequireCapability("PLATFORM.ROLE.UPDATE")
    public PlatformRoleResponse updateRole(
            Authentication authentication,
            @PathVariable UUID roleId,
            @Valid @RequestBody UpdatePlatformRoleRequest request) {
        membershipGuard.requireActive(authentication);
        return roles.update(authentication, roleId, request);
    }

    @GetMapping("/capabilities")
    @RequireCapability("PLATFORM.PERMISSION.READ")
    public List<CapabilityResponse> listCapabilities(Authentication authentication) {
        membershipGuard.requireActive(authentication);
        return roles.listCapabilities(authentication);
    }

    @GetMapping("/roles/{roleId}/capabilities")
    @RequireCapability("PLATFORM.PERMISSION.READ")
    public List<RoleAccessResponse> listRoleCapabilities(
            Authentication authentication,
            @PathVariable UUID roleId) {
        membershipGuard.requireActive(authentication);
        return roles.listRoleCapabilities(authentication, roleId);
    }

    @PutMapping("/roles/{roleId}/capabilities")
    @RequireCapability("PLATFORM.PERMISSION.MANAGE")
    public List<RoleAccessResponse> replaceRoleCapabilities(
            Authentication authentication,
            @PathVariable UUID roleId,
            @Valid @RequestBody ReplaceRoleCapabilitiesRequest request) {
        membershipGuard.requireActive(authentication);
        return roles.replaceRoleCapabilities(authentication, roleId, request);
    }
}
