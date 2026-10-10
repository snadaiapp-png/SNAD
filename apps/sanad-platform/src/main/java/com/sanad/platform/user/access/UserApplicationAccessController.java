package com.sanad.platform.user.access;

import com.sanad.platform.access.api.AccessPrincipalContext;
import com.sanad.platform.security.authorization.RequireCapability;
import com.sanad.platform.user.dto.UserResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
public class UserApplicationAccessController {

    private final UserApplicationAccessProjectionService projectionService;
    private final ModuleUserProvisioningService moduleProvisioningService;
    private final ModuleUserCreationService moduleUserCreationService;

    public UserApplicationAccessController(
            UserApplicationAccessProjectionService projectionService,
            ModuleUserProvisioningService moduleProvisioningService,
            ModuleUserCreationService moduleUserCreationService) {
        this.projectionService = projectionService;
        this.moduleProvisioningService = moduleProvisioningService;
        this.moduleUserCreationService = moduleUserCreationService;
    }

    @GetMapping("/{userId}/application-access")
    @RequireCapability("USER.READ")
    public List<ApplicationAccessProjection> list(
            Authentication authentication,
            @RequestParam UUID tenantId,
            @PathVariable UUID userId) {
        UUID authenticatedTenant = AccessPrincipalContext.requireTenantId(authentication);
        if (!authenticatedTenant.equals(tenantId)) {
            throw new AccessDeniedException("Cross-tenant application access read denied");
        }
        return projectionService.project(tenantId, userId);
    }
    @GetMapping("/module-context")
    @RequireCapability("CAPABILITY.READ")
    public ModuleProvisioningContext moduleContext(
            Authentication authentication,
            @RequestParam String routeRoot) {
        UUID tenantId = AccessPrincipalContext.requireTenantId(authentication);
        return moduleProvisioningService.resolve(tenantId, routeRoot);
    }

    @PostMapping("/module-user-provisioning")
    @RequireCapability("USER.GRANT_ROLE")
    public UserResponse provisionModuleUser(
            Authentication authentication,
            @RequestParam UUID tenantId,
            @Valid @RequestBody ModuleUserProvisionRequest request) {
        UUID authenticatedTenant = AccessPrincipalContext.requireTenantId(authentication);
        if (!authenticatedTenant.equals(tenantId)) {
            throw new AccessDeniedException("Cross-tenant module user provisioning denied");
        }
        return moduleUserCreationService.provision(
                tenantId,
                AccessPrincipalContext.requireUserId(authentication),
                request);
    }

    @PostMapping("/module-user-provisioning-overrides")
    @RequireCapability("AUTHORIZATION.OVERRIDE.MANAGE")
    public UserResponse provisionModuleUserWithOverrides(
            Authentication authentication,
            @RequestParam UUID tenantId,
            @Valid @RequestBody ModuleUserProvisionRequest request) {
        UUID authenticatedTenant = AccessPrincipalContext.requireTenantId(authentication);
        if (!authenticatedTenant.equals(tenantId)) {
            throw new AccessDeniedException("Cross-tenant module user override provisioning denied");
        }
        return moduleUserCreationService.provision(
                tenantId,
                AccessPrincipalContext.requireUserId(authentication),
                request);
    }

    @PostMapping("/module-capability-grants")
    @RequireCapability("USER.GRANT_ROLE")
    public void grantModuleCapabilities(
            Authentication authentication,
            @RequestParam UUID tenantId,
            @RequestBody ModuleCapabilityGrantRequest request) {
        UUID authenticatedTenant = AccessPrincipalContext.requireTenantId(authentication);
        if (!authenticatedTenant.equals(tenantId)) {
            throw new AccessDeniedException("Cross-tenant module capability grant denied");
        }
        moduleProvisioningService.grantCapabilities(
                tenantId,
                AccessPrincipalContext.requireUserId(authentication),
                request.targetUserId(),
                request.routeRoot(),
                request.capabilityCodes());
    }

    @PostMapping("/module-capability-overrides")
    @RequireCapability("AUTHORIZATION.OVERRIDE.MANAGE")
    public void grantModuleCapabilityOverrides(
            Authentication authentication,
            @RequestParam UUID tenantId,
            @RequestBody ModuleCapabilityGrantRequest request) {
        UUID authenticatedTenant = AccessPrincipalContext.requireTenantId(authentication);
        if (!authenticatedTenant.equals(tenantId)) {
            throw new AccessDeniedException("Cross-tenant module capability override denied");
        }
        moduleProvisioningService.grantCapabilities(
                tenantId,
                AccessPrincipalContext.requireUserId(authentication),
                request.targetUserId(),
                request.routeRoot(),
                request.capabilityCodes());
    }

    @GetMapping("/module-access-users")
    @RequireCapability("USER.READ")
    public List<ModuleUserAccessProjection> moduleAccessUsers(
            Authentication authentication,
            @RequestParam String routeRoot) {
        UUID tenantId = AccessPrincipalContext.requireTenantId(authentication);
        return projectionService.projectModuleUsers(tenantId, routeRoot);
    }

    @GetMapping("/module-provisioning-context")
    @RequireCapability("USER.CREATE")
    public ModuleProvisioningContext provisioningContext(
            Authentication authentication,
            @RequestParam UUID tenantId,
            @RequestParam String routeRoot) {
        UUID authenticatedTenant = AccessPrincipalContext.requireTenantId(authentication);
        if (!authenticatedTenant.equals(tenantId)) {
            throw new AccessDeniedException("Cross-tenant module provisioning read denied");
        }
        return moduleProvisioningService.resolve(tenantId, routeRoot);
    }
}
