package com.sanad.platform.access.role;

import com.sanad.platform.access.capability.AccessCapability;
import com.sanad.platform.access.capability.AccessCapabilityService;
import com.sanad.platform.access.capability.CapabilityStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class RoleCapabilityService {

    private final RoleCapabilityRepository mappingRepository;
    private final RoleService roleService;
    private final AccessCapabilityService capabilityService;

    public RoleCapabilityService(
            RoleCapabilityRepository mappingRepository,
            RoleService roleService,
            AccessCapabilityService capabilityService) {
        this.mappingRepository = mappingRepository;
        this.roleService = roleService;
        this.capabilityService = capabilityService;
    }

    @Transactional
    public RoleAccessResponse attach(UUID tenantId, UUID roleId, UUID capabilityId) {
        Role role = roleService.load(tenantId, roleId);
        AccessCapability capability = capabilityService.load(capabilityId);
        if (role.getStatus() != RoleStatus.ACTIVE) {
            throw new IllegalStateException("Only active roles can receive capabilities");
        }
        if (capability.getStatus() != CapabilityStatus.ACTIVE) {
            throw new IllegalStateException("Only active capabilities can be attached");
        }

        RoleCapability mapping = mappingRepository
                .findByTenantIdAndRoleIdAndCapabilityId(tenantId, roleId, capabilityId)
                .orElseGet(() -> mappingRepository.save(
                        new RoleCapability(tenantId, roleId, capabilityId)));
        return response(mapping, capability.getCode());
    }

    @Transactional
    public void detach(UUID tenantId, UUID roleId, UUID capabilityId) {
        roleService.load(tenantId, roleId);
        RoleCapability mapping = mappingRepository
                .findByTenantIdAndRoleIdAndCapabilityId(tenantId, roleId, capabilityId)
                .orElse(null);
        if (mapping != null) {
            mappingRepository.delete(mapping);
        }
    }

    @Transactional(readOnly = true)
    public List<RoleAccessResponse> list(UUID tenantId, UUID roleId) {
        roleService.load(tenantId, roleId);
        return mappingRepository.findByTenantIdAndRoleId(tenantId, roleId).stream()
                .map(mapping -> {
                    AccessCapability capability = capabilityService.load(mapping.getCapabilityId());
                    return response(mapping, capability.getCode());
                }).toList();
    }

    public boolean roleHasCapability(UUID tenantId, UUID roleId, UUID capabilityId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        return mappingRepository.existsByTenantIdAndRoleIdAndCapabilityId(
                tenantId, roleId, capabilityId);
    }

    private static RoleAccessResponse response(RoleCapability mapping, String code) {
        return new RoleAccessResponse(mapping.getId(), mapping.getTenantId(),
                mapping.getRoleId(), mapping.getCapabilityId(), code,
                mapping.getCreatedAt());
    }
}
