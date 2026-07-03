package com.sanad.platform.access.evaluation;

import com.sanad.platform.access.AccessDecisionResponse;
import com.sanad.platform.access.AccessResourceNotFoundException;
import com.sanad.platform.access.capability.AccessCapability;
import com.sanad.platform.access.capability.AccessCapabilityService;
import com.sanad.platform.access.capability.CapabilityStatus;
import com.sanad.platform.access.grant.UserRoleGrant;
import com.sanad.platform.access.grant.UserRoleGrantService;
import com.sanad.platform.access.role.Role;
import com.sanad.platform.access.role.RoleCapabilityService;
import com.sanad.platform.access.role.RoleService;
import com.sanad.platform.access.role.RoleStatus;
import com.sanad.platform.organization.repository.OrganizationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.UUID;

@Service
public class CapabilityEvaluationService {

    private final UserRoleGrantService grantService;
    private final RoleService roleService;
    private final RoleCapabilityService roleCapabilityService;
    private final AccessCapabilityService capabilityService;
    private final OrganizationRepository organizationRepository;

    public CapabilityEvaluationService(
            UserRoleGrantService grantService,
            RoleService roleService,
            RoleCapabilityService roleCapabilityService,
            AccessCapabilityService capabilityService,
            OrganizationRepository organizationRepository) {
        this.grantService = grantService;
        this.roleService = roleService;
        this.roleCapabilityService = roleCapabilityService;
        this.capabilityService = capabilityService;
        this.organizationRepository = organizationRepository;
    }

    @Transactional(readOnly = true)
    public AccessDecisionResponse evaluate(
            UUID tenantId, UUID userId, String capabilityCode, UUID organizationId) {
        validateOrganization(tenantId, organizationId);

        AccessCapability capability;
        try {
            capability = capabilityService.loadByCode(capabilityCode);
        } catch (AccessResourceNotFoundException exception) {
            return denied(tenantId, userId, organizationId,
                    normalize(capabilityCode), "CAPABILITY_NOT_FOUND");
        }

        if (capability.getStatus() != CapabilityStatus.ACTIVE) {
            return denied(tenantId, userId, organizationId,
                    capability.getCode(), "CAPABILITY_INACTIVE");
        }

        for (UserRoleGrant grant : grantService.activeGrants(tenantId, userId)) {
            if (!scopeMatches(grant, organizationId)) continue;
            Role role = roleService.load(tenantId, grant.getRoleId());
            if (role.getStatus() != RoleStatus.ACTIVE) continue;
            if (roleCapabilityService.roleHasCapability(
                    tenantId, role.getId(), capability.getId())) {
                return new AccessDecisionResponse(tenantId, userId, organizationId,
                        capability.getCode(), true, "ROLE_CAPABILITY_MATCH",
                        role.getId(), role.getCode());
            }
        }

        return denied(tenantId, userId, organizationId,
                capability.getCode(), "NO_MATCHING_ACTIVE_ROLE");
    }

    private void validateOrganization(UUID tenantId, UUID organizationId) {
        if (organizationId != null
                && organizationRepository.findByTenantIdAndId(tenantId, organizationId).isEmpty()) {
            throw new AccessResourceNotFoundException("Organization not found");
        }
    }

    private static boolean scopeMatches(UserRoleGrant grant, UUID organizationId) {
        if (organizationId == null) return grant.isTenantWide();
        return grant.isTenantWide() || organizationId.equals(grant.getOrganizationId());
    }

    private static AccessDecisionResponse denied(
            UUID tenantId, UUID userId, UUID organizationId, String code, String reason) {
        return new AccessDecisionResponse(tenantId, userId, organizationId,
                code, false, reason, null, null);
    }

    private static String normalize(String value) {
        return value == null ? null : value.trim().toUpperCase(Locale.ROOT);
    }
}
