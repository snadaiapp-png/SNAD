package com.sanad.platform.organization.service;

import com.sanad.platform.organization.domain.Organization;
import com.sanad.platform.organization.domain.OrganizationStatus;
import com.sanad.platform.organization.dto.CreateOrganizationRequest;
import com.sanad.platform.organization.dto.OrganizationResponse;
import com.sanad.platform.organization.exception.OrganizationAlreadyExistsException;
import com.sanad.platform.organization.mapper.OrganizationMapper;
import com.sanad.platform.organization.repository.OrganizationRepository;
import com.sanad.platform.tenant.domain.Tenant;
import com.sanad.platform.tenant.domain.TenantStatus;
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
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Pure unit tests for {@link OrganizationService}.
 *
 * <p>Uses Mockito to mock {@link TenantRepository},
 * {@link OrganizationRepository}, and {@link OrganizationMapper} so that
 * the service's business logic can be tested in isolation without
 * touching a database.</p>
 *
 * <p>Three test cases required by EXEC-PROMPT-006:</p>
 * <ol>
 *   <li>CASE 1 — Successful Creation: Organization is saved</li>
 *   <li>CASE 2 — Duplicate Organization: OrganizationAlreadyExistsException</li>
 *   <li>CASE 3 — Invalid Tenant: EntityNotFoundException</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
class OrganizationServiceTest {

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private OrganizationRepository organizationRepository;

    @Mock
    private OrganizationMapper organizationMapper;

    @InjectMocks
    private OrganizationService organizationService;

    // Common test fixtures
    private UUID tenantId;
    private Tenant tenant;
    private CreateOrganizationRequest createRequest;
    private Organization savedOrganization;
    private OrganizationResponse expectedResponse;

    @BeforeEach
    void setUp() {
        tenantId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        tenant = new Tenant("Acme Corp", "acme", TenantStatus.ACTIVE);
        // Simulate JPA having populated the id field
        reflectSet(tenant, "id", tenantId);
        // Simulate JPA auditing
        Instant now = Instant.now();
        reflectSet(tenant, "createdAt", now);
        reflectSet(tenant, "updatedAt", now);

        createRequest = new CreateOrganizationRequest(
                tenantId,
                "Acme Riyadh Branch",
                "Main Riyadh operations"
        );

        savedOrganization = new Organization(
                tenant,
                "Acme Riyadh Branch",
                "Main Riyadh operations",
                OrganizationStatus.ACTIVE
        );
        UUID orgId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        reflectSet(savedOrganization, "id", orgId);
        reflectSet(savedOrganization, "createdAt", now);
        reflectSet(savedOrganization, "updatedAt", now);

        expectedResponse = new OrganizationResponse(
                orgId,
                tenantId,
                "Acme Riyadh Branch",
                "Main Riyadh operations",
                OrganizationStatus.ACTIVE,
                now,
                now
        );
    }

