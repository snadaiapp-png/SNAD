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

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CapabilityEvaluationServiceTest {

    @Mock private UserRoleGrantService grantService;
    @Mock private RoleService roleService;
    @Mock private RoleCapabilityService mappingService;
    @Mock private AccessCapabilityService capabilityService;
    @Mock private OrganizationRepository organizationRepository;

    private CapabilityEvaluationService service;
    private UUID tenantId;
    private UUID userId;
    private UUID roleId;
    private UUID organizationId;
    private UUID otherOrganizationId;
    private UUID capabilityId;
    private Role role;
    private AccessCapability capability;

    @BeforeEach
    void setUp() {
        service = new CapabilityEvaluationService(
                grantService, roleService, mappingService,
                capabilityService, organizationRepository);
        tenantId = UUID.randomUUID();
        userId = UUID.randomUUID();
        roleId = UUID.randomUUID();
        organizationId = UUID.randomUUID();
        otherOrganizationId = UUID.randomUUID();
        capabilityId = UUID.randomUUID();
        role = new Role(tenantId, "ADMIN", "Admin", null);
        capability = new AccessCapability("USER.READ", "Read users", null);
    }

    @Test
    void tenantWideGrantAllowsTenantEvaluation() {
        allowSetup(new UserRoleGrant(tenantId, userId, roleId, null));
        AccessDecisionResponse result = service.evaluate(
                tenantId, userId, "USER.READ", null);
        assertThat(result.allowed()).isTrue();
        assertThat(result.matchedRoleCode()).isEqualTo("ADMIN");
    }

    @Test
    void tenantWideGrantAllowsOrganizationEvaluation() {
        organizationExists(organizationId);
        allowSetup(new UserRoleGrant(tenantId, userId, roleId, null));
        assertThat(service.evaluate(
                tenantId, userId, "USER.READ", organizationId).allowed()).isTrue();
    }

    @Test
    void organizationGrantAllowsMatchingOrganization() {
        organizationExists(organizationId);
        allowSetup(new UserRoleGrant(tenantId, userId, roleId, organizationId));
        assertThat(service.evaluate(
                tenantId, userId, "USER.READ", organizationId).allowed()).isTrue();
    }

    @Test
    void organizationGrantDoesNotAllowTenantEvaluation() {
        loadCapability();
        when(grantService.activeGrants(tenantId, userId)).thenReturn(List.of(
                new UserRoleGrant(tenantId, userId, roleId, organizationId)));
        assertThat(service.evaluate(tenantId, userId, "USER.READ", null).allowed()).isFalse();
    }

    @Test
    void organizationGrantDoesNotAllowOtherOrganization() {
        organizationExists(otherOrganizationId);
        loadCapability();
        when(grantService.activeGrants(tenantId, userId)).thenReturn(List.of(
                new UserRoleGrant(tenantId, userId, roleId, organizationId)));
        assertThat(service.evaluate(
                tenantId, userId, "USER.READ", otherOrganizationId).allowed()).isFalse();
    }

    @Test
    void unknownCapabilityIsDenied() {
        when(capabilityService.loadByCode("UNKNOWN"))
                .thenThrow(new AccessResourceNotFoundException("Capability not found"));
        AccessDecisionResponse result = service.evaluate(
                tenantId, userId, "UNKNOWN", null);
        assertThat(result.allowed()).isFalse();
        assertThat(result.reason()).isEqualTo("CAPABILITY_NOT_FOUND");
    }

    @Test
    void inactiveCapabilityIsDenied() {
        capability.setStatus(CapabilityStatus.INACTIVE);
        loadCapability();
        AccessDecisionResponse result = service.evaluate(
                tenantId, userId, "USER.READ", null);
        assertThat(result.allowed()).isFalse();
        assertThat(result.reason()).isEqualTo("CAPABILITY_INACTIVE");
    }

    @Test
    void inactiveRoleIsIgnored() {
        role.setStatus(RoleStatus.INACTIVE);
        loadCapability();
        when(grantService.activeGrants(tenantId, userId)).thenReturn(List.of(
                new UserRoleGrant(tenantId, userId, roleId, null)));
        when(roleService.load(tenantId, roleId)).thenReturn(role);
        assertThat(service.evaluate(tenantId, userId, "USER.READ", null).allowed()).isFalse();
    }

    @Test
    void missingRoleCapabilityIsDenied() {
        loadCapability();
        when(grantService.activeGrants(tenantId, userId)).thenReturn(List.of(
                new UserRoleGrant(tenantId, userId, roleId, null)));
        when(roleService.load(tenantId, roleId)).thenReturn(role);
        when(mappingService.roleHasCapability(tenantId, roleId, capabilityId)).thenReturn(false);
        assertThat(service.evaluate(tenantId, userId, "USER.READ", null).allowed()).isFalse();
    }

    @Test
    void crossTenantOrganizationIsRejected() {
        when(organizationRepository.findByTenantIdAndId(tenantId, organizationId))
                .thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.evaluate(
                tenantId, userId, "USER.READ", organizationId))
                .isInstanceOf(AccessResourceNotFoundException.class)
                .hasMessage("Organization not found");
    }

    private void allowSetup(UserRoleGrant grant) {
        loadCapability();
        when(grantService.activeGrants(tenantId, userId)).thenReturn(List.of(grant));
        when(roleService.load(tenantId, roleId)).thenReturn(role);
        when(mappingService.roleHasCapability(tenantId, roleId, capabilityId)).thenReturn(true);
    }

    private void loadCapability() {
        when(capabilityService.loadByCode("USER.READ")).thenReturn(capability);
    }

    private void organizationExists(UUID id) {
        when(organizationRepository.findByTenantIdAndId(tenantId, id))
                .thenReturn(Optional.of(org.mockito.Mockito.mock(
                        com.sanad.platform.organization.domain.Organization.class)));
    }
}
