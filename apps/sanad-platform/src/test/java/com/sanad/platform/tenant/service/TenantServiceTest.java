package com.sanad.platform.tenant.service;

import com.sanad.platform.tenant.domain.Tenant;
import com.sanad.platform.tenant.domain.TenantStatus;
import com.sanad.platform.tenant.dto.CreateTenantRequest;
import com.sanad.platform.tenant.dto.TenantResponse;
import com.sanad.platform.tenant.dto.UpdateTenantRequest;
import com.sanad.platform.tenant.exception.TenantAlreadyExistsException;
import com.sanad.platform.tenant.mapper.TenantMapper;
import com.sanad.platform.tenant.repository.TenantRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Pure unit tests for {@link TenantService} using Mockito.
 */
@ExtendWith(MockitoExtension.class)
class TenantServiceTest {

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private TenantMapper tenantMapper;

    @InjectMocks
    private TenantService tenantService;

    private UUID tenantId;
    private Tenant tenant;
    private TenantResponse expectedResponse;

    @BeforeEach
    void setUp() {
        tenantId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        tenant = new Tenant("Acme Corp", "acme", TenantStatus.ACTIVE);
        reflectSet(tenant, "id", tenantId);
        Instant now = Instant.now();
        reflectSet(tenant, "createdAt", now);
        reflectSet(tenant, "updatedAt", now);

        expectedResponse = new TenantResponse(tenantId, "Acme Corp", "acme",
                TenantStatus.ACTIVE, now, now);
    }

    @Test
    @DisplayName("createTenant: success - returns TenantResponse with ACTIVE status")
    void createTenant_success_returnsResponse() {
        CreateTenantRequest request = new CreateTenantRequest("Acme Corp", "acme");
        when(tenantRepository.existsBySubdomain("acme")).thenReturn(false);
        when(tenantRepository.save(any(Tenant.class))).thenReturn(tenant);
        when(tenantMapper.toResponse(tenant)).thenReturn(expectedResponse);

        TenantResponse result = tenantService.createTenant(request);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(tenantId);
        assertThat(result.getName()).isEqualTo("Acme Corp");
        assertThat(result.getSubdomain()).isEqualTo("acme");
        assertThat(result.getStatus()).isEqualTo(TenantStatus.ACTIVE);
        verify(tenantRepository, times(1)).existsBySubdomain("acme");
        verify(tenantRepository, times(1)).save(any(Tenant.class));
    }

    @Test
    @DisplayName("createTenant: duplicate subdomain -> TenantAlreadyExistsException")
    void createTenant_duplicateSubdomain_throwsException() {
        CreateTenantRequest request = new CreateTenantRequest("Acme Corp", "acme");
        when(tenantRepository.existsBySubdomain("acme")).thenReturn(true);

        assertThatThrownBy(() -> tenantService.createTenant(request))
                .isInstanceOf(TenantAlreadyExistsException.class)
                .hasMessageContaining("Tenant already exists with subdomain: acme");

        verify(tenantRepository, never()).save(any(Tenant.class));
    }

    @Test
    @DisplayName("getTenant: success - returns response")
    void getTenant_success_returnsResponse() {
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(tenantMapper.toResponse(tenant)).thenReturn(expectedResponse);

        TenantResponse result = tenantService.getTenant(tenantId);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(tenantId);
        verify(tenantRepository, times(1)).findById(tenantId);
    }

