package com.sanad.platform.access;

import com.sanad.platform.access.capability.*;
import com.sanad.platform.access.role.*;
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
class RoleAccessMappingServiceTest {

    @Mock private RoleCapabilityRepository mappingRepository;
    @Mock private RoleService roleService;
    @Mock private AccessCapabilityService capabilityService;

    private RoleCapabilityService service;
    private UUID tenantId;
    private UUID roleId;
    private UUID capabilityId;
    private Role role;
    private AccessCapability capability;

    @BeforeEach
    void setUp() {
        service = new RoleCapabilityService(
                mappingRepository, roleService, capabilityService);
        tenantId = UUID.randomUUID();
        roleId = UUID.randomUUID();
        capabilityId = UUID.randomUUID();
        role = new Role(tenantId, "ADMIN", "Admin", null);
        capability = new AccessCapability("USER.READ", "Read users", null);
    }

    @Test
    void attachPersistsActiveMapping() {
        when(roleService.load(tenantId, roleId)).thenReturn(role);
        when(capabilityService.load(capabilityId)).thenReturn(capability);
        when(mappingRepository.findByTenantIdAndRoleIdAndCapabilityId(
                tenantId, roleId, capabilityId)).thenReturn(Optional.empty());
        when(mappingRepository.save(any(RoleCapability.class)))
                .thenAnswer(call -> call.getArgument(0));

        RoleAccessResponse response = service.attach(tenantId, roleId, capabilityId);
        assertThat(response.capabilityCode()).isEqualTo("USER.READ");
        verify(mappingRepository).save(any(RoleCapability.class));
    }

    @Test
    void attachIsIdempotent() {
        RoleCapability mapping = new RoleCapability(tenantId, roleId, capabilityId);
        when(roleService.load(tenantId, roleId)).thenReturn(role);
        when(capabilityService.load(capabilityId)).thenReturn(capability);
        when(mappingRepository.findByTenantIdAndRoleIdAndCapabilityId(
                tenantId, roleId, capabilityId)).thenReturn(Optional.of(mapping));

        service.attach(tenantId, roleId, capabilityId);
        verify(mappingRepository, never()).save(any());
    }

    @Test
    void inactiveRoleCannotReceiveCatalogItem() {
        role.setStatus(RoleStatus.INACTIVE);
        when(roleService.load(tenantId, roleId)).thenReturn(role);
        when(capabilityService.load(capabilityId)).thenReturn(capability);

        assertThatThrownBy(() -> service.attach(tenantId, roleId, capabilityId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("active roles");
    }

    @Test
    void inactiveCatalogItemCannotBeAttached() {
        capability.setStatus(CapabilityStatus.INACTIVE);
        when(roleService.load(tenantId, roleId)).thenReturn(role);
        when(capabilityService.load(capabilityId)).thenReturn(capability);

        assertThatThrownBy(() -> service.attach(tenantId, roleId, capabilityId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("active capabilities");
    }

    @Test
    void detachDeletesExistingMapping() {
        RoleCapability mapping = new RoleCapability(tenantId, roleId, capabilityId);
        when(roleService.load(tenantId, roleId)).thenReturn(role);
        when(mappingRepository.findByTenantIdAndRoleIdAndCapabilityId(
                tenantId, roleId, capabilityId)).thenReturn(Optional.of(mapping));

        service.detach(tenantId, roleId, capabilityId);
        verify(mappingRepository).delete(mapping);
    }

    @Test
    void listResolvesCatalogCodes() {
        RoleCapability mapping = new RoleCapability(tenantId, roleId, capabilityId);
        when(roleService.load(tenantId, roleId)).thenReturn(role);
        when(mappingRepository.findByTenantIdAndRoleId(tenantId, roleId))
                .thenReturn(List.of(mapping));
        when(capabilityService.load(capabilityId)).thenReturn(capability);

        assertThat(service.list(tenantId, roleId))
                .extracting(RoleAccessResponse::capabilityCode)
                .containsExactly("USER.READ");
    }
}
