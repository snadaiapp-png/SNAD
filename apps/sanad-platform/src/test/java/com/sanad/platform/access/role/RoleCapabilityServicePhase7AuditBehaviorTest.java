package com.sanad.platform.access.role;

import com.sanad.platform.access.audit.AccessMutationAuditSupport;
import com.sanad.platform.access.capability.AccessCapability;
import com.sanad.platform.access.capability.AccessCapabilityService;
import com.sanad.platform.access.capability.CapabilityStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

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
 * Phase 7 behavioral audit contract for role capability mutations: canonical
 * event names (ROLE_CAPABILITY_ATTACHED / ROLE_CAPABILITY_DETACHED) and zero
 * false-change events for idempotent no-ops.
 */
class RoleCapabilityServicePhase7AuditBehaviorTest {

    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ROLE_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID CAPABILITY_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");

    private RoleCapabilityRepository mappingRepository;
    private AccessMutationAuditSupport audit;
    private RoleCapabilityService service;

    @BeforeEach
    void setUp() {
        mappingRepository = mock(RoleCapabilityRepository.class);
        RoleService roleService = mock(RoleService.class);
        AccessCapabilityService capabilityService = mock(AccessCapabilityService.class);
        service = new RoleCapabilityService(mappingRepository, roleService, capabilityService);
        service.setAudit(audit = mock(AccessMutationAuditSupport.class));
        service.setProtectedRoleGuard(null);

        Role role = mock(Role.class);
        when(role.getId()).thenReturn(ROLE_ID);
        when(role.getStatus()).thenReturn(RoleStatus.ACTIVE);
        when(roleService.load(TENANT_ID, ROLE_ID)).thenReturn(role);
        AccessCapability capability = mock(AccessCapability.class);
        when(capability.getId()).thenReturn(CAPABILITY_ID);
        when(capability.getCode()).thenReturn("USER.READ");
        when(capability.getStatus()).thenReturn(CapabilityStatus.ACTIVE);
        when(capabilityService.load(CAPABILITY_ID)).thenReturn(capability);
    }

    @Test
    void attachEmitsCanonicalAttachedEvent() {
        when(mappingRepository.findByTenantIdAndRoleIdAndCapabilityId(TENANT_ID, ROLE_ID, CAPABILITY_ID))
                .thenReturn(Optional.empty());
        when(mappingRepository.save(any(RoleCapability.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.attach(TENANT_ID, ROLE_ID, CAPABILITY_ID);

        ArgumentCaptor<String> action = ArgumentCaptor.forClass(String.class);
        verify(audit, times(1)).success(eq(TENANT_ID), action.capture(), eq("ROLE_CAPABILITY"),
                any(), any(), any());
        assertThat(action.getValue()).isEqualTo("ROLE_CAPABILITY_ATTACHED");
    }

    @Test
    void reAttachOfExistingMappingEmitsNoFalseChangeAudit() {
        RoleCapability existing = new RoleCapability(TENANT_ID, ROLE_ID, CAPABILITY_ID);
        when(mappingRepository.findByTenantIdAndRoleIdAndCapabilityId(TENANT_ID, ROLE_ID, CAPABILITY_ID))
                .thenReturn(Optional.of(existing));

        service.attach(TENANT_ID, ROLE_ID, CAPABILITY_ID);

        verifyNoInteractions(audit);
    }

    @Test
    void detachEmitsCanonicalDetachedEvent() {
        RoleCapability mapping = new RoleCapability(TENANT_ID, ROLE_ID, CAPABILITY_ID);
        when(mappingRepository.findByTenantIdAndRoleIdAndCapabilityId(TENANT_ID, ROLE_ID, CAPABILITY_ID))
                .thenReturn(Optional.of(mapping));

        service.detach(TENANT_ID, ROLE_ID, CAPABILITY_ID);

        ArgumentCaptor<String> action = ArgumentCaptor.forClass(String.class);
        verify(audit, times(1)).success(eq(TENANT_ID), action.capture(), eq("ROLE_CAPABILITY"),
                any(), any(), any());
        assertThat(action.getValue()).isEqualTo("ROLE_CAPABILITY_DETACHED");
    }

    @Test
    void detachOfAbsentMappingEmitsNoAudit() {
        when(mappingRepository.findByTenantIdAndRoleIdAndCapabilityId(TENANT_ID, ROLE_ID, CAPABILITY_ID))
                .thenReturn(Optional.empty());

        service.detach(TENANT_ID, ROLE_ID, CAPABILITY_ID);

        verifyNoInteractions(audit);
    }
}
