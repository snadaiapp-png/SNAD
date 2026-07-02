package com.sanad.platform.organization.service;

import com.sanad.platform.organization.domain.Organization;
import com.sanad.platform.organization.domain.OrganizationStatus;
import com.sanad.platform.organization.dto.CreateOrganizationRequest;
import com.sanad.platform.organization.dto.OrganizationResponse;
import com.sanad.platform.organization.dto.UpdateOrganizationRequest;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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
        OrganizationResponse actualResponse = organizationService.createOrganization(tenantId, createRequest);

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
        assertThatThrownBy(() -> organizationService.createOrganization(tenantId, createRequest))
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
        assertThatThrownBy(() -> organizationService.createOrganization(tenantId, createRequest))
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

    // ============================================================
    // EXEC-PROMPT-008 — Read use case tests
    // ============================================================

    /**
     * STEP 6 / TEST 1 — getOrganization success.
     *
     * <p>Given a valid (tenantId, organizationId) pair,
     * when {@code getOrganization} is called,
     * then the matching Organization is returned as a response DTO.</p>
     */
    @Test
    @DisplayName("getOrganization: success - returns OrganizationResponse")
    void getOrganization_success_returnsResponse() {
        // --- Arrange ---
        UUID orgId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        when(organizationRepository.findByTenantIdAndId(tenantId, orgId))
                .thenReturn(Optional.of(savedOrganization));
        // savedOrganization has the tenantId we set in setUp()
        // Adjust its id to match orgId so the response id matches expectations
        reflectSet(savedOrganization, "id", orgId);
        when(organizationMapper.toResponse(savedOrganization)).thenReturn(
                new OrganizationResponse(orgId, tenantId, "Acme Riyadh Branch",
                        "Main Riyadh operations", OrganizationStatus.ACTIVE, Instant.now(), Instant.now()));

        // --- Act ---
        OrganizationResponse result = organizationService.getOrganization(tenantId, orgId);

        // --- Assert ---
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(orgId);
        assertThat(result.getTenantId()).isEqualTo(tenantId);
        assertThat(result.getName()).isEqualTo("Acme Riyadh Branch");
        verify(organizationRepository, times(1)).findByTenantIdAndId(tenantId, orgId);
        verify(organizationMapper, times(1)).toResponse(savedOrganization);
    }

    /**
     * STEP 6 / TEST 2 — getOrganization not found.
     *
     * <p>Given the (tenantId, organizationId) pair does not match any Organization
     * (either the id is wrong OR the id belongs to a different tenant),
     * when {@code getOrganization} is called,
     * then {@link EntityNotFoundException} is thrown.</p>
     */
    @Test
    @DisplayName("getOrganization: not found - throws EntityNotFoundException")
    void getOrganization_notFound_throwsEntityNotFoundException() {
        // --- Arrange ---
        UUID missingOrgId = UUID.fromString("44444444-4444-4444-4444-444444444444");
        when(organizationRepository.findByTenantIdAndId(tenantId, missingOrgId))
                .thenReturn(Optional.empty());

        // --- Act + Assert ---
        assertThatThrownBy(() -> organizationService.getOrganization(tenantId, missingOrgId))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("Organization not found with id")
                .hasMessageContaining(missingOrgId.toString())
                .hasMessageContaining(tenantId.toString());

        verify(organizationMapper, never()).toResponse(any(Organization.class));
    }

    /**
     * STEP 6 / TEST 3 — listOrganizations success.
     *
     * <p>Given a tenant with 2 organizations,
     * when {@code listOrganizations} is called,
     * then a list of 2 {@link OrganizationResponse} objects is returned.</p>
     */
    @Test
    @DisplayName("listOrganizations: success - returns list of OrganizationResponse")
    void listOrganizations_success_returnsListOfResponses() {
        // --- Arrange ---
        Organization second = new Organization(tenant, "Acme Jeddah Branch",
                "Jeddah operations", OrganizationStatus.ACTIVE);
        reflectSet(second, "id", UUID.fromString("55555555-5555-5555-5555-555555555555"));
        Instant ts = Instant.now();
        reflectSet(second, "createdAt", ts);
        reflectSet(second, "updatedAt", ts);

        when(organizationRepository.findByTenantId(tenantId))
                .thenReturn(List.of(savedOrganization, second));
        when(organizationMapper.toResponse(savedOrganization)).thenReturn(expectedResponse);
        OrganizationResponse secondResponse = new OrganizationResponse(
                second.getId(), tenantId, "Acme Jeddah Branch", "Jeddah operations",
                OrganizationStatus.ACTIVE, ts, ts);
        when(organizationMapper.toResponse(second)).thenReturn(secondResponse);

        // --- Act ---
        List<OrganizationResponse> result = organizationService.listOrganizations(tenantId);

        // --- Assert ---
        assertThat(result).isNotNull().hasSize(2);
        assertThat(result).extracting(OrganizationResponse::getName)
                .containsExactlyInAnyOrder("Acme Riyadh Branch", "Acme Jeddah Branch");
        verify(organizationRepository, times(1)).findByTenantId(tenantId);
        verify(organizationMapper, times(1)).toResponse(savedOrganization);
        verify(organizationMapper, times(1)).toResponse(second);
    }

    /**
     * STEP 6 / TEST 4 (bonus) — listOrganizations returns empty list when tenant has no organizations.
     */
    @Test
    @DisplayName("listOrganizations: empty list when tenant has no organizations")
    void listOrganizations_emptyTenant_returnsEmptyList() {
        when(organizationRepository.findByTenantId(tenantId))
                .thenReturn(List.of());

        List<OrganizationResponse> result = organizationService.listOrganizations(tenantId);

        assertThat(result).isNotNull().isEmpty();
        verify(organizationRepository, times(1)).findByTenantId(tenantId);
        verify(organizationMapper, never()).toResponse(any(Organization.class));
    }

    // ============================================================
    // EXEC-PROMPT-009 — Update + Status Management tests
    // ============================================================

    /**
     * TEST 1 — updateOrganization success.
     *
     * <p>When updating name + description on an existing Organization,
     * the service loads it via {@code findByTenantIdAndId}, sets the new
     * field values (without changing status), saves, and returns the response.</p>
     */
    @Test
    @DisplayName("updateOrganization: success - returns updated response")
    void updateOrganization_success_returnsUpdatedResponse() {
        // --- Arrange ---
        UUID orgId = savedOrganization.getId();
        UpdateOrganizationRequest update = new UpdateOrganizationRequest(
                "Acme Riyadh v2", "Updated description");

        when(organizationRepository.findByTenantIdAndId(tenantId, orgId))
                .thenReturn(Optional.of(savedOrganization));
        // New name differs from current name -> existsByTenantIdAndName is called
        when(organizationRepository.existsByTenantIdAndName(tenantId, "Acme Riyadh v2"))
                .thenReturn(false);
        when(organizationRepository.save(savedOrganization)).thenReturn(savedOrganization);
        when(organizationMapper.toResponse(savedOrganization)).thenReturn(
                new OrganizationResponse(orgId, tenantId, "Acme Riyadh v2", "Updated description",
                        OrganizationStatus.ACTIVE, Instant.now(), Instant.now()));

        // --- Act ---
        OrganizationResponse result = organizationService.updateOrganization(tenantId, orgId, update);

        // --- Assert ---
        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("Acme Riyadh v2");
        assertThat(result.getDescription()).isEqualTo("Updated description");
        assertThat(result.getStatus()).isEqualTo(OrganizationStatus.ACTIVE);
        // status not changed
        assertThat(savedOrganization.getName()).isEqualTo("Acme Riyadh v2");
        assertThat(savedOrganization.getDescription()).isEqualTo("Updated description");
        assertThat(savedOrganization.getStatus()).isEqualTo(OrganizationStatus.ACTIVE);

        verify(organizationRepository, times(1)).findByTenantIdAndId(tenantId, orgId);
        verify(organizationRepository, times(1)).existsByTenantIdAndName(tenantId, "Acme Riyadh v2");
        verify(organizationRepository, times(1)).save(savedOrganization);
    }

    /**
     * TEST 2 — updateOrganization with duplicate name (within same tenant) throws.
     */
    @Test
    @DisplayName("updateOrganization: duplicate name in same tenant -> OrganizationAlreadyExistsException")
    void updateOrganization_duplicateName_throwsException() {
        UUID orgId = savedOrganization.getId();
        UpdateOrganizationRequest update = new UpdateOrganizationRequest(
                "Existing Other Org", "x");

        when(organizationRepository.findByTenantIdAndId(tenantId, orgId))
                .thenReturn(Optional.of(savedOrganization));
        when(organizationRepository.existsByTenantIdAndName(tenantId, "Existing Other Org"))
                .thenReturn(true);

        assertThatThrownBy(() -> organizationService.updateOrganization(tenantId, orgId, update))
                .isInstanceOf(OrganizationAlreadyExistsException.class)
                .hasMessage("Organization already exists for this tenant");

        // save should NEVER have been called
        verify(organizationRepository, never()).save(any(Organization.class));
    }

    /**
     * TEST 3 — updateOrganization not found (wrong id or wrong tenant) throws.
     */
    @Test
    @DisplayName("updateOrganization: not found -> EntityNotFoundException")
    void updateOrganization_notFound_throwsException() {
        UUID missingId = UUID.fromString("66666666-6666-6666-6666-666666666666");
        UpdateOrganizationRequest update = new UpdateOrganizationRequest("New Name", "x");

        when(organizationRepository.findByTenantIdAndId(tenantId, missingId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> organizationService.updateOrganization(tenantId, missingId, update))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("Organization not found with id")
                .hasMessageContaining(missingId.toString());

        verify(organizationRepository, never()).existsByTenantIdAndName(any(UUID.class), anyString());
        verify(organizationRepository, never()).save(any(Organization.class));
    }

    /**
     * TEST 4 — activateOrganization success.
     */
    @Test
    @DisplayName("activateOrganization: success - sets status to ACTIVE")
    void activateOrganization_success_setsActive() {
        UUID orgId = savedOrganization.getId();
        // Start from INACTIVE to prove the transition
        savedOrganization.setStatus(OrganizationStatus.INACTIVE);

        when(organizationRepository.findByTenantIdAndId(tenantId, orgId))
                .thenReturn(Optional.of(savedOrganization));
        when(organizationRepository.save(savedOrganization)).thenReturn(savedOrganization);
        when(organizationMapper.toResponse(savedOrganization)).thenReturn(
                new OrganizationResponse(orgId, tenantId, "Acme Riyadh Branch",
                        "Main Riyadh operations", OrganizationStatus.ACTIVE, Instant.now(), Instant.now()));

        OrganizationResponse result = organizationService.activateOrganization(tenantId, orgId);

        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(OrganizationStatus.ACTIVE);
        // Verify the entity was actually mutated
        assertThat(savedOrganization.getStatus()).isEqualTo(OrganizationStatus.ACTIVE);
        verify(organizationRepository, times(1)).save(savedOrganization);
    }

    /**
     * TEST 5 — deactivateOrganization success.
     */
    @Test
    @DisplayName("deactivateOrganization: success - sets status to INACTIVE")
    void deactivateOrganization_success_setsInactive() {
        UUID orgId = savedOrganization.getId();
        // Start from ACTIVE
        savedOrganization.setStatus(OrganizationStatus.ACTIVE);

        when(organizationRepository.findByTenantIdAndId(tenantId, orgId))
                .thenReturn(Optional.of(savedOrganization));
        when(organizationRepository.save(savedOrganization)).thenReturn(savedOrganization);
        when(organizationMapper.toResponse(savedOrganization)).thenReturn(
                new OrganizationResponse(orgId, tenantId, "Acme Riyadh Branch",
                        "Main Riyadh operations", OrganizationStatus.INACTIVE, Instant.now(), Instant.now()));

        OrganizationResponse result = organizationService.deactivateOrganization(tenantId, orgId);

        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(OrganizationStatus.INACTIVE);
        assertThat(savedOrganization.getStatus()).isEqualTo(OrganizationStatus.INACTIVE);
        verify(organizationRepository, times(1)).save(savedOrganization);
    }

    /**
     * TEST 6 — activateOrganization not found throws.
     */
    @Test
    @DisplayName("activateOrganization: not found -> EntityNotFoundException")
    void activateOrganization_notFound_throwsException() {
        UUID missingId = UUID.fromString("77777777-7777-7777-7777-777777777777");

        when(organizationRepository.findByTenantIdAndId(tenantId, missingId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> organizationService.activateOrganization(tenantId, missingId))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("Organization not found with id")
                .hasMessageContaining(missingId.toString());

        verify(organizationRepository, never()).save(any(Organization.class));
    }

    // ============================================================
    // EXEC-PROMPT-010 — archiveOrganization tests
    // ============================================================

    /**
     * TEST 7 — archiveOrganization success.
     *
     * <p>Archiving an ACTIVE (or INACTIVE) organization sets status to ARCHIVED
     * and persists via save().</p>
     */
    @Test
    @DisplayName("archiveOrganization: success - sets status to ARCHIVED")
    void archiveOrganization_success_setsArchived() {
        UUID orgId = savedOrganization.getId();
        // Start from ACTIVE
        savedOrganization.setStatus(OrganizationStatus.ACTIVE);

        when(organizationRepository.findByTenantIdAndId(tenantId, orgId))
                .thenReturn(Optional.of(savedOrganization));
        when(organizationRepository.save(savedOrganization)).thenReturn(savedOrganization);
        when(organizationMapper.toResponse(savedOrganization)).thenReturn(
                new OrganizationResponse(orgId, tenantId, "Acme Riyadh Branch",
                        "Main Riyadh operations", OrganizationStatus.ARCHIVED, Instant.now(), Instant.now()));

        OrganizationResponse result = organizationService.archiveOrganization(tenantId, orgId);

        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(OrganizationStatus.ARCHIVED);
        // Verify the entity was mutated
        assertThat(savedOrganization.getStatus()).isEqualTo(OrganizationStatus.ARCHIVED);
        verify(organizationRepository, times(1)).save(savedOrganization);
    }

    /**
     * TEST 8 — archiveOrganization already archived (idempotency).
     *
     * <p>If the organization is already ARCHIVED, the service returns the
     * current response WITHOUT calling save() and WITHOUT throwing.</p>
     */
    @Test
    @DisplayName("archiveOrganization: already archived -> returns response without re-saving")
    void archiveOrganization_alreadyArchived_returnsResponseWithoutSave() {
        UUID orgId = savedOrganization.getId();
        // Start from ARCHIVED
        savedOrganization.setStatus(OrganizationStatus.ARCHIVED);

        when(organizationRepository.findByTenantIdAndId(tenantId, orgId))
                .thenReturn(Optional.of(savedOrganization));
        // Note: NO stub for save() — it should never be called
        when(organizationMapper.toResponse(savedOrganization)).thenReturn(
                new OrganizationResponse(orgId, tenantId, "Acme Riyadh Branch",
                        "Main Riyadh operations", OrganizationStatus.ARCHIVED, Instant.now(), Instant.now()));

        OrganizationResponse result = organizationService.archiveOrganization(tenantId, orgId);

        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(OrganizationStatus.ARCHIVED);
        // Verify save() was NEVER called (idempotency guard worked)
        verify(organizationRepository, never()).save(any(Organization.class));
        verify(organizationMapper, times(1)).toResponse(savedOrganization);
    }

    /**
     * TEST 9 — archiveOrganization not found throws.
     */
    @Test
    @DisplayName("archiveOrganization: not found -> EntityNotFoundException")
    void archiveOrganization_notFound_throwsException() {
        UUID missingId = UUID.fromString("88888888-8888-8888-8888-888888888888");

        when(organizationRepository.findByTenantIdAndId(tenantId, missingId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> organizationService.archiveOrganization(tenantId, missingId))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("Organization not found with id")
                .hasMessageContaining(missingId.toString());

        verify(organizationRepository, never()).save(any(Organization.class));
    }
}