    @Test
    @DisplayName("getTenant: not found -> EntityNotFoundException")
    void getTenant_notFound_throwsException() {
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tenantService.getTenant(tenantId))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("Tenant not found with id");
    }

    @Test
    @DisplayName("listTenants: returns list of responses")
    void listTenants_returnsList() {
        Tenant second = new Tenant("Beta Corp", "beta", TenantStatus.ACTIVE);
        reflectSet(second, "id", UUID.fromString("22222222-2222-2222-2222-222222222222"));
        when(tenantRepository.findAll()).thenReturn(List.of(tenant, second));
        when(tenantMapper.toResponse(tenant)).thenReturn(expectedResponse);
        when(tenantMapper.toResponse(second)).thenReturn(
                new TenantResponse(second.getId(), "Beta Corp", "beta",
                        TenantStatus.ACTIVE, Instant.now(), Instant.now()));

        List<TenantResponse> result = tenantService.listTenants();

        assertThat(result).hasSize(2);
        assertThat(result).extracting(TenantResponse::getName)
                .containsExactlyInAnyOrder("Acme Corp", "Beta Corp");
    }

    @Test
    @DisplayName("updateTenant: success - updates name, preserves subdomain")
    void updateTenant_success_updatesName() {
        UpdateTenantRequest request = new UpdateTenantRequest("Acme Renamed");
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(tenantRepository.save(tenant)).thenReturn(tenant);
        when(tenantMapper.toResponse(tenant)).thenReturn(
                new TenantResponse(tenantId, "Acme Renamed", "acme",
                        TenantStatus.ACTIVE, Instant.now(), Instant.now()));

        TenantResponse result = tenantService.updateTenant(tenantId, request);

        assertThat(result.getName()).isEqualTo("Acme Renamed");
        assertThat(tenant.getName()).isEqualTo("Acme Renamed");
        assertThat(tenant.getSubdomain()).isEqualTo("acme"); // unchanged
    }

    @Test
    @DisplayName("updateTenant: not found -> EntityNotFoundException")
    void updateTenant_notFound_throwsException() {
        UpdateTenantRequest request = new UpdateTenantRequest("New Name");
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tenantService.updateTenant(tenantId, request))
                .isInstanceOf(EntityNotFoundException.class);
        verify(tenantRepository, never()).save(any(Tenant.class));
    }

    @Test
    @DisplayName("activateTenant: success - sets status to ACTIVE")
    void activateTenant_success_setsActive() {
        tenant.setStatus(TenantStatus.SUSPENDED);
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(tenantRepository.save(tenant)).thenReturn(tenant);
        when(tenantMapper.toResponse(tenant)).thenReturn(
                new TenantResponse(tenantId, "Acme Corp", "acme",
                        TenantStatus.ACTIVE, Instant.now(), Instant.now()));

        TenantResponse result = tenantService.activateTenant(tenantId);

        assertThat(result.getStatus()).isEqualTo(TenantStatus.ACTIVE);
        assertThat(tenant.getStatus()).isEqualTo(TenantStatus.ACTIVE);
    }

    @Test
    @DisplayName("deactivateTenant: success - sets status to SUSPENDED")
    void deactivateTenant_success_setsSuspended() {
        tenant.setStatus(TenantStatus.ACTIVE);
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(tenantRepository.save(tenant)).thenReturn(tenant);
        when(tenantMapper.toResponse(tenant)).thenReturn(
                new TenantResponse(tenantId, "Acme Corp", "acme",
                        TenantStatus.SUSPENDED, Instant.now(), Instant.now()));

        TenantResponse result = tenantService.deactivateTenant(tenantId);

        assertThat(result.getStatus()).isEqualTo(TenantStatus.SUSPENDED);
        assertThat(tenant.getStatus()).isEqualTo(TenantStatus.SUSPENDED);
    }

    @Test
    @DisplayName("archiveTenant: success - sets status to ARCHIVED")
    void archiveTenant_success_setsArchived() {
        tenant.setStatus(TenantStatus.ACTIVE);
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(tenantRepository.save(tenant)).thenReturn(tenant);
        when(tenantMapper.toResponse(tenant)).thenReturn(
                new TenantResponse(tenantId, "Acme Corp", "acme",
                        TenantStatus.ARCHIVED, Instant.now(), Instant.now()));

        TenantResponse result = tenantService.archiveTenant(tenantId);

        assertThat(result.getStatus()).isEqualTo(TenantStatus.ARCHIVED);
        assertThat(tenant.getStatus()).isEqualTo(TenantStatus.ARCHIVED);
        verify(tenantRepository, times(1)).save(tenant);
    }

    @Test
    @DisplayName("archiveTenant: already archived - idempotent, no save")
    void archiveTenant_alreadyArchived_idempotent() {
        tenant.setStatus(TenantStatus.ARCHIVED);
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(tenantMapper.toResponse(tenant)).thenReturn(
                new TenantResponse(tenantId, "Acme Corp", "acme",
                        TenantStatus.ARCHIVED, Instant.now(), Instant.now()));

        TenantResponse result = tenantService.archiveTenant(tenantId);

        assertThat(result.getStatus()).isEqualTo(TenantStatus.ARCHIVED);
        verify(tenantRepository, never()).save(any(Tenant.class));
    }

    @Test
    @DisplayName("archiveTenant: not found -> EntityNotFoundException")
    void archiveTenant_notFound_throwsException() {
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tenantService.archiveTenant(tenantId))
                .isInstanceOf(EntityNotFoundException.class);
        verify(tenantRepository, never()).save(any(Tenant.class));
    }

    private static void reflectSet(Object target, String fieldName, Object value) {
        try {
            java.lang.reflect.Field f = target.getClass().getDeclaredField(fieldName);
            f.setAccessible(true);
            f.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
