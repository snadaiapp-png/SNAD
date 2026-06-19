package com.sanad.platform.access.grant;

import com.sanad.platform.access.AccessResourceNotFoundException;
import com.sanad.platform.access.UserAccessResponse;
import com.sanad.platform.access.role.Role;
import com.sanad.platform.access.role.RoleService;
import com.sanad.platform.access.role.RoleStatus;
import com.sanad.platform.organization.repository.OrganizationRepository;
import com.sanad.platform.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class UserRoleGrantService {

    private final UserRoleGrantRepository grantRepository;
    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;
    private final RoleService roleService;

    public UserRoleGrantService(
            UserRoleGrantRepository grantRepository,
            UserRepository userRepository,
            OrganizationRepository organizationRepository,
            RoleService roleService) {
        this.grantRepository = grantRepository;
        this.userRepository = userRepository;
        this.organizationRepository = organizationRepository;
        this.roleService = roleService;
    }

    @Transactional
    public UserAccessResponse grant(
            UUID tenantId, UUID userId, UUID roleId, UUID organizationId) {
        requireUser(tenantId, userId);
        Role role = roleService.load(tenantId, roleId);
        if (role.getStatus() != RoleStatus.ACTIVE) {
            throw new IllegalStateException("Only active roles can be granted");
        }
        requireOrganization(tenantId, organizationId);

        Optional<UserRoleGrant> existing = organizationId == null
                ? grantRepository.findByTenantIdAndUserIdAndRoleIdAndOrganizationIdIsNull(
                        tenantId, userId, roleId)
                : grantRepository.findByTenantIdAndUserIdAndRoleIdAndOrganizationId(
                        tenantId, userId, roleId, organizationId);

        UserRoleGrant grant = existing.orElseGet(() ->
                new UserRoleGrant(tenantId, userId, roleId, organizationId));
        if (grant.getStatus() == UserGrantStatus.REVOKED) {
            grant.setStatus(UserGrantStatus.ACTIVE);
        }
        if (grant.getId() == null || existing.isPresent()) {
            grant = grantRepository.save(grant);
        }
        return response(grant, role.getCode());
    }

    @Transactional
    public UserAccessResponse revoke(UUID tenantId, UUID grantId) {
        UserRoleGrant grant = grantRepository.findByTenantIdAndId(tenantId, grantId)
                .orElseThrow(() -> new AccessResourceNotFoundException("User role grant not found"));
        grant.setStatus(UserGrantStatus.REVOKED);
        grant = grantRepository.save(grant);
        Role role = roleService.load(tenantId, grant.getRoleId());
        return response(grant, role.getCode());
    }

    @Transactional(readOnly = true)
    public List<UserAccessResponse> list(UUID tenantId, UUID userId) {
        requireUser(tenantId, userId);
        return grantRepository.findByTenantIdAndUserIdOrderByCreatedAtAsc(tenantId, userId)
                .stream()
                .map(grant -> response(
                        grant, roleService.load(tenantId, grant.getRoleId()).getCode()))
                .toList();
    }

    public List<UserRoleGrant> activeGrants(UUID tenantId, UUID userId) {
        requireUser(tenantId, userId);
        return grantRepository.findByTenantIdAndUserIdAndStatus(
                tenantId, userId, UserGrantStatus.ACTIVE);
    }

    private void requireUser(UUID tenantId, UUID userId) {
        if (tenantId == null || userId == null
                || userRepository.findByTenantIdAndId(tenantId, userId).isEmpty()) {
            throw new AccessResourceNotFoundException("User not found");
        }
    }

    private void requireOrganization(UUID tenantId, UUID organizationId) {
        if (organizationId != null
                && organizationRepository.findByTenantIdAndId(tenantId, organizationId).isEmpty()) {
            throw new AccessResourceNotFoundException("Organization not found");
        }
    }

    private static UserAccessResponse response(UserRoleGrant grant, String roleCode) {
        return new UserAccessResponse(grant.getId(), grant.getTenantId(), grant.getUserId(),
                grant.getRoleId(), roleCode, grant.getOrganizationId(), grant.getStatus(),
                grant.getCreatedAt(), grant.getUpdatedAt());
    }
}
