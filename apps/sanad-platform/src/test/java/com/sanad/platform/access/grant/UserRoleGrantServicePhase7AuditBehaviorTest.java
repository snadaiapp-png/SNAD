package com.sanad.platform.access.grant;

import com.sanad.platform.access.AccessResourceNotFoundException;
import com.sanad.platform.access.UserAccessResponse;
import com.sanad.platform.access.audit.AccessMutationAuditSupport;
import com.sanad.platform.access.role.Role;
import com.sanad.platform.access.role.RoleService;
import com.sanad.platform.access.role.RoleStatus;
import com.sanad.platform.organization.domain.Organization;
import com.sanad.platform.organization.repository.OrganizationRepository;
import com.sanad.platform.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Phase 7 behavioral audit contract for user role grants: canonical event
 * names (USER_ROLE_GRANTED / USER_ROLE_REVOKED), the distinct USER_SCOPE_CHANGED
 * fact for organization-scoped grants only, and zero false-change events for
 * idempotent no-ops.
 */
class UserRoleGrantServicePhase7AuditBehaviorTest {

    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID USER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID ROLE_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID ORG_ID = UUID.fromString("66666666-6666-6666-6666-666666666666");

    private UserRoleGrantRepository grantRepository;
    private AccessMutationAuditSupport audit;
    private UserRoleGrantService service;

