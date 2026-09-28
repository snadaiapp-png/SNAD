package com.sanad.platform.access.override;

import com.sanad.platform.access.capability.AccessCapability;
import com.sanad.platform.access.capability.AccessCapabilityService;
import com.sanad.platform.access.capability.CapabilityStatus;
import com.sanad.platform.access.evaluation.AuthorizationVersionService;
import com.sanad.platform.access.override.dto.CreateOverrideRequest;
import com.sanad.platform.access.override.dto.OverrideResponse;
import com.sanad.platform.admin.service.PlatformAuditWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Wave 1 Task 9 — override service validation matrix. Scoped DENY is rejected
 * in the service BEFORE persistence (DB CHECK is the backstop, proven by the
 * PostgreSQL Direct RLS test). Every mutation audits + events + bumps the
 * authorization version.
 */
class UserPermissionOverrideServiceTest {

    private static final String CAPABILITY_CODE = "CRM.ACCOUNT.READ";

    private final UUID tenantId = UUID.randomUUID();
    private final UUID actorUserId = UUID.randomUUID();
    private final UUID targetUserId = UUID.randomUUID();
    private final UUID capabilityId = UUID.randomUUID();

    private AccessCapabilityService capabilityService;
    private UserPermissionOverrideRepository repository;
    private AuthorizationVersionService authorizationVersionService;
    private PlatformAuditWriter auditWriter;
    private JdbcTemplate jdbc;
    private UserPermissionOverrideService service;

    @BeforeEach
    void wire() {
        capabilityService = mock(AccessCapabilityService.class);
        repository = mock(UserPermissionOverrideRepository.class);
        authorizationVersionService = mock(AuthorizationVersionService.class);
        auditWriter = mock(PlatformAuditWriter.class);
        jdbc = mock(JdbcTemplate.class);
        AccessCapability capability = mock(AccessCapability.class);
        when(capability.getId()).thenReturn(capabilityId);
        when(capability.getCode()).thenReturn(CAPABILITY_CODE);
        when(capability.getStatus()).thenReturn(CapabilityStatus.ACTIVE);
        when(capabilityService.loadByCode(CAPABILITY_CODE)).thenReturn(capability);
        when(jdbc.queryForObject(contains("FROM users"), eq(Integer.class), any(Object[].class)))
                .thenReturn(1);
        when(jdbc.queryForObject(contains("supports_scope"), eq(Boolean.class), any(Object[].class)))
                .thenReturn(true);
        service = new UserPermissionOverrideService(
                capabilityService, repository, authorizationVersionService, auditWriter, jdbc);
    }

    private CreateOverrideRequest request(String effect, String scopeType) {
        return new CreateOverrideRequest(targetUserId, CAPABILITY_CODE, effect, scopeType,
                scopeType == null ? null : UUID.randomUUID(), "forensic override test",
                Instant.now().minusSeconds(30), null);
    }

