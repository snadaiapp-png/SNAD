package com.sanad.platform.platformiam;

import com.sanad.platform.access.AccessDecisionResponse;
import com.sanad.platform.access.evaluation.CapabilityEvaluationService;
import com.sanad.platform.platformiam.domain.PlatformMembership;
import com.sanad.platform.platformiam.domain.PlatformMembershipStatus;
import com.sanad.platform.platformiam.repository.PlatformMembershipRepository;
import com.sanad.platform.platformiam.service.PlatformAuthorizationService;
import com.sanad.platform.platformiam.service.PlatformMembershipService;
import com.sanad.platform.security.authorization.ControlPlaneAccessGuard;
import com.sanad.platform.security.authorization.PlatformMembershipGuard;
import com.sanad.platform.security.rls.TenantRlsTransactionContext;
import com.sanad.platform.security.scope.AccessScopeGrant;
import com.sanad.platform.security.scope.AccessScopeType;
import com.sanad.platform.security.scope.JdbcAccessScopeRepository;
import com.sanad.platform.user.domain.User;
import com.sanad.platform.user.domain.UserStatus;
import com.sanad.platform.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlatformAuthorizationServiceTest {

    private static final UUID CONTROL_TENANT =
            UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID OTHER_TENANT =
            UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID USER_ID =
            UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID ACTOR_ID =
            UUID.fromString("40000000-0000-0000-0000-000000000001");
    private static final String CAPABILITY = "PLATFORM.USER.READ";
    private static final Instant NOW = Instant.parse("2026-09-26T21:00:00Z");

    @Test
    void membershipGuardFailsClosedForUnauthenticatedAndWrongControlTenant() {
        Fixture fixture = fixture();

        UsernamePasswordAuthenticationToken unauthenticated =
                UsernamePasswordAuthenticationToken.unauthenticated("operator", "n/a");

        assertThatThrownBy(() -> fixture.guard.requireActive(unauthenticated))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> fixture.guard.requireActive(auth(OTHER_TENANT, USER_ID)))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void membershipGuardDeniesWhenMembershipIsMissing() {
        Fixture fixture = fixture();
        when(fixture.memberships.findByControlTenantIdAndUserId(CONTROL_TENANT, USER_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> fixture.guard.requireActive(auth(CONTROL_TENANT, USER_ID)))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("membership");
    }

    @ParameterizedTest
    @EnumSource(value = PlatformMembershipStatus.class,
            names = {"INVITED", "SUSPENDED", "LOCKED", "DISABLED"})
    void membershipGuardDeniesEveryNonActiveMembership(PlatformMembershipStatus status) {
        Fixture fixture = fixture();
        when(fixture.memberships.findByControlTenantIdAndUserId(CONTROL_TENANT, USER_ID))
                .thenReturn(Optional.of(membership(status)));

        assertThatThrownBy(() -> fixture.guard.requireActive(auth(CONTROL_TENANT, USER_ID)))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("ACTIVE");
    }

    @Test
    void membershipGuardAllowsOnlyActiveMembership() {
        Fixture fixture = fixture();
        when(fixture.memberships.findByControlTenantIdAndUserId(CONTROL_TENANT, USER_ID))
                .thenReturn(Optional.of(membership(PlatformMembershipStatus.ACTIVE)));

        assertThatCode(() -> fixture.guard.requireActive(auth(CONTROL_TENANT, USER_ID)))
                .doesNotThrowAnyException();
    }

    @Test
    void legacyPlatformAdminFlagWithoutActiveMembershipIsDenied() {
        Fixture fixture = fixture();
        User legacyPlatformAdmin = activeUser();
        legacyPlatformAdmin.setPlatformAdmin(true);
        when(fixture.users.findByTenantIdAndId(CONTROL_TENANT, USER_ID))
                .thenReturn(Optional.of(legacyPlatformAdmin));
        when(fixture.memberships.findByControlTenantIdAndUserId(CONTROL_TENANT, USER_ID))
                .thenReturn(Optional.empty());

        AccessDecisionResponse decision = fixture.authorization.evaluate(
                auth(CONTROL_TENANT, USER_ID), CAPABILITY);

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.reason()).isEqualTo("PLATFORM_MEMBERSHIP_REQUIRED");
        verify(fixture.capabilityEvaluation, never()).evaluate(any(), any(), any(), any());
    }

    @Test
    void inactiveAccountIsDeniedEvenWithActivePlatformMembership() {
        Fixture fixture = fixture();
        when(fixture.memberships.findByControlTenantIdAndUserId(CONTROL_TENANT, USER_ID))
                .thenReturn(Optional.of(membership(PlatformMembershipStatus.ACTIVE)));
        User inactive = new User(CONTROL_TENANT, "inactive@example.test", "Inactive", UserStatus.SUSPENDED);
        when(fixture.users.findByTenantIdAndId(CONTROL_TENANT, USER_ID))
                .thenReturn(Optional.of(inactive));

        AccessDecisionResponse decision = fixture.authorization.evaluate(
                auth(CONTROL_TENANT, USER_ID), CAPABILITY);

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.reason()).isEqualTo("PLATFORM_USER_NOT_ACTIVE");
        verify(fixture.capabilityEvaluation, never()).evaluate(any(), any(), any(), any());
    }

    @Test
    void validDirectTemporaryGrantAllowsExactCapabilityBeforeExpiry() {
        Fixture fixture = activeFixture();
        AccessScopeGrant grant = directGrant(NOW.plusSeconds(60));
        when(fixture.scopeRepository.findEffectiveGrants(
                CONTROL_TENANT, USER_ID, null, CAPABILITY, NOW))
                .thenReturn(List.of(grant));

        AccessDecisionResponse decision = fixture.authorization.evaluate(
                auth(CONTROL_TENANT, USER_ID), CAPABILITY);

        assertThat(decision.allowed()).isTrue();
        assertThat(decision.reason()).isEqualTo("TEMPORARY_DIRECT_GRANT");
        assertThat(decision.capabilityCode()).isEqualTo(CAPABILITY);
        verify(fixture.capabilityEvaluation, never()).evaluate(any(), any(), any(), any());
    }

    @Test
    void directTemporaryGrantDeniesAtExactExpiryBoundaryAndFallsBackToRoleEngine() {
        Fixture fixture = activeFixture();
        when(fixture.scopeRepository.findEffectiveGrants(
                CONTROL_TENANT, USER_ID, null, CAPABILITY, NOW))
                .thenReturn(List.of(directGrant(NOW)));
        AccessDecisionResponse roleDenied = new AccessDecisionResponse(
                CONTROL_TENANT, USER_ID, null, CAPABILITY,
                false, "NO_MATCHING_ACTIVE_ROLE", null, null);
        when(fixture.capabilityEvaluation.evaluate(CONTROL_TENANT, USER_ID, CAPABILITY, null))
                .thenReturn(roleDenied);

        AccessDecisionResponse decision = fixture.authorization.evaluate(
                auth(CONTROL_TENANT, USER_ID), CAPABILITY);

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.reason()).isEqualTo("NO_MATCHING_ACTIVE_ROLE");
        verify(fixture.capabilityEvaluation).evaluate(CONTROL_TENANT, USER_ID, CAPABILITY, null);
    }

    @Test
    void invalidDirectExceptionMetadataCannotAuthorize() {
        Fixture fixture = activeFixture();
        AccessScopeGrant invalid = new AccessScopeGrant(
                UUID.randomUUID(), CONTROL_TENANT, null, USER_ID,
                AccessScopeType.TENANT, null, null, null,
                true, " ", null, NOW.minusSeconds(60), NOW.plusSeconds(60));
        when(fixture.scopeRepository.findEffectiveGrants(
                CONTROL_TENANT, USER_ID, null, CAPABILITY, NOW))
                .thenReturn(List.of(invalid));
        AccessDecisionResponse roleDenied = new AccessDecisionResponse(
                CONTROL_TENANT, USER_ID, null, CAPABILITY,
                false, "NO_MATCHING_ACTIVE_ROLE", null, null);
        when(fixture.capabilityEvaluation.evaluate(CONTROL_TENANT, USER_ID, CAPABILITY, null))
                .thenReturn(roleDenied);

        assertThat(fixture.authorization.evaluate(auth(CONTROL_TENANT, USER_ID), CAPABILITY).allowed())
                .isFalse();
    }

    @Test
    void activeMembershipWithoutDirectGrantDelegatesToExistingCapabilityEngine() {
        Fixture fixture = activeFixture();
        when(fixture.scopeRepository.findEffectiveGrants(
                CONTROL_TENANT, USER_ID, null, CAPABILITY, NOW))
                .thenReturn(List.of());
        AccessDecisionResponse roleAllowed = new AccessDecisionResponse(
                CONTROL_TENANT, USER_ID, null, CAPABILITY,
                true, "ROLE_CAPABILITY_MATCH", UUID.randomUUID(), "SECURITY_ADMIN");
        when(fixture.capabilityEvaluation.evaluate(CONTROL_TENANT, USER_ID, CAPABILITY, null))
                .thenReturn(roleAllowed);

        AccessDecisionResponse decision = fixture.authorization.evaluate(
                auth(CONTROL_TENANT, USER_ID), CAPABILITY);

        assertThat(decision).isEqualTo(roleAllowed);
        verify(fixture.capabilityEvaluation).evaluate(CONTROL_TENANT, USER_ID, CAPABILITY, null);
    }

    private static Fixture activeFixture() {
        Fixture fixture = fixture();
        when(fixture.memberships.findByControlTenantIdAndUserId(CONTROL_TENANT, USER_ID))
                .thenReturn(Optional.of(membership(PlatformMembershipStatus.ACTIVE)));
        when(fixture.users.findByTenantIdAndId(CONTROL_TENANT, USER_ID))
                .thenReturn(Optional.of(activeUser()));
        return fixture;
    }

    private static Fixture fixture() {
        PlatformMembershipRepository memberships = mock(PlatformMembershipRepository.class);
        TenantRlsTransactionContext rlsContext = mock(TenantRlsTransactionContext.class);
        UserRepository users = mock(UserRepository.class);
        JdbcAccessScopeRepository scopeRepository = mock(JdbcAccessScopeRepository.class);
        CapabilityEvaluationService capabilityEvaluation = mock(CapabilityEvaluationService.class);
        ControlPlaneAccessGuard controlPlane = new ControlPlaneAccessGuard(CONTROL_TENANT.toString());
        PlatformMembershipService membershipService = new PlatformMembershipService(memberships, rlsContext);
        PlatformMembershipGuard guard = new PlatformMembershipGuard(controlPlane, membershipService);
        PlatformAuthorizationService authorization = new PlatformAuthorizationService(
                controlPlane,
                membershipService,
                users,
                scopeRepository,
                capabilityEvaluation,
                Clock.fixed(NOW, ZoneOffset.UTC));
        return new Fixture(memberships, users, scopeRepository, capabilityEvaluation, guard, authorization);
    }

    private static PlatformMembership membership(PlatformMembershipStatus status) {
        return new PlatformMembership(
                UUID.randomUUID(), CONTROL_TENANT, USER_ID, status,
                NOW.minusSeconds(600),
                status == PlatformMembershipStatus.ACTIVE ? NOW.minusSeconds(300) : null,
                null, null, null,
                ACTOR_ID, ACTOR_ID, null,
                NOW.minusSeconds(600), NOW.minusSeconds(60));
    }

    private static User activeUser() {
        return new User(CONTROL_TENANT, "operator@example.test", "Operator", UserStatus.ACTIVE);
    }

    private static AccessScopeGrant directGrant(Instant effectiveTo) {
        return new AccessScopeGrant(
                UUID.randomUUID(), CONTROL_TENANT, null, USER_ID,
                AccessScopeType.TENANT, null, null, null,
                true, "incident support", ACTOR_ID,
                NOW.minusSeconds(60), effectiveTo);
    }

    private static UsernamePasswordAuthenticationToken auth(UUID tenantId, UUID userId) {
        UsernamePasswordAuthenticationToken authentication =
                UsernamePasswordAuthenticationToken.authenticated(userId.toString(), "n/a", List.of());
        authentication.setDetails(Map.of(
                "tenant_id", tenantId.toString(),
                "user_id", userId.toString()));
        return authentication;
    }

    private record Fixture(
            PlatformMembershipRepository memberships,
            UserRepository users,
            JdbcAccessScopeRepository scopeRepository,
            CapabilityEvaluationService capabilityEvaluation,
            PlatformMembershipGuard guard,
            PlatformAuthorizationService authorization) {
    }
}
