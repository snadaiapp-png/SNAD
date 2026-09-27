package com.sanad.platform.platformiam;

import com.sanad.platform.access.AccessConflictException;
import com.sanad.platform.admin.service.PlatformAuditService;
import com.sanad.platform.platformiam.domain.PlatformMembership;
import com.sanad.platform.platformiam.domain.PlatformMembershipStatus;
import com.sanad.platform.platformiam.dto.CreatePlatformUserRequest;
import com.sanad.platform.platformiam.dto.PlatformUserResponse;
import com.sanad.platform.platformiam.repository.PlatformMembershipRepository;
import com.sanad.platform.platformiam.service.PlatformOwnerSafetyService;
import com.sanad.platform.platformiam.service.PlatformUserService;
import com.sanad.platform.security.authorization.ControlPlaneAccessGuard;
import com.sanad.platform.security.service.AuthService;
import com.sanad.platform.user.domain.User;
import com.sanad.platform.user.domain.UserStatus;
import com.sanad.platform.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlatformUserServiceTest {

    private static final UUID CONTROL_TENANT = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID ACTOR_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID USER_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID MEMBERSHIP_ID = UUID.fromString("40000000-0000-0000-0000-000000000001");
    private static final Instant NOW = Instant.parse("2026-09-28T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void existingControlTenantIdentityWithoutMembershipIsReused() {
        Fixture f = fixture();
        User existing = user(USER_ID, CONTROL_TENANT, "operator@example.com", UserStatus.ACTIVE);
        when(f.users.findByTenantIdAndEmail(CONTROL_TENANT, "operator@example.com"))
                .thenReturn(Optional.of(existing));
        when(f.memberships.findByControlTenantIdAndUserId(CONTROL_TENANT, USER_ID))
                .thenReturn(Optional.empty());
        when(f.memberships.save(any(PlatformMembership.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        Authentication actor = actor();

        PlatformUserResponse response = f.service.createPlatformUser(
                actor, new CreatePlatformUserRequest(" Operator@Example.com ", "Operator"));

        assertThat(response.userId()).isEqualTo(USER_ID);
        assertThat(response.membershipStatus()).isEqualTo(PlatformMembershipStatus.INVITED);
        verify(f.users, never()).save(any(User.class));
        verify(f.memberships).save(any(PlatformMembership.class));
        verify(f.audit).success(actor, CONTROL_TENANT, "PLATFORM_USER_CREATED",
                "PLATFORM_USER", USER_ID.toString(), null, null, response);
    }

    @Test
    void identityExistingOnlyOutsideControlTenantIsNeverReusedOrPromoted() {
        Fixture f = fixture();
        User saved = user(USER_ID, CONTROL_TENANT, "operator@example.com", UserStatus.ACTIVE);
        when(f.users.findByTenantIdAndEmail(CONTROL_TENANT, "operator@example.com"))
                .thenReturn(Optional.empty());
        when(f.users.save(any(User.class))).thenReturn(saved);
        when(f.memberships.findByControlTenantIdAndUserId(CONTROL_TENANT, USER_ID))
                .thenReturn(Optional.empty());
        when(f.memberships.save(any(PlatformMembership.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PlatformUserResponse response = f.service.createPlatformUser(
                actor(), new CreatePlatformUserRequest("operator@example.com", "Operator"));

        assertThat(response.userId()).isEqualTo(USER_ID);
        verify(f.users, never()).findAllByEmail("operator@example.com");
        verify(f.users).save(any(User.class));
    }

    @Test
    void existingPlatformMembershipIsAConflict() {
        Fixture f = fixture();
        User existing = user(USER_ID, CONTROL_TENANT, "operator@example.com", UserStatus.ACTIVE);
        when(f.users.findByTenantIdAndEmail(CONTROL_TENANT, "operator@example.com"))
                .thenReturn(Optional.of(existing));
        when(f.memberships.findByControlTenantIdAndUserId(CONTROL_TENANT, USER_ID))
                .thenReturn(Optional.of(membership(PlatformMembershipStatus.ACTIVE)));

        assertThatThrownBy(() -> f.service.createPlatformUser(
                actor(), new CreatePlatformUserRequest("operator@example.com", "Operator")))
                .isInstanceOf(AccessConflictException.class);

        verify(f.memberships, never()).save(any());
    }

    @ParameterizedTest
    @EnumSource(value = PlatformMembershipStatus.class, names = {"SUSPENDED", "LOCKED", "DISABLED"})
    void sensitiveLifecycleTransitionsProtectOwnerAndInvalidateSessions(PlatformMembershipStatus target) {
        Fixture f = fixture();
        User existing = user(USER_ID, CONTROL_TENANT, "operator@example.com", UserStatus.ACTIVE);
        when(f.users.findByTenantIdAndId(CONTROL_TENANT, USER_ID)).thenReturn(Optional.of(existing));
        when(f.memberships.findByControlTenantIdAndUserId(CONTROL_TENANT, USER_ID))
                .thenReturn(Optional.of(membership(PlatformMembershipStatus.ACTIVE)));
        when(f.memberships.save(any(PlatformMembership.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PlatformUserResponse response = switch (target) {
            case SUSPENDED -> f.service.suspend(actor(), USER_ID, "incident");
            case LOCKED -> f.service.lock(actor(), USER_ID, "incident");
            case DISABLED -> f.service.disable(actor(), USER_ID, "incident");
            default -> throw new IllegalStateException("unexpected target");
        };

        assertThat(response.membershipStatus()).isEqualTo(target);
        verify(f.ownerSafety).assertMayDeactivateMembership(CONTROL_TENANT, USER_ID);
        verify(f.authService).logout(CONTROL_TENANT, USER_ID);
        verify(f.memberships).save(any(PlatformMembership.class));
    }

    @Test
    void disabledMembershipCannotUseNormalActivateFlow() {
        Fixture f = fixture();
        User existing = user(USER_ID, CONTROL_TENANT, "operator@example.com", UserStatus.ACTIVE);
        when(f.users.findByTenantIdAndId(CONTROL_TENANT, USER_ID)).thenReturn(Optional.of(existing));
        when(f.memberships.findByControlTenantIdAndUserId(CONTROL_TENANT, USER_ID))
                .thenReturn(Optional.of(membership(PlatformMembershipStatus.DISABLED)));

        assertThatThrownBy(() -> f.service.activate(actor(), USER_ID, "recovery"))
                .isInstanceOf(AccessConflictException.class);
    }

    @Test
    void controlPlaneBoundaryIsRequiredBeforeAnyMutation() {
        Fixture f = fixture();
        org.mockito.Mockito.doThrow(new AccessDeniedException("control plane required"))
                .when(f.guard).require(any(Authentication.class));

        assertThatThrownBy(() -> f.service.createPlatformUser(
                actor(), new CreatePlatformUserRequest("operator@example.com", "Operator")))
                .isInstanceOf(AccessDeniedException.class);

        verify(f.users, never()).findByTenantIdAndEmail(any(), any());
    }

    private static Fixture fixture() {
        ControlPlaneAccessGuard guard = mock(ControlPlaneAccessGuard.class);
        UserRepository users = mock(UserRepository.class);
        PlatformMembershipRepository memberships = mock(PlatformMembershipRepository.class);
        PlatformOwnerSafetyService ownerSafety = mock(PlatformOwnerSafetyService.class);
        AuthService authService = mock(AuthService.class);
        PlatformAuditService audit = mock(PlatformAuditService.class);
        return new Fixture(guard, users, memberships, ownerSafety, authService, audit,
                new PlatformUserService(guard, users, memberships, ownerSafety, authService, audit, CLOCK));
    }

    private static Authentication actor() {
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken("owner", "n/a", List.of());
        auth.setDetails(Map.of("tenant_id", CONTROL_TENANT.toString(), "user_id", ACTOR_ID.toString()));
        return auth;
    }

    private static User user(UUID id, UUID tenantId, String email, UserStatus status) {
        User user = mock(User.class);
        when(user.getId()).thenReturn(id);
        when(user.getTenantId()).thenReturn(tenantId);
        when(user.getEmail()).thenReturn(email);
        when(user.getDisplayName()).thenReturn("Operator");
        when(user.getStatus()).thenReturn(status);
        when(user.getLastLoginAt()).thenReturn(null);
        return user;
    }

    private static PlatformMembership membership(PlatformMembershipStatus status) {
        return new PlatformMembership(
                MEMBERSHIP_ID, CONTROL_TENANT, USER_ID, status,
                NOW.minusSeconds(3600),
                status == PlatformMembershipStatus.ACTIVE ? NOW.minusSeconds(1800) : null,
                status == PlatformMembershipStatus.SUSPENDED ? NOW.minusSeconds(600) : null,
                status == PlatformMembershipStatus.LOCKED ? NOW.minusSeconds(600) : null,
                status == PlatformMembershipStatus.DISABLED ? NOW.minusSeconds(600) : null,
                ACTOR_ID, ACTOR_ID, null, NOW.minusSeconds(3600), NOW.minusSeconds(60));
    }

    private record Fixture(
            ControlPlaneAccessGuard guard,
            UserRepository users,
            PlatformMembershipRepository memberships,
            PlatformOwnerSafetyService ownerSafety,
            AuthService authService,
            PlatformAuditService audit,
            PlatformUserService service) {
    }
}