    @BeforeEach
    void setUp() {
        grantRepository = mock(UserRoleGrantRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        OrganizationRepository organizationRepository = mock(OrganizationRepository.class);
        RoleService roleService = mock(RoleService.class);
        service = new UserRoleGrantService(grantRepository, userRepository, organizationRepository, roleService);
        service.setAudit(audit = mock(AccessMutationAuditSupport.class));

        when(userRepository.findByTenantIdAndId(TENANT_ID, USER_ID))
                .thenReturn(Optional.of(mock(com.sanad.platform.user.domain.User.class)));
        Role role = mock(Role.class);
        when(role.getId()).thenReturn(ROLE_ID);
        when(role.getCode()).thenReturn("TENANT_ADMIN");
        when(role.getStatus()).thenReturn(RoleStatus.ACTIVE);
        when(roleService.load(TENANT_ID, ROLE_ID)).thenReturn(role);
        when(organizationRepository.findByTenantIdAndId(TENANT_ID, ORG_ID))
                .thenReturn(Optional.of(mock(Organization.class)));
        when(grantRepository.save(any(UserRoleGrant.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void tenantWideGrantEmitsCanonicalGrantedEventWithoutScopeEvent() {
        when(grantRepository.findByTenantIdAndUserIdAndRoleIdAndOrganizationIdIsNull(TENANT_ID, USER_ID, ROLE_ID))
                .thenReturn(Optional.empty());

        service.grant(TENANT_ID, USER_ID, ROLE_ID, null);

        ArgumentCaptor<String> action = ArgumentCaptor.forClass(String.class);
        verify(audit, times(1)).success(eq(TENANT_ID), action.capture(), eq("USER_ROLE_GRANT"),
                any(), any(), any());
        assertThat(action.getValue()).isEqualTo("USER_ROLE_GRANTED");
    }

    @Test
    void organizationGrantEmitsGrantedAndDistinctScopeChangedFacts() {
        when(grantRepository.findByTenantIdAndUserIdAndRoleIdAndOrganizationId(TENANT_ID, USER_ID, ROLE_ID, ORG_ID))
                .thenReturn(Optional.empty());

        service.grant(TENANT_ID, USER_ID, ROLE_ID, ORG_ID);

        ArgumentCaptor<String> action = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object> after = ArgumentCaptor.forClass(Object.class);
        verify(audit, times(2)).success(eq(TENANT_ID), action.capture(), eq("USER_ROLE_GRANT"),
                any(), any(), after.capture());
        assertThat(action.getAllValues()).containsExactly("USER_ROLE_GRANTED", "USER_SCOPE_CHANGED");
        @SuppressWarnings("unchecked")
        Map<String, Object> scopeEvent = (Map<String, Object>) after.getAllValues().get(1);
        assertThat(scopeEvent).containsEntry("organizationId", ORG_ID)
                .containsEntry("scope", "ORGANIZATION")
                .containsEntry("operation", "GRANTED")
                .containsEntry("roleId", ROLE_ID);
    }

    @Test
    void reGrantOfAlreadyActiveGrantEmitsNoFalseChangeAudit() {
        UserRoleGrant existing = new UserRoleGrant(TENANT_ID, USER_ID, ROLE_ID, ORG_ID);
        existing.setStatus(UserGrantStatus.ACTIVE);
        when(grantRepository.findByTenantIdAndUserIdAndRoleIdAndOrganizationId(TENANT_ID, USER_ID, ROLE_ID, ORG_ID))
                .thenReturn(Optional.of(existing));

        UserAccessResponse response = service.grant(TENANT_ID, USER_ID, ROLE_ID, ORG_ID);

        assertThat(response.status()).isEqualTo(UserGrantStatus.ACTIVE);
        verifyNoInteractions(audit);
    }

    @Test
    void revokeEmitsCanonicalRevokedEvent() {
        UUID grantId = UUID.randomUUID();
        UserRoleGrant grant = storedGrant(grantId, TENANT_ID, USER_ID, ROLE_ID, null, UserGrantStatus.ACTIVE);
        when(grantRepository.findByTenantIdAndId(TENANT_ID, grantId)).thenReturn(Optional.of(grant));

        service.revoke(TENANT_ID, grantId);

        ArgumentCaptor<String> action = ArgumentCaptor.forClass(String.class);
        verify(audit, times(1)).success(eq(TENANT_ID), action.capture(), eq("USER_ROLE_GRANT"),
                any(), any(), any());
        assertThat(action.getValue()).isEqualTo("USER_ROLE_REVOKED");
    }

    @Test
    void organizationRevokeEmitsDistinctScopeChangedRevokedFact() {
        UUID grantId = UUID.randomUUID();
        UserRoleGrant grant = storedGrant(grantId, TENANT_ID, USER_ID, ROLE_ID, ORG_ID, UserGrantStatus.ACTIVE);
        when(grantRepository.findByTenantIdAndId(TENANT_ID, grantId)).thenReturn(Optional.of(grant));

        service.revoke(TENANT_ID, grantId);

        // The authority fact and the scope fact are distinct audit events.
        verify(audit, times(1)).success(eq(TENANT_ID), eq("USER_ROLE_REVOKED"),
                eq("USER_ROLE_GRANT"), any(), any(), any());
        // On revoke, the scope fact is carried in the before-state slot
        // (the after-state is null: the binding no longer exists).
        ArgumentCaptor<Object> scopeBefore = ArgumentCaptor.forClass(Object.class);
        verify(audit, times(1)).success(eq(TENANT_ID), eq("USER_SCOPE_CHANGED"),
                eq("USER_ROLE_GRANT"), any(), scopeBefore.capture(), any());
        @SuppressWarnings("unchecked")
        Map<String, Object> scopeEvent = (Map<String, Object>) scopeBefore.getValue();
        assertThat(scopeEvent).containsEntry("operation", "REVOKED")
                .containsEntry("organizationId", ORG_ID)
                .containsEntry("userId", USER_ID)
                .containsEntry("roleId", ROLE_ID);
    }

    @Test
    void reRevokeOfAlreadyRevokedGrantEmitsNoFalseChangeAudit() {
        UUID grantId = UUID.randomUUID();
        UserRoleGrant grant = storedGrant(grantId, TENANT_ID, USER_ID, ROLE_ID, null, UserGrantStatus.REVOKED);
        when(grantRepository.findByTenantIdAndId(TENANT_ID, grantId)).thenReturn(Optional.of(grant));

        service.revoke(TENANT_ID, grantId);

        verifyNoInteractions(audit);
    }

    @Test
    void crossTenantGrantTargetFailsClosedWithoutAudit() {
        UserRepository unknownUserRepo = mock(UserRepository.class);
        when(unknownUserRepo.findByTenantIdAndId(TENANT_ID, USER_ID)).thenReturn(Optional.empty());
        UserRoleGrantService isolated = new UserRoleGrantService(grantRepository, unknownUserRepo,
                mock(OrganizationRepository.class), mock(RoleService.class));
        isolated.setAudit(audit);

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                        isolated.grant(TENANT_ID, USER_ID, ROLE_ID, null))
                .isInstanceOf(AccessResourceNotFoundException.class);
        verifyNoInteractions(audit);
    }

    private static UserRoleGrant storedGrant(UUID id, UUID tenantId, UUID userId, UUID roleId,
                                             UUID organizationId, UserGrantStatus status) {
        UserRoleGrant grant = new UserRoleGrant(tenantId, userId, roleId, organizationId);
        grant.setStatus(status);
        try {
            java.lang.reflect.Field idField = UserRoleGrant.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(grant, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unable to assign stored grant id for test", e);
        }
        return grant;
    }
}
