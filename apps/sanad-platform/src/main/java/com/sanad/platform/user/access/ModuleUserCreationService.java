package com.sanad.platform.user.access;

import com.sanad.platform.access.evaluation.CapabilityEvaluationService;
import com.sanad.platform.user.dto.UserResponse;
import com.sanad.platform.user.service.UserService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Creates a tenant user and grants the requested module access in one
 * transaction. Any permission-grant failure rolls the user creation back,
 * preventing orphaned users after a partially completed provisioning flow.
 */
@Service
public class ModuleUserCreationService {

    private final UserService userService;
    private final ModuleUserProvisioningService moduleProvisioningService;
    private final CapabilityEvaluationService capabilityEvaluationService;

    public ModuleUserCreationService(
            UserService userService,
            ModuleUserProvisioningService moduleProvisioningService,
            CapabilityEvaluationService capabilityEvaluationService) {
        this.userService = userService;
        this.moduleProvisioningService = moduleProvisioningService;
        this.capabilityEvaluationService = capabilityEvaluationService;
    }

    @Transactional
    public UserResponse provision(
            UUID tenantId,
            UUID actorUserId,
            ModuleUserProvisionRequest request) {
        if (tenantId == null || actorUserId == null) {
            throw new IllegalArgumentException("subject context is required");
        }
        if (request == null) {
            throw new IllegalArgumentException("provisioning request is required");
        }
        if (!capabilityEvaluationService.evaluate(
                tenantId, actorUserId, "USER.CREATE", null).allowed()) {
            throw new AccessDeniedException("USER.CREATE is required for atomic module user provisioning");
        }

        // Resolve and validate the module contract before persisting the user so
        // invalid route roots / cross-module capabilities cannot create a user.
        ModuleProvisioningContext context =
                moduleProvisioningService.resolve(tenantId, request.routeRoot());
        var requested = request.capabilityCodes().stream()
                .filter(code -> code != null && !code.isBlank())
                .map(code -> code.trim().toUpperCase(java.util.Locale.ROOT))
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
        if (requested.isEmpty()) {
            throw new IllegalArgumentException("At least one module capability is required");
        }
        if (!context.declaredCapabilities().containsAll(requested)) {
            throw new IllegalArgumentException("Cross-module capability grant denied");
        }

        UserResponse created = userService.createUser(tenantId, request.user());
        moduleProvisioningService.grantCapabilities(
                tenantId,
                actorUserId,
                created.getId(),
                request.routeRoot(),
                java.util.List.copyOf(requested));
        return created;
    }
}