    @Test
    void scopedAllowRejectedWhenCapabilityDoesNotSupportScope() {
        when(jdbc.queryForObject(contains("supports_scope"), eq(Boolean.class), any(Object[].class)))
                .thenReturn(false);
        CreateOverrideRequest request = request("ALLOW", "TEAM");

        assertThatThrownBy(() -> service.create(tenantId, actorUserId, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("SCOPE_NOT_SUPPORTED");
        verify(repository, never()).insert(any());
    }

    @Test
    void unknownScopeTypeRejected() {
        CreateOverrideRequest request = request("ALLOW", "GALAXY");

        assertThatThrownBy(() -> service.create(tenantId, actorUserId, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("INVALID_SCOPE_TYPE");
        verify(repository, never()).insert(any());
    }

    @Test
    void scopedDenyRejectedInServiceBeforeDatabaseBackstop() {
        CreateOverrideRequest request = request("DENY", "TEAM");

        assertThatThrownBy(() -> service.create(tenantId, actorUserId, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("DENY_IS_CAPABILITY_WIDE");
        verify(repository, never()).insert(any());
        verify(authorizationVersionService, never()).bump(any(), any());
    }

    @Test
    void capabilityWideDenyPersistsAuditsEventsAndBumpsVersion() {
        CreateOverrideRequest request = request("DENY", null);
        when(repository.insert(any())).thenAnswer(invocation -> invocation.getArgument(0));

        OverrideResponse response = service.create(tenantId, actorUserId, request);

        assertThat(response.effect()).isEqualTo("DENY");
        assertThat(response.scopeType()).isNull();
        verify(repository).insert(any());
        verify(auditWriter).writeSuccess(
                eq(tenantId), eq(actorUserId), eq(tenantId), anyString(), anyString(),
                anyString(), anyString(), any(), any(), any(), any(Instant.class));
        verify(jdbc).update(contains("USER_OVERRIDE_CHANGED"),
                ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any(),
                ArgumentMatchers.any(), ArgumentMatchers.any());
        verify(authorizationVersionService).bump(tenantId, targetUserId);
    }

    @Test
    void expiredValidityWindowRejected() {
        CreateOverrideRequest request = new CreateOverrideRequest(targetUserId, CAPABILITY_CODE,
                "ALLOW", "TENANT_ALL", null, "forensic override test",
                Instant.now(), Instant.now().minusSeconds(60));

        assertThatThrownBy(() -> service.create(tenantId, actorUserId, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("INVALID_VALIDITY_WINDOW");
        verify(repository, never()).insert(any());
    }

    @Test
    void inactiveCapabilityRejected() {
        AccessCapability capability = mock(AccessCapability.class);
        when(capability.getStatus()).thenReturn(CapabilityStatus.INACTIVE);
        when(capabilityService.loadByCode(CAPABILITY_CODE)).thenReturn(capability);

        assertThatThrownBy(() -> service.create(tenantId, actorUserId, request("ALLOW", "TENANT_ALL")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("CAPABILITY_INACTIVE");
        verify(repository, never()).insert(any());
    }

    @Test
    void crossTenantCongruenceMismatchRejected() {
        when(jdbc.queryForObject(contains("FROM users"), eq(Integer.class), any(Object[].class)))
                .thenReturn(0);

        assertThatThrownBy(() -> service.create(tenantId, actorUserId, request("DENY", null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("USER_TENANT_MISMATCH");
        verify(repository, never()).insert(any());
    }

    @Test
    void revokeWritesAuditEventAndBumpsVersion() {
        UserPermissionOverride override = new UserPermissionOverride(UUID.randomUUID(), tenantId,
                targetUserId, capabilityId, "ALLOW", "TENANT_ALL", null, "forensic override test",
                Instant.now().minusSeconds(30), null, actorUserId, 0);
        when(repository.find(tenantId, override.getId())).thenReturn(Optional.of(override));
        when(repository.expire(tenantId, override.getId())).thenReturn(true);

        service.revoke(tenantId, actorUserId, override.getId());

        verify(repository).expire(tenantId, override.getId());
        verify(auditWriter).writeSuccess(
                eq(tenantId), eq(actorUserId), eq(tenantId), anyString(), anyString(),
                anyString(), anyString(), any(), any(), any(), any(Instant.class));
        verify(jdbc).update(contains("USER_OVERRIDE_CHANGED"),
                ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any(),
                ArgumentMatchers.any(), ArgumentMatchers.any());
        verify(authorizationVersionService).bump(tenantId, targetUserId);
    }

    @Test
    void listReturnsSubjectOverridesWithinTenantBoundary() {
        UserPermissionOverride override = new UserPermissionOverride(UUID.randomUUID(), tenantId,
                targetUserId, capabilityId, "DENY", null, null, "forensic override test",
                Instant.now().minusSeconds(30), null, actorUserId, 0);
        when(repository.listForUser(tenantId, targetUserId)).thenReturn(List.of(override));
        when(jdbc.queryForObject(contains("SELECT code FROM access_capabilities"),
                eq(String.class), any(Object[].class))).thenReturn(CAPABILITY_CODE);

        List<OverrideResponse> responses = service.list(tenantId, targetUserId);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).effect()).isEqualTo("DENY");
    }
}
