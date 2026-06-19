package com.sanad.platform.access;

import com.sanad.platform.access.grant.*;
import com.sanad.platform.access.role.*;
import com.sanad.platform.organization.domain.Organization;
import com.sanad.platform.organization.repository.OrganizationRepository;
import com.sanad.platform.tenant.domain.Tenant;
import com.sanad.platform.tenant.domain.TenantStatus;
import com.sanad.platform.user.domain.User;
import com.sanad.platform.user.domain.UserStatus;
import com.sanad.platform.user.repository.UserRepository;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserRoleGrantServiceTest {

    @Mock private UserRoleGrantRepository grantRepository;
    @Mock private UserRepository userRepository;
    @Mock private OrganizationRepository organizationRepository;
    @Mock private RoleService roleService;

    private UserRoleGrantService service;
    private UUID tenantId;
    private UUID userId;
    private UUID roleId;
    private UUID organizationId;
    private UUID grantId;
    private Role role;
    private User user;

    @BeforeEach
    void setUp() {
        service = new UserRoleGrantService(
                grantRepository, userRepository, organizationRepository, roleService);
        tenantId = UUID.randomUUID();
        userId = UUID.randomUUID();
        roleId = UUID.randomUUID();
        organizationId = UUID.randomUUID();
        grantId = UUID.randomUUID();
        role = new Role(tenantId, "ADMIN", "Admin", null);
        user = new User(tenantId, "user@example.com", "User", UserStatus.ACTIVE);
    }

    @Test
    void tenantWideGrantPersists() {
        validUserAndRole();
        when(grantRepository.findByTenantIdAndUserIdAndRoleIdAndOrganizationIdIsNull(
                tenantId, userId, roleId)).thenReturn(Optional.empty());
        when(grantRepository.save(any(UserRoleGrant.class)))
                .thenAnswer(call -> call.getArgument(0));

        UserAccessResponse response = service.grant(tenantId, userId, roleId, null);
        assertThat(response.organizationId()).isNull();
        assertThat(response.status()).isEqualTo(UserGrantStatus.ACTIVE);
    }

    @Test
    void organizationScopedGrantValidatesOrganization() {
        validUserAndRole();
        Tenant tenant = new Tenant("Tenant", "tenant-" + UUID.randomUUID(), TenantStatus.ACTIVE);
        Organization organization = new Organization(tenant, "Org", null,
                com.sanad.platform.organization.domain.OrganizationStatus.ACTIVE);
        when(organizationRepository.findByTenantIdAndId(tenantId, organizationId))
                .thenReturn(Optional.of(organization));
        when(grantRepository.findByTenantIdAndUserIdAndRoleIdAndOrganizationId(
                tenantId, userId, roleId, organizationId)).thenReturn(Optional.empty());
        when(grantRepository.save(any(UserRoleGrant.class)))
                .thenAnswer(call -> call.getArgument(0));

        assertThat(service.grant(tenantId, userId, roleId, organizationId).organizationId())
                .isEqualTo(organizationId);
    }

    @Test
    void existingActiveGrantIsIdempotent() {
        UserRoleGrant existing = new UserRoleGrant(tenantId, userId, roleId, null);
        validUserAndRole();
        when(grantRepository.findByTenantIdAndUserIdAndRoleIdAndOrganizationIdIsNull(
                tenantId, userId, roleId)).thenReturn(Optional.of(existing));
        when(grantRepository.save(existing)).thenReturn(existing);

        assertThat(service.grant(tenantId, userId, roleId, null).status())
                .isEqualTo(UserGrantStatus.ACTIVE);
    }

    @Test
    void revokedGrantIsReactivated() {
        UserRoleGrant existing = new UserRoleGrant(tenantId, userId, roleId, null);
        existing.setStatus(UserGrantStatus.REVOKED);
        validUserAndRole();
        when(grantRepository.findByTenantIdAndUserIdAndRoleIdAndOrganizationIdIsNull(
                tenantId, userId, roleId)).thenReturn(Optional.of(existing));
        when(grantRepository.save(existing)).thenReturn(existing);

        assertThat(service.grant(tenantId, userId, roleId, null).status())
                .isEqualTo(UserGrantStatus.ACTIVE);
    }

    @Test
    void inactiveRoleCannotBeGranted() {
        role.setStatus(RoleStatus.INACTIVE);
        validUserAndRole();
        assertThatThrownBy(() -> service.grant(tenantId, userId, roleId, null))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void unknownUserIsRejected() {
        when(userRepository.findByTenantIdAndId(tenantId, userId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.grant(tenantId, userId, roleId, null))
                .isInstanceOf(AccessResourceNotFoundException.class)
                .hasMessage("User not found");
        verifyNoInteractions(roleService);
    }

    @Test
    void unknownOrganizationIsRejected() {
        validUserAndRole();
        when(organizationRepository.findByTenantIdAndId(tenantId, organizationId))
                .thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.grant(
                tenantId, userId, roleId, organizationId))
                .isInstanceOf(AccessResourceNotFoundException.class)
                .hasMessage("Organization not found");
    }

    @Test
    void revokePersistsRevokedStatus() {
        UserRoleGrant grant = new UserRoleGrant(tenantId, userId, roleId, null);
        when(grantRepository.findByTenantIdAndId(tenantId, grantId))
                .thenReturn(Optional.of(grant));
        when(grantRepository.save(grant)).thenReturn(grant);
        when(roleService.load(tenantId, roleId)).thenReturn(role);

        assertThat(service.revoke(tenantId, grantId).status())
                .isEqualTo(UserGrantStatus.REVOKED);
    }

    @Test
    void listIsTenantAndUserScoped() {
        UserRoleGrant grant = new UserRoleGrant(tenantId, userId, roleId, null);
        when(userRepository.findByTenantIdAndId(tenantId, userId)).thenReturn(Optional.of(user));
        when(grantRepository.findByTenantIdAndUserIdOrderByCreatedAtAsc(tenantId, userId))
                .thenReturn(List.of(grant));
        when(roleService.load(tenantId, roleId)).thenReturn(role);

        assertThat(service.list(tenantId, userId))
                .extracting(UserAccessResponse::roleCode).containsExactly("ADMIN");
    }

    private void validUserAndRole() {
        when(userRepository.findByTenantIdAndId(tenantId, userId)).thenReturn(Optional.of(user));
        when(roleService.load(tenantId, roleId)).thenReturn(role);
    }
}
