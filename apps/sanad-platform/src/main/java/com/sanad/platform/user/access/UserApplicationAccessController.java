package com.sanad.platform.user.access;

import com.sanad.platform.access.api.AccessPrincipalContext;
import com.sanad.platform.security.authorization.RequireCapability;
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

    public UserApplicationAccessController(
            UserApplicationAccessProjectionService projectionService,
            ModuleUserProvisioningService moduleProvisioningService) {
        this.projectionService = projectionService;
        this.moduleProvisioningService = moduleProvisioningService;
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
    @RequireCapability("USER.GRANT_ROLE")
    public ModuleProvisioningContext moduleContext(
            Authentication authentication,
            @RequestParam String routeRoot) {
        UUID tenantId = AccessPrincipalContext.requireTenantId(authentication);
        return moduleProvisioningService.resolve(tenantId, routeRoot);
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

    @GetMapping("/module-access-users")
    @RequireCapability("USER.READ")
    public List<ModuleUserAccessProjection> moduleAccessUsers(
            Authentication authentication,
            @RequestParam String routeRoot) {
        UUID tenantId = AccessPrincipalContext.requireTenantId(authentication);
        return projectionService.projectModuleUsers(tenantId, routeRoot);
    }

    @GetMapping("/module-provisioning-context")
    @RequireCapability("USER.GRANT_ROLE")
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
