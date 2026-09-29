package com.sanad.platform.access.role;

import com.sanad.platform.access.audit.AccessMutationAuditSupport;
import com.sanad.platform.access.capability.AccessCapability;
import com.sanad.platform.access.capability.AccessCapabilityService;
import com.sanad.platform.access.capability.CapabilityStatus;
import com.sanad.platform.security.authorization.AuthorizationMutationCoordinator;
import com.sanad.platform.security.authorization.ProtectedRoleGuard;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
public class RoleCapabilityService {
    private final RoleCapabilityRepository mappingRepository;
    private final RoleService roleService;
    private final AccessCapabilityService capabilityService;
    private AccessMutationAuditSupport audit;
    private AuthorizationMutationCoordinator authChanges;
    private ProtectedRoleGuard protectedRoleGuard;

    public RoleCapabilityService(RoleCapabilityRepository mappingRepository, RoleService roleService,
                                 AccessCapabilityService capabilityService) {
        this.mappingRepository = mappingRepository; this.roleService = roleService; this.capabilityService = capabilityService;
    }
    @Autowired(required = false) void setAudit(AccessMutationAuditSupport audit) { this.audit = audit; }
    @Autowired(required = false) void setAuthChanges(AuthorizationMutationCoordinator authChanges) { this.authChanges = authChanges; }
    @Autowired(required = false) void setProtectedRoleGuard(ProtectedRoleGuard guard) { this.protectedRoleGuard = guard; }

    @Transactional
    public RoleAccessResponse attach(UUID tenantId, UUID roleId, UUID capabilityId) {
        Role role = roleService.load(tenantId, roleId);
        AccessCapability capability = capabilityService.load(capabilityId);
        if (role.getStatus() != RoleStatus.ACTIVE) throw new IllegalStateException("Only active roles can receive capabilities");
        if (capability.getStatus() != CapabilityStatus.ACTIVE) throw new IllegalStateException("Only active capabilities can be attached");
        java.util.Optional<RoleCapability> existing = mappingRepository.findByTenantIdAndRoleIdAndCapabilityId(tenantId, roleId, capabilityId);
        RoleCapability mapping = existing.orElseGet(() -> mappingRepository.save(new RoleCapability(tenantId, roleId, capabilityId)));
        audit(tenantId, "ROLE_CAPABILITY_ATTACH", mapping.getId(), null,
                Map.of("roleId", roleId, "capabilityId", capabilityId));
        if (existing.isEmpty() && authChanges != null) authChanges.roleChanged(tenantId, roleId, "ROLE_CAPABILITY_CHANGED");
        return response(mapping, capability.getCode());
    }

    @Transactional
    public void detach(UUID tenantId, UUID roleId, UUID capabilityId) {
        roleService.load(tenantId, roleId);
        if (protectedRoleGuard != null) protectedRoleGuard.assertMayDetachCapability(tenantId, roleId);
        RoleCapability mapping = mappingRepository.findByTenantIdAndRoleIdAndCapabilityId(tenantId, roleId, capabilityId).orElse(null);
        if (mapping != null) {
            mappingRepository.delete(mapping);
            audit(tenantId, "ROLE_CAPABILITY_DETACH", mapping.getId(),
                    Map.of("roleId", roleId, "capabilityId", capabilityId), null);
            if (authChanges != null) authChanges.roleChanged(tenantId, roleId, "ROLE_CAPABILITY_CHANGED");
        }
    }

    @Transactional(readOnly = true)
    public List<RoleAccessResponse> list(UUID tenantId, UUID roleId) {
        roleService.load(tenantId, roleId);
        return mappingRepository.findByTenantIdAndRoleId(tenantId, roleId).stream()
                .map(mapping -> response(mapping, capabilityService.load(mapping.getCapabilityId()).getCode())).toList();
    }
    public boolean roleHasCapability(UUID tenantId, UUID roleId, UUID capabilityId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        return mappingRepository.existsByTenantIdAndRoleIdAndCapabilityId(tenantId, roleId, capabilityId);
    }
    private void audit(UUID tenantId, String action, UUID id, Object before, Object after) {
        if (audit != null) audit.success(tenantId, action, "ROLE_CAPABILITY", id == null ? null : id.toString(), before, after);
    }
    private static RoleAccessResponse response(RoleCapability mapping, String code) {
        return new RoleAccessResponse(mapping.getId(), mapping.getTenantId(), mapping.getRoleId(),
                mapping.getCapabilityId(), code, mapping.getCreatedAt());
    }
}
