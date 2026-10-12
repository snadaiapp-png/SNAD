package com.sanad.platform.security.api;

import com.sanad.platform.access.grant.UserGrantStatus;
import com.sanad.platform.access.grant.UserRoleGrantRepository;
import com.sanad.platform.access.role.RoleCapabilityRepository;
import com.sanad.platform.access.role.RoleRepository;
import com.sanad.platform.organization.membership.repository.OrganizationMembershipRepository;
import com.sanad.platform.security.authorization.ControlPlaneAccessGuard;
import com.sanad.platform.security.notification.PasswordRecoveryNotificationCoordinator;
import com.sanad.platform.security.ratelimit.LoginRateLimitKeys;
import com.sanad.platform.security.service.AuthService;
import com.sanad.platform.security.service.LoginDestinationResolver;
import com.sanad.platform.user.access.UserApplicationAccessReadRepository;
import com.sanad.platform.user.domain.User;
import com.sanad.platform.user.domain.UserStatus;
import com.sanad.platform.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class AuthMeEffectiveCapabilityContractTest {
    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID OWNER = UUID.randomUUID();

    @Test
    void meIncludesTenantScopedDirectAllowEvenWithoutRoleCapabilities() {
        Fixture fixture = new Fixture();
        when(fixture.access.effectiveCapabilityCodes(TENANT, OWNER))
                .thenReturn(Set.of("HRM.PAYROLL.VIEW"));

        var me = (com.sanad.platform.security.dto.MeResponse) ReflectionTestUtils.invokeMethod(
                fixture.controller, "buildMeResponse", TENANT, OWNER);

        assertEquals(OWNER, me.getId());
        assertEquals(TENANT, me.getTenantId());
        assertEquals(List.of("HRM.PAYROLL.VIEW"), me.getCapabilities());
        verify(fixture.access).effectiveCapabilityCodes(TENANT, OWNER);
        verifyNoInteractions(fixture.roleCaps);
    }

    @Test
    void meDoesNotReintroduceDeniedCapabilityFromRoleOnlyProjection() {
        Fixture fixture = new Fixture();
        // The effective repository already applies explicit DENY over role ALLOW.
        when(fixture.access.effectiveCapabilityCodes(TENANT, OWNER))
                .thenReturn(Set.of("CRM.LEADS.VIEW"));

        var me = (com.sanad.platform.security.dto.MeResponse) ReflectionTestUtils.invokeMethod(
                fixture.controller, "buildMeResponse", TENANT, OWNER);

        assertEquals(List.of("CRM.LEADS.VIEW"), me.getCapabilities());
        verify(fixture.access).effectiveCapabilityCodes(TENANT, OWNER);
        verifyNoInteractions(fixture.roleCaps);
    }

    private static final class Fixture {
        final UserRepository users = mock(UserRepository.class);
        final OrganizationMembershipRepository memberships = mock(OrganizationMembershipRepository.class);
        final UserRoleGrantRepository grants = mock(UserRoleGrantRepository.class);
        final RoleCapabilityRepository roleCaps = mock(RoleCapabilityRepository.class);
        final UserApplicationAccessReadRepository access = mock(UserApplicationAccessReadRepository.class);
        final AuthController controller;

        Fixture() {
            User owner = mock(User.class);
            when(owner.getId()).thenReturn(OWNER);
            when(owner.getTenantId()).thenReturn(TENANT);
            when(owner.getEmail()).thenReturn("owner@example.invalid");
            when(owner.getStatus()).thenReturn(UserStatus.ACTIVE);
            when(users.findByTenantIdAndId(TENANT, OWNER)).thenReturn(Optional.of(owner));
            when(memberships.findByTenantIdAndUserId(TENANT, OWNER)).thenReturn(List.of());
            when(grants.findByTenantIdAndUserIdAndStatus(TENANT, OWNER, UserGrantStatus.ACTIVE))
                    .thenReturn(List.of());
            controller = new AuthController(
                    mock(AuthService.class), users, memberships, grants,
                    mock(RoleRepository.class), roleCaps, access,
                    mock(ControlPlaneAccessGuard.class), mock(Environment.class),
                    mock(PasswordRecoveryNotificationCoordinator.class),
                    mock(LoginDestinationResolver.class), mock(LoginRateLimitKeys.class));
        }
    }
}
