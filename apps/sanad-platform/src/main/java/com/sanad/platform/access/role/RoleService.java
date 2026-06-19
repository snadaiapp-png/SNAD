package com.sanad.platform.access.role;

import com.sanad.platform.access.AccessConflictException;
import com.sanad.platform.access.AccessResourceNotFoundException;
import com.sanad.platform.tenant.repository.TenantRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Service
public class RoleService {

    private final RoleRepository roleRepository;
    private final TenantRepository tenantRepository;

    public RoleService(RoleRepository roleRepository, TenantRepository tenantRepository) {
        this.roleRepository = roleRepository;
        this.tenantRepository = tenantRepository;
    }

    @Transactional
    public RoleResponse create(UUID tenantId, CreateRoleRequest request) {
        requireTenant(tenantId);
        String code = normalizeCode(request.code());
        if (roleRepository.existsByTenantIdAndCode(tenantId, code)) {
            throw new AccessConflictException("Role code already exists in tenant: " + code);
        }
        Role role = new Role(tenantId, code, request.name(), request.description());
        return RoleResponse.from(roleRepository.save(role));
    }

    @Transactional(readOnly = true)
    public List<RoleResponse> list(UUID tenantId) {
        requireTenant(tenantId);
        return roleRepository.findByTenantIdOrderByCodeAsc(tenantId).stream()
                .map(RoleResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public RoleResponse get(UUID tenantId, UUID roleId) {
        return RoleResponse.from(load(tenantId, roleId));
    }

    @Transactional
    public RoleResponse update(UUID tenantId, UUID roleId, UpdateRoleRequest request) {
        Role role = load(tenantId, roleId);
        role.setName(request.name());
        role.setDescription(request.description());
        return RoleResponse.from(roleRepository.save(role));
    }

    @Transactional
    public RoleResponse changeStatus(UUID tenantId, UUID roleId, RoleStatus status) {
        Role role = load(tenantId, roleId);
        role.setStatus(Objects.requireNonNull(status, "status must not be null"));
        return RoleResponse.from(roleRepository.save(role));
    }

    public Role load(UUID tenantId, UUID roleId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(roleId, "roleId must not be null");
        return roleRepository.findByTenantIdAndId(tenantId, roleId)
                .orElseThrow(() -> new AccessResourceNotFoundException("Role not found"));
    }

    private void requireTenant(UUID tenantId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        if (!tenantRepository.existsById(tenantId)) {
            throw new AccessResourceNotFoundException("Tenant not found");
        }
    }

    private static String normalizeCode(String code) {
        return code == null ? null : code.trim().toUpperCase(Locale.ROOT);
    }
}
