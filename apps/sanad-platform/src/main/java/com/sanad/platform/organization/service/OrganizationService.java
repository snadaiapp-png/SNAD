package com.sanad.platform.organization.service;

import com.sanad.platform.audit.domain.AuditOutcome;
import com.sanad.platform.audit.service.AuditContext;
import com.sanad.platform.audit.service.AuditService;
import com.sanad.platform.organization.domain.Organization;
import com.sanad.platform.organization.domain.OrganizationStatus;
import com.sanad.platform.organization.dto.CreateOrganizationRequest;
import com.sanad.platform.organization.dto.OrganizationResponse;
import com.sanad.platform.organization.dto.UpdateOrganizationRequest;
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
    private final AuditService auditService;

    /**
     * Constructor-based dependency injection (no field injection,
     * no Lombok). All collaborators are required and final.
     */
    public OrganizationService(TenantRepository tenantRepository,
                                OrganizationRepository organizationRepository,
                                OrganizationMapper organizationMapper,
                                AuditService auditService) {
        this.tenantRepository = tenantRepository;
        this.organizationRepository = organizationRepository;
        this.organizationMapper = organizationMapper;
        this.auditService = auditService;
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
    public OrganizationResponse createOrganization(UUID tenantId, CreateOrganizationRequest request) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(request, "CreateOrganizationRequest must not be null");
        Objects.requireNonNull(request.getName(), "name must not be null");

        // --- Business Rule 1: Verify the parent Tenant exists ---
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Tenant not found with id: " + tenantId));

        // --- Business Rule 2: Verify organization name uniqueness within the tenant ---
        if (organizationRepository.existsByTenantIdAndName(
                tenantId, request.getName())) {
            throw new OrganizationAlreadyExistsException(
                    tenantId, request.getName());
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

        // Stage 05 §9: Audit the successful organization creation.
        // The audit event is recorded in the SAME transaction — if
        // anything fails after this point, both the organization and
        // the audit event roll back (no false success audit).
        auditService.record(AuditContext.builder(
                        "ORGANIZATION.CREATE", "Organization", "CREATE")
                .resourceId(saved.getId().toString())
                .outcome(AuditOutcome.SUCCESS)
                .httpStatus(201)
                .afterState("{\"name\":\"" + saved.getName() + "\",\"status\":\"ACTIVE\"}")
                .build());

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

    /**
     * Stage 03A — Paginated, tenant-scoped organization query.
     */
    @Transactional(readOnly = true, propagation = Propagation.SUPPORTS)
    public org.springframework.data.domain.Page<OrganizationResponse> listOrganizations(
            UUID tenantId, org.springframework.data.domain.Pageable pageable) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(pageable, "pageable must not be null");
        return organizationRepository.findByTenantId(tenantId, pageable)
                .map(organizationMapper::toResponse);
    }

    /**
     * Use Case: Update an Organization's mutable fields (name + description).
     *
     * <p>The tenant relationship and status are NOT changed by this operation.
     * If the new name conflicts with another Organization under the same
     * tenant, {@link OrganizationAlreadyExistsException} is thrown. The
     * organization's own current name is NOT considered a duplicate
     * (so re-saving the same name is allowed).</p>
     *
     * @param tenantId       the tenant scope
     * @param organizationId the organization to update
     * @param request        the new name + description
     * @return the updated Organization as a response DTO
     * @throws EntityNotFoundException              if the (tenantId, organizationId) pair does not exist
     * @throws OrganizationAlreadyExistsException   if another organization under the same tenant
     *                                              already uses the new name
     */
    @Transactional
    public OrganizationResponse updateOrganization(UUID tenantId, UUID organizationId,
                                                    UpdateOrganizationRequest request) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(organizationId, "organizationId must not be null");
        Objects.requireNonNull(request, "UpdateOrganizationRequest must not be null");

        Organization organization = organizationRepository
                .findByTenantIdAndId(tenantId, organizationId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Organization not found with id: " + organizationId
                                + " for tenant: " + tenantId));

        // Duplicate-name check: skip if the new name equals the current name
        // (so re-submitting the same name is a no-op for uniqueness purposes).
        String newName = request.getName();
        if (!newName.equals(organization.getName())
                && organizationRepository.existsByTenantIdAndName(tenantId, newName)) {
            throw new OrganizationAlreadyExistsException(tenantId, newName);
        }

        organization.setName(newName);
        organization.setDescription(request.getDescription());

        Organization saved = organizationRepository.save(organization);
        return organizationMapper.toResponse(saved);
    }

    /**
     * Use Case: Activate an Organization (set status = ACTIVE).
     */
    @Transactional
    public OrganizationResponse activateOrganization(UUID tenantId, UUID organizationId) {
        return setStatus(tenantId, organizationId, OrganizationStatus.ACTIVE);
    }

    /**
     * Use Case: Deactivate an Organization (set status = INACTIVE).
     */
    @Transactional
    public OrganizationResponse deactivateOrganization(UUID tenantId, UUID organizationId) {
        return setStatus(tenantId, organizationId, OrganizationStatus.INACTIVE);
    }

    /**
     * Use Case: Archive an Organization (soft delete via status = ARCHIVED).
     *
     * <p>This is the SANAD platform's soft-delete policy: instead of physically
     * removing a row from the database (which would break referential integrity
     * with future modules like ERP, CRM, HRM, Accounting, and Commerce), the
     * Organization is marked as {@link OrganizationStatus#ARCHIVED ARCHIVED}.</p>
     *
     * <p>The row remains in the database for audit, compliance, and historical
     * reporting. Future query use cases may filter out ARCHIVED organizations
     * by default.</p>
     *
     * <h3>Idempotency</h3>
     * <p>If the Organization is already ARCHIVED, this method is a no-op: it
     * returns the current {@link OrganizationResponse} without raising an error
     * and without issuing a redundant {@code save()}. This makes the endpoint
     * safe to retry.</p>
     *
     * @param tenantId       the tenant scope
     * @param organizationId the organization to archive
     * @return the archived Organization as a response DTO
     * @throws EntityNotFoundException if no Organization with the given id exists under the given tenant
     */
    @Transactional
    public OrganizationResponse archiveOrganization(UUID tenantId, UUID organizationId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(organizationId, "organizationId must not be null");

        Organization organization = organizationRepository
                .findByTenantIdAndId(tenantId, organizationId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Organization not found with id: " + organizationId
                                + " for tenant: " + tenantId));

        // Idempotency guard: if already ARCHIVED, return current state without re-saving
        if (organization.getStatus() == OrganizationStatus.ARCHIVED) {
            return organizationMapper.toResponse(organization);
        }

        organization.setStatus(OrganizationStatus.ARCHIVED);
        Organization saved = organizationRepository.save(organization);
        return organizationMapper.toResponse(saved);
    }

    /**
     * Internal helper: load an Organization within a tenant, set its status,
     * persist, and return the response. Used by both activate and deactivate
     * use cases to avoid duplication.
     */
    private OrganizationResponse setStatus(UUID tenantId, UUID organizationId,
                                            OrganizationStatus newStatus) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(organizationId, "organizationId must not be null");
        Objects.requireNonNull(newStatus, "newStatus must not be null");

        Organization organization = organizationRepository
                .findByTenantIdAndId(tenantId, organizationId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Organization not found with id: " + organizationId
                                + " for tenant: " + tenantId));

        organization.setStatus(newStatus);
        Organization saved = organizationRepository.save(organization);
        return organizationMapper.toResponse(saved);
    }
}
