package com.sanad.platform.organization.service;

import com.sanad.platform.organization.domain.Organization;
import com.sanad.platform.organization.domain.OrganizationStatus;
import com.sanad.platform.organization.dto.CreateOrganizationRequest;
import com.sanad.platform.organization.dto.OrganizationResponse;
import com.sanad.platform.organization.exception.OrganizationAlreadyExistsException;
import com.sanad.platform.organization.mapper.OrganizationMapper;
import com.sanad.platform.organization.repository.OrganizationRepository;
import com.sanad.platform.tenant.domain.Tenant;
import com.sanad.platform.tenant.repository.TenantRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Application service for the Organization aggregate.
 *
 * <p>This is the entry point for all use cases involving Organizations.
 * It orchestrates the {@link OrganizationRepository} and
 * {@link TenantRepository} ports, enforces business invariants, and
 * returns transport-layer DTOs ({@link OrganizationResponse}) rather
 * than domain entities — keeping the domain model pure.</p>
 *
 * <h2>Use Cases (Stage 0)</h2>
 * <ul>
 *   <li>{@link #createOrganization(CreateOrganizationRequest)} — register a new
 *       Organization under an existing Tenant.</li>
 * </ul>
 *
 * <p>Future stages will add: activate, deactivate, archive, rename,
 * transfer-to-tenant, and query/list operations.</p>
 *
 * <h2>DDD Compliance</h2>
 * <p>Per the aggregate-consistency rule enforced by EXEC-PROMPT-005-FIX,
 * this service NEVER constructs an {@link Organization} from a bare
 * {@code UUID tenantId}. Instead it loads the parent {@link Tenant}
 * aggregate via {@code tenantRepository.findById(tenantId)} and then
 * passes the resolved entity to {@code new Organization(tenant, ...)}.
 * This guarantees that an Organization cannot exist without a valid
 * Tenant reference at the domain level.</p>
 */
@Service
public class OrganizationService {

    private final TenantRepository tenantRepository;
    private final OrganizationRepository organizationRepository;
    private final OrganizationMapper organizationMapper;

    /**
     * Constructor-based dependency injection (no field injection,
     * no Lombok). All collaborators are required and final.
     */
    public OrganizationService(TenantRepository tenantRepository,
                                OrganizationRepository organizationRepository,
                                OrganizationMapper organizationMapper) {
        this.tenantRepository = tenantRepository;
        this.organizationRepository = organizationRepository;
        this.organizationMapper = organizationMapper;
    }

    /**
     * Use Case: Create a new Organization under an existing Tenant.
     *
     * <h3>Business Rules</h3>
     * <ol>
     *   <li>The referenced Tenant MUST exist; otherwise
     *       {@link EntityNotFoundException} is thrown.</li>
     *   <li>The (tenantId, name) pair MUST be unique; otherwise
     *       {@link OrganizationAlreadyExistsException} is thrown.</li>
     *   <li>The new Organization is created with
     *       {@link OrganizationStatus#ACTIVE} as its initial status.</li>
     *   <li>Audit fields ({@code createdAt}, {@code updatedAt}) are
     *       populated automatically by JPA auditing.</li>
     * </ol>
     *
     * <h3>Transaction Management</h3>
     * <p>Annotated with {@link Transactional} so that the
     * exists-check + save pair executes atomically. If anything fails
     * between the two operations, the entire transaction rolls back,
     * preventing partial writes.</p>
     *
     * @param request the inbound create request (validated by the controller
     *                layer via {@code @Valid}; the service performs defensive
     *                null-checks as well)
     * @return the persisted Organization as an {@link OrganizationResponse}
     * @throws EntityNotFoundException              if the referenced Tenant does not exist
     * @throws OrganizationAlreadyExistsException   if an Organization with the same name
     *                                              already exists under the same Tenant
     */
    @Transactional
    public OrganizationResponse createOrganization(CreateOrganizationRequest request) {
        Objects.requireNonNull(request, "CreateOrganizationRequest must not be null");
        Objects.requireNonNull(request.getTenantId(), "tenantId must not be null");
        Objects.requireNonNull(request.getName(), "name must not be null");

        // --- Business Rule 1: Verify the parent Tenant exists ---
        Tenant tenant = tenantRepository.findById(request.getTenantId())
                .orElseThrow(() -> new EntityNotFoundException(
                        "Tenant not found with id: " + request.getTenantId()));

        // --- Business Rule 2: Verify organization name uniqueness within the tenant ---
        if (organizationRepository.existsByTenantIdAndName(
                request.getTenantId(), request.getName())) {
            throw new OrganizationAlreadyExistsException(
                    request.getTenantId(), request.getName());
        }

        // --- Business Rule 3 + 5: Construct the new Organization aggregate ---
        // Per EXEC-PROMPT-005-FIX, we pass the Tenant ENTITY (not the bare UUID)
        // to the Organization constructor, enforcing DDD aggregate consistency.
        Organization organization = new Organization(
                tenant,
                request.getName(),
                request.getDescription(),
                OrganizationStatus.ACTIVE
        );

        // --- Business Rule 7: Persist the new aggregate ---
        Organization saved = organizationRepository.save(organization);

        // --- Business Rule 8: Return the transport-layer DTO ---
        return organizationMapper.toResponse(saved);
    }

    /**
     * Use Case: Fetch a single Organization within a specific Tenant.
     *
     * <p>The lookup is tenant-scoped: passing a valid {@code organizationId}
     * that belongs to a different tenant will return empty (and therefore
     * throw {@link EntityNotFoundException}). This enforces data isolation
     * at the application layer on top of the repository's tenant-scoped
     * query methods.</p>
     *
     * <p>Read-only: runs in a {@link Propagation#SUPPORTS SUPPORTS}
     * transaction so it can participate in an existing transaction or run
     * without one, but never opens a write transaction.</p>
     *
     * @param tenantId       the tenant scope (must not be null)
     * @param organizationId the organization to fetch (must not be null)
     * @return the matching Organization as an {@link OrganizationResponse}
     * @throws EntityNotFoundException if no Organization with the given id
     *                                 exists under the given tenant
     */
    @Transactional(readOnly = true, propagation = Propagation.SUPPORTS)
    public OrganizationResponse getOrganization(UUID tenantId, UUID organizationId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(organizationId, "organizationId must not be null");

        return organizationRepository
                .findByTenantIdAndId(tenantId, organizationId)
                .map(organizationMapper::toResponse)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Organization not found with id: " + organizationId
                                + " for tenant: " + tenantId));
    }

    /**
     * Use Case: List all Organizations belonging to a specific Tenant.
     *
     * <p>Returns an empty list (not null) if the tenant has no organizations.
     * The lookup is tenant-scoped via
     * {@link OrganizationRepository#findByTenantId(UUID)}.</p>
     *
     * <p>Read-only: runs in a {@link Propagation#SUPPORTS SUPPORTS}
     * transaction.</p>
     *
     * @param tenantId the tenant scope (must not be null)
     * @return a list of {@link OrganizationResponse} (never null, possibly empty)
     */
    @Transactional(readOnly = true, propagation = Propagation.SUPPORTS)
    public List<OrganizationResponse> listOrganizations(UUID tenantId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");

        return organizationRepository.findByTenantId(tenantId).stream()
                .map(organizationMapper::toResponse)
                .toList();
    }
}
