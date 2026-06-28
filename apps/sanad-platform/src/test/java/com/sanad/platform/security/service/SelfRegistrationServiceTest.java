package com.sanad.platform.security.service;

import com.sanad.platform.access.capability.AccessCapability;
import com.sanad.platform.access.capability.AccessCapabilityRepository;
import com.sanad.platform.access.capability.CapabilityStatus;
import com.sanad.platform.access.grant.UserRoleGrantRepository;
import com.sanad.platform.access.role.Role;
import com.sanad.platform.access.role.RoleCapability;
import com.sanad.platform.access.role.RoleCapabilityRepository;
import com.sanad.platform.access.role.RoleRepository;
import com.sanad.platform.organization.domain.Organization;
import com.sanad.platform.organization.membership.repository.OrganizationMembershipRepository;
import com.sanad.platform.organization.repository.OrganizationRepository;
import com.sanad.platform.security.dto.SelfRegistrationRequest;
import com.sanad.platform.security.dto.SelfRegistrationResponse;
import com.sanad.platform.security.exception.RegistrationConflictException;
import com.sanad.platform.security.notification.PasswordRecoveryNotificationCoordinator;
import com.sanad.platform.tenant.domain.Tenant;
import com.sanad.platform.tenant.repository.TenantRepository;
import com.sanad.platform.user.domain.User;
import com.sanad.platform.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SelfRegistrationServiceTest {

    @Mock TenantRepository tenantRepository;
    @Mock OrganizationRepository organizationRepository;
    @Mock UserRepository userRepository;
    @Mock OrganizationMembershipRepository membershipRepository;
    @Mock RoleRepository roleRepository;
    @Mock UserRoleGrantRepository roleGrantRepository;
    @Mock AccessCapabilityRepository capabilityRepository;
    @Mock RoleCapabilityRepository roleCapabilityRepository;
    @Mock PasswordRecoveryNotificationCoordinator recoveryCoordinator;
    @Mock Organization savedOrganization;
    @Mock User savedUser;
    @Mock Role savedRole;
    @Mock AccessCapability activeCapability;

    @Test
    void createsWorkspaceAdministratorCapabilitiesAndPasswordSetupLink() {
        UUID tenantId = UUID.randomUUID();
        UUID organizationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        UUID capabilityId = UUID.randomUUID();

        when(tenantRepository.existsBySubdomain("acme")).thenReturn(false);
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(invocation -> {
            Tenant tenant = invocation.getArgument(0);
            tenant.setId(tenantId);
            return tenant;
        });
        when(savedOrganization.getId()).thenReturn(organizationId);
        when(organizationRepository.save(any(Organization.class))).thenReturn(savedOrganization);
        when(savedUser.getId()).thenReturn(userId);
        when(savedUser.getTenantId()).thenReturn(tenantId);
        when(userRepository.save(any(User.class))).thenReturn(savedUser);
        when(savedRole.getId()).thenReturn(roleId);
        when(roleRepository.save(any(Role.class))).thenReturn(savedRole);
        when(activeCapability.getId()).thenReturn(capabilityId);
        when(capabilityRepository.findByStatusOrderByCodeAsc(CapabilityStatus.ACTIVE))
                .thenReturn(List.of(activeCapability));

        SelfRegistrationResponse response = service().register(
                request("acme"), "127.0.0.1", "ar");

        assertEquals("acme", response.getSubdomain());
        assertTrue(response.isPasswordSetupRequired());
        verify(membershipRepository).save(any());
        verify(roleGrantRepository).save(any());
        verify(roleCapabilityRepository).save(any(RoleCapability.class));
        verify(recoveryCoordinator).createAdministrativeResetLink(
                eq(tenantId), eq(userId), eq("ar"), eq("127.0.0.1"));
    }

    @Test
    void rejectsReservedWorkspaceIdentifierBeforePersistence() {
        assertThrows(RegistrationConflictException.class,
                () -> service().register(request("admin"), "127.0.0.1", "ar"));
        verify(tenantRepository, never()).save(any());
    }

    private SelfRegistrationService service() {
        return new SelfRegistrationService(
                tenantRepository,
                organizationRepository,
                userRepository,
                membershipRepository,
                roleRepository,
                roleGrantRepository,
                capabilityRepository,
                roleCapabilityRepository,
                recoveryCoordinator);
    }

    private SelfRegistrationRequest request(String subdomain) {
        SelfRegistrationRequest request = new SelfRegistrationRequest();
        request.setDisplayName("Abdulrahman Sinan");
        request.setEmail("admin@example.com");
        request.setOrganizationName("Acme Company");
        request.setSubdomain(subdomain);
        request.setAcceptTerms(true);
        return request;
    }
}
