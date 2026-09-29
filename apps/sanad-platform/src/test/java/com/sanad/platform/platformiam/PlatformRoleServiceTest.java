package com.sanad.platform.platformiam;

import com.sanad.platform.access.AccessConflictException;
import com.sanad.platform.access.UserAccessResponse;
import com.sanad.platform.access.capability.AccessCapabilityService;
import com.sanad.platform.access.grant.UserGrantStatus;
import com.sanad.platform.access.grant.UserRoleGrantService;
import com.sanad.platform.access.role.RoleCapabilityService;
import com.sanad.platform.access.role.RoleResponse;
import com.sanad.platform.access.role.RoleService;
import com.sanad.platform.access.role.RoleStatus;
import com.sanad.platform.admin.service.PlatformAuditService;
import com.sanad.platform.platformiam.domain.PlatformRoleMetadata;
import com.sanad.platform.platformiam.dto.CreatePlatformRoleRequest;
import com.sanad.platform.platformiam.dto.ReplacePlatformRolesRequest;
import com.sanad.platform.platformiam.dto.ReplaceRoleCapabilitiesRequest;
import com.sanad.platform.platformiam.repository.PlatformRoleMetadataRepository;
import com.sanad.platform.platformiam.service.PlatformOwnerSafetyService;
import com.sanad.platform.platformiam.service.PlatformRoleService;
import com.sanad.platform.security.authorization.ControlPlaneAccessGuard;
import org.junit.jupiter.api.Test;
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

class PlatformRoleServiceTest {

    private static final UUID CONTROL_TENANT = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID ACTOR_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID USER_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID ROLE_ID = UUID.fromString("40000000-0000-0000-0000-000000000001");
    private static final UUID OTHER_ROLE_ID = UUID.fromString("40000000-0000-0000-0000-000000000002");
    private static final UUID GRANT_ID = UUID.fromString("50000000-0000-0000-0000-000000000001");
    private static final UUID CAPABILITY_ID = UUID.fromString("60000000-0000-0000-0000-000000000001");
    private static final Instant NOW = Instant.parse("2026-09-28T00:00:00Z");

