package com.sanad.platform.access;

import com.sanad.platform.access.role.*;
import com.sanad.platform.tenant.repository.TenantRepository;
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
class RoleCatalogServiceTest {

    @Mock private RoleRepository roleRepository;
    @Mock private TenantRepository tenantRepository;

    private RoleService service;
    private UUID tenantId;
    private UUID roleId;
    private Role role;

    @BeforeEach
    void setUp() {
        service = new RoleService(roleRepository, tenantRepository);
        tenantId = UUID.randomUUID();
        roleId = UUID.randomUUID();
        role = new Role(tenantId, "ADMIN", "Admin", "desc");
    }

    @Test
    void createNormalizesAndPersistsRole() {
        when(tenantRepository.existsById(tenantId)).thenReturn(true);
        when(roleRepository.existsByTenantIdAndCode(tenantId, "ADMIN")).thenReturn(false);
        when(roleRepository.save(any(Role.class))).thenAnswer(call -> call.getArgument(0));

        RoleResponse response = service.create(tenantId,
                new CreateRoleRequest(" admin ", " Admin ", " desc "));

        assertThat(response.code()).isEqualTo("ADMIN");
        assertThat(response.name()).isEqualTo("Admin");
        assertThat(response.status()).isEqualTo(RoleStatus.ACTIVE);
    }

    @Test
    void duplicateRoleCodeIsRejected() {
        when(tenantRepository.existsById(tenantId)).thenReturn(true);
        when(roleRepository.existsByTenantIdAndCode(tenantId, "ADMIN")).thenReturn(true);

        assertThatThrownBy(() -> service.create(tenantId,
                new CreateRoleRequest("ADMIN", "Admin", null)))
                .isInstanceOf(AccessConflictException.class);
        verify(roleRepository, never()).save(any());
    }

    @Test
    void missingTenantIsRejected() {
        when(tenantRepository.existsById(tenantId)).thenReturn(false);
        assertThatThrownBy(() -> service.list(tenantId))
                .isInstanceOf(AccessResourceNotFoundException.class)
                .hasMessage("Tenant not found");
    }

    @Test
    void listIsTenantScoped() {
        when(tenantRepository.existsById(tenantId)).thenReturn(true);
        when(roleRepository.findByTenantIdOrderByCodeAsc(tenantId)).thenReturn(List.of(role));
        assertThat(service.list(tenantId)).extracting(RoleResponse::code)
                .containsExactly("ADMIN");
    }

    @Test
    void unknownRoleIsRejected() {
        when(roleRepository.findByTenantIdAndId(tenantId, roleId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.get(tenantId, roleId))
                .isInstanceOf(AccessResourceNotFoundException.class)
                .hasMessage("Role not found");
    }

    @Test
    void updateChangesMutableFieldsOnly() {
        when(roleRepository.findByTenantIdAndId(tenantId, roleId)).thenReturn(Optional.of(role));
        when(roleRepository.save(role)).thenReturn(role);

        RoleResponse response = service.update(tenantId, roleId,
                new UpdateRoleRequest("Operations", "updated"));
        assertThat(response.code()).isEqualTo("ADMIN");
        assertThat(response.name()).isEqualTo("Operations");
    }

    @Test
    void statusLifecyclePersists() {
        when(roleRepository.findByTenantIdAndId(tenantId, roleId)).thenReturn(Optional.of(role));
        when(roleRepository.save(role)).thenReturn(role);

        assertThat(service.changeStatus(tenantId, roleId, RoleStatus.INACTIVE).status())
                .isEqualTo(RoleStatus.INACTIVE);
        assertThat(service.changeStatus(tenantId, roleId, RoleStatus.ARCHIVED).status())
                .isEqualTo(RoleStatus.ARCHIVED);
    }
}
