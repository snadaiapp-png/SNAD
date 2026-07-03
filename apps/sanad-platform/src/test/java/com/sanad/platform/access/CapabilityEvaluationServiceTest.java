package com.sanad.platform.access;

import com.sanad.platform.access.capability.*;
import com.sanad.platform.access.evaluation.CapabilityEvaluationService;
import com.sanad.platform.access.grant.*;
import com.sanad.platform.access.role.*;
import com.sanad.platform.organization.repository.OrganizationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CapabilityEvaluationServiceTest {

    @Mock private UserRoleGrantService grants;
    @Mock private RoleService roles;
    @Mock private RoleCapabilityService mappings;
    @Mock private AccessCapabilityService capabilities;
    @Mock private OrganizationRepository organizations;

    private CapabilityEvaluationService service;
    private UUID tenantId;
    private UUID userId;
    private UUID roleId;
    private UUID orgId;
    private UUID otherOrgId;
    private UUID capabilityId;
    private Role role;
    private AccessCapability capability;

    @BeforeEach
    void setUp() {
        service = new CapabilityEvaluationService(
                grants, roles, mappings, capabilities, organizations);
        tenantId = UUID.randomUUID();
        userId = UUID.randomUUID();
        roleId = UUID.randomUUID();
        orgId = UUID.randomUUID();
        otherOrgId = UUID.randomUUID();
        capabilityId = UUID.randomUUID();
        role = new Role(tenantId, "ADMIN", "Admin", null);
        capability = new AccessCapability("USER.READ", "Read users", null);
        ReflectionTestUtils.setField(role, "id", roleId);
        ReflectionTestUtils.setField(capability, "id", capabilityId);
    }

    @Test
    void tenantGrantAllowsTenantScope() {
        allow(new UserRoleGrant(tenantId, userId, roleId, null));
        assertThat(service.evaluate(tenantId, userId, "USER.READ", null).allowed()).isTrue();
    }

    @Test
    void tenantGrantAllowsOrganizationScope() {
        organizationExists(orgId);
        allow(new UserRoleGrant(tenantId, userId, roleId, null));
        assertThat(service.evaluate(tenantId, userId, "USER.READ", orgId).allowed()).isTrue();
    }

    @Test
    void organizationGrantAllowsMatchingScope() {
        organizationExists(orgId);
        allow(new UserRoleGrant(tenantId, userId, roleId, orgId));
        assertThat(service.evaluate(tenantId, userId, "USER.READ", orgId).allowed()).isTrue();
    }

    @Test
    void organizationGrantDoesNotAllowTenantScope() {
        loadCapability();
        when(grants.activeGrants(tenantId, userId)).thenReturn(List.of(
                new UserRoleGrant(tenantId, userId, roleId, orgId)));
        assertThat(service.evaluate(tenantId, userId, "USER.READ", null).allowed()).isFalse();
    }

    @Test
    void organizationGrantDoesNotAllowOtherScope() {
        organizationExists(otherOrgId);
        loadCapability();
        when(grants.activeGrants(tenantId, userId)).thenReturn(List.of(
                new UserRoleGrant(tenantId, userId, roleId, orgId)));
        assertThat(service.evaluate(tenantId, userId, "USER.READ", otherOrgId).allowed()).isFalse();
    }

    @Test
    void unknownCapabilityIsDenied() {
        when(capabilities.loadByCode("UNKNOWN"))
                .thenThrow(new AccessResourceNotFoundException("Capability not found"));
        AccessDecisionResponse result = service.evaluate(tenantId, userId, "UNKNOWN", null);
        assertThat(result.allowed()).isFalse();
        assertThat(result.reason()).isEqualTo("CAPABILITY_NOT_FOUND");
    }

    @Test
    void inactiveCapabilityIsDenied() {
        capability.setStatus(CapabilityStatus.INACTIVE);
        loadCapability();
        assertThat(service.evaluate(tenantId, userId, "USER.READ", null).allowed()).isFalse();
    }

    @Test
    void inactiveRoleIsIgnored() {
        role.setStatus(RoleStatus.INACTIVE);
        loadCapability();
        when(grants.activeGrants(tenantId, userId)).thenReturn(List.of(
                new UserRoleGrant(tenantId, userId, roleId, null)));
        when(roles.load(tenantId, roleId)).thenReturn(role);
        assertThat(service.evaluate(tenantId, userId, "USER.READ", null).allowed()).isFalse();
    }

    @Test
    void missingMappingIsDenied() {
        loadCapability();
        when(grants.activeGrants(tenantId, userId)).thenReturn(List.of(
                new UserRoleGrant(tenantId, userId, roleId, null)));
        when(roles.load(tenantId, roleId)).thenReturn(role);
        when(mappings.roleHasCapability(tenantId, roleId, capabilityId)).thenReturn(false);
        assertThat(service.evaluate(tenantId, userId, "USER.READ", null).allowed()).isFalse();
    }

    @Test
    void unknownOrganizationIsRejected() {
        when(organizations.findByTenantIdAndId(tenantId, orgId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.evaluate(tenantId, userId, "USER.READ", orgId))
                .isInstanceOf(AccessResourceNotFoundException.class);
    }

    private void allow(UserRoleGrant grant) {
        loadCapability();
        when(grants.activeGrants(tenantId, userId)).thenReturn(List.of(grant));
        when(roles.load(tenantId, roleId)).thenReturn(role);
        when(mappings.roleHasCapability(tenantId, roleId, capabilityId)).thenReturn(true);
    }

    private void loadCapability() {
        when(capabilities.loadByCode("USER.READ")).thenReturn(capability);
    }

    private void organizationExists(UUID id) {
        when(organizations.findByTenantIdAndId(tenantId, id))
                .thenReturn(Optional.of(mock(
                        com.sanad.platform.organization.domain.Organization.class)));
    }
}