    @Test
    void createPlatformRoleUsesCanonicalRoleServiceAndAddsCustomMetadata() {
        Fixture f = fixture();
        RoleResponse canonical = role(ROLE_ID, "SUPPORT_CUSTOM");
        when(f.roles.create(any(), any())).thenReturn(canonical);
        when(f.metadata.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var response = f.service.create(actor(),
                new CreatePlatformRoleRequest("support_custom", "Support Custom", "Scoped support"));

        assertThat(response.id()).isEqualTo(ROLE_ID);
        assertThat(response.roleType()).isEqualTo(PlatformRoleMetadata.RoleType.CUSTOM);
        assertThat(response.protectedRole()).isFalse();
        verify(f.roles).create(org.mockito.ArgumentMatchers.eq(CONTROL_TENANT), any());
        verify(f.metadata).save(any(PlatformRoleMetadata.class));
    }

    @Test
    void protectedSystemRoleCapabilitiesCannotBeReplaced() {
        Fixture f = fixture();
        when(f.metadata.findByControlTenantIdAndRoleId(CONTROL_TENANT, ROLE_ID))
                .thenReturn(Optional.of(new PlatformRoleMetadata(
                        CONTROL_TENANT, ROLE_ID, PlatformRoleMetadata.RoleType.SYSTEM,
                        true, true, NOW, NOW)));

        assertThatThrownBy(() -> f.service.replaceRoleCapabilities(
                actor(), ROLE_ID, new ReplaceRoleCapabilitiesRequest(List.of(CAPABILITY_ID))))
                .isInstanceOf(AccessConflictException.class);

        verify(f.roleCapabilities, never()).attach(any(), any(), any());
        verify(f.roleCapabilities, never()).detach(any(), any(), any());
    }

    @Test
    void removingOwnerRoleUsesOwnerSafetyBeforeCanonicalRevoke() {
        Fixture f = fixture();
        UserAccessResponse current = new UserAccessResponse(
                GRANT_ID, CONTROL_TENANT, USER_ID, ROLE_ID, "PLATFORM_OWNER",
                null, UserGrantStatus.ACTIVE, NOW.minusSeconds(100), NOW.minusSeconds(100));
        when(f.grants.list(CONTROL_TENANT, USER_ID)).thenReturn(List.of(current));
        when(f.metadata.findByControlTenantIdAndRoleId(CONTROL_TENANT, ROLE_ID))
                .thenReturn(Optional.of(new PlatformRoleMetadata(
                        CONTROL_TENANT, ROLE_ID, PlatformRoleMetadata.RoleType.SYSTEM,
                        true, true, NOW, NOW)));
        when(f.metadata.findByControlTenantIdAndRoleId(CONTROL_TENANT, OTHER_ROLE_ID))
                .thenReturn(Optional.of(new PlatformRoleMetadata(
                        CONTROL_TENANT, OTHER_ROLE_ID, PlatformRoleMetadata.RoleType.SYSTEM,
                        false, false, NOW, NOW)));

        f.service.replaceUserRoles(actor(), USER_ID,
                new ReplacePlatformRolesRequest(List.of(OTHER_ROLE_ID), "rotation"));

        verify(f.ownerSafety).assertMayRemoveOwnerRole(CONTROL_TENANT, USER_ID, ROLE_ID);
        verify(f.grants).revoke(CONTROL_TENANT, GRANT_ID);
        verify(f.grants).grant(CONTROL_TENANT, USER_ID, OTHER_ROLE_ID, null);
    }

    @Test
    void actorTenantIsAlwaysTheCanonicalRoleScope() {
        Fixture f = fixture();
        when(f.roles.list(CONTROL_TENANT)).thenReturn(List.of(role(ROLE_ID, "PLATFORM_ADMIN")));
        when(f.metadata.findByControlTenantId(CONTROL_TENANT)).thenReturn(List.of());

        f.service.list(actor());

        verify(f.guard).require(any(Authentication.class));
        verify(f.roles).list(CONTROL_TENANT);
    }

    private static Fixture fixture() {
        ControlPlaneAccessGuard guard = mock(ControlPlaneAccessGuard.class);
        when(guard.isControlPlaneTenant(CONTROL_TENANT)).thenReturn(true);
        RoleService roles = mock(RoleService.class);
        RoleCapabilityService roleCapabilities = mock(RoleCapabilityService.class);
        AccessCapabilityService capabilities = mock(AccessCapabilityService.class);
        PlatformRoleMetadataRepository metadata = mock(PlatformRoleMetadataRepository.class);
        UserRoleGrantService grants = mock(UserRoleGrantService.class);
        PlatformOwnerSafetyService ownerSafety = mock(PlatformOwnerSafetyService.class);
        PlatformAuditService audit = mock(PlatformAuditService.class);
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        return new Fixture(guard, roles, roleCapabilities, capabilities, metadata, grants, ownerSafety, audit,
                new PlatformRoleService(guard, roles, roleCapabilities, capabilities, metadata,
                        grants, ownerSafety, audit, clock));
    }

    private static Authentication actor() {
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken("owner", "n/a", List.of());
        auth.setDetails(Map.of("tenant_id", CONTROL_TENANT.toString(), "user_id", ACTOR_ID.toString()));
        return auth;
    }

    private static RoleResponse role(UUID roleId, String code) {
        return new RoleResponse(roleId, CONTROL_TENANT, code, code, null,
                RoleStatus.ACTIVE, NOW.minusSeconds(100), NOW.minusSeconds(100));
    }

    private record Fixture(
            ControlPlaneAccessGuard guard,
            RoleService roles,
            RoleCapabilityService roleCapabilities,
            AccessCapabilityService capabilities,
            PlatformRoleMetadataRepository metadata,
            UserRoleGrantService grants,
            PlatformOwnerSafetyService ownerSafety,
            PlatformAuditService audit,
            PlatformRoleService service) {
    }
}