    /**
     * CASE 1 — Successful Creation.
     *
     * <p>Given a valid request and a non-duplicate name,
     * when {@code createOrganization} is called,
     * then the Organization is saved and a fully-populated response is returned.</p>
     */
    @Test
    @DisplayName("CASE 1: Successful Creation — Organization is saved and response returned")
    void createOrganization_successfulCreation_savesAndReturnsResponse() {
        // --- Arrange ---
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(organizationRepository.existsByTenantIdAndName(tenantId, "Acme Riyadh Branch"))
                .thenReturn(false);
        when(organizationRepository.save(any(Organization.class))).thenReturn(savedOrganization);
        when(organizationMapper.toResponse(savedOrganization)).thenReturn(expectedResponse);

        // --- Act ---
        OrganizationResponse actualResponse = organizationService.createOrganization(createRequest);

        // --- Assert ---
        assertThat(actualResponse).isNotNull();
        assertThat(actualResponse.getId()).isEqualTo(expectedResponse.getId());
        assertThat(actualResponse.getTenantId()).isEqualTo(tenantId);
        assertThat(actualResponse.getName()).isEqualTo("Acme Riyadh Branch");
        assertThat(actualResponse.getDescription()).isEqualTo("Main Riyadh operations");
        assertThat(actualResponse.getStatus()).isEqualTo(OrganizationStatus.ACTIVE);
        assertThat(actualResponse.getCreatedAt()).isNotNull();
        assertThat(actualResponse.getUpdatedAt()).isNotNull();

        // Verify interactions with mocks
        verify(tenantRepository, times(1)).findById(tenantId);
        verify(organizationRepository, times(1)).existsByTenantIdAndName(tenantId, "Acme Riyadh Branch");
        verify(organizationRepository, times(1)).save(any(Organization.class));
        verify(organizationMapper, times(1)).toResponse(savedOrganization);

        // Verify the Organization passed to save() is correctly wired to the Tenant
        // and uses ACTIVE status (Business Rule 6)
        org.mockito.ArgumentCaptor<Organization> orgCaptor =
                org.mockito.ArgumentCaptor.forClass(Organization.class);
        verify(organizationRepository).save(orgCaptor.capture());
        Organization captured = orgCaptor.getValue();
        assertThat(captured.getTenant()).isSameAs(tenant);
        assertThat(captured.getName()).isEqualTo("Acme Riyadh Branch");
        assertThat(captured.getStatus()).isEqualTo(OrganizationStatus.ACTIVE);
    }

    /**
     * CASE 2 — Duplicate Organization.
     *
     * <p>Given the (tenantId, name) pair already exists,
     * when {@code createOrganization} is called,
     * then {@link OrganizationAlreadyExistsException} is thrown and no save occurs.</p>
     */
    @Test
    @DisplayName("CASE 2: Duplicate Organization — throws OrganizationAlreadyExistsException")
    void createOrganization_duplicateName_throwsOrganizationAlreadyExistsException() {
        // --- Arrange ---
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(organizationRepository.existsByTenantIdAndName(tenantId, "Acme Riyadh Branch"))
                .thenReturn(true);

        // --- Act + Assert ---
        assertThatThrownBy(() -> organizationService.createOrganization(createRequest))
                .isInstanceOf(OrganizationAlreadyExistsException.class)
                .hasMessage("Organization already exists for this tenant");

        // Verify save was NEVER called (the precondition failed before reaching save)
        verify(organizationRepository, never()).save(any(Organization.class));
        verify(organizationMapper, never()).toResponse(any(Organization.class));
    }

    /**
     * CASE 3 — Invalid Tenant.
     *
     * <p>Given the tenantId does not correspond to any Tenant in the repository,
     * when {@code createOrganization} is called,
     * then {@link EntityNotFoundException} is thrown and neither the duplicate
     * check nor save is performed.</p>
     */
    @Test
    @DisplayName("CASE 3: Invalid Tenant — throws EntityNotFoundException")
    void createOrganization_invalidTenant_throwsEntityNotFoundException() {
        // --- Arrange ---
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.empty());

        // --- Act + Assert ---
        assertThatThrownBy(() -> organizationService.createOrganization(createRequest))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("Tenant not found with id")
                .hasMessageContaining(tenantId.toString());

        // Verify that the duplicate-check and save were NEVER called
        // (we failed at the first business rule)
        verify(organizationRepository, never()).existsByTenantIdAndName(any(UUID.class), anyString());
        verify(organizationRepository, never()).save(any(Organization.class));
        verify(organizationMapper, never()).toResponse(any(Organization.class));
    }

    // ------------------------------------------------------------
    // Test helpers
    // ------------------------------------------------------------

    /**
     * Reflectively set a private field on the target object. Used in
     * test setup to simulate what JPA would do (auto-populate id,
     * createdAt, updatedAt) without having to expose setters on the
     * domain entities.
     */
    private static void reflectSet(Object target, String fieldName, Object value) {
        try {
            java.lang.reflect.Field f = target.getClass().getDeclaredField(fieldName);
            f.setAccessible(true);
            f.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Could not set field " + fieldName +
                    " on " + target.getClass().getSimpleName(), e);
        }
    }
}
