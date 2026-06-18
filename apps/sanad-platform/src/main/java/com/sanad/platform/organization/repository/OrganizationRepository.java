package com.sanad.platform.organization.repository;

import com.sanad.platform.organization.domain.Organization;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link Organization} aggregates.
 *
 * <p>Stage 0 exposes the three query methods strictly required by the
 * upcoming application service layer. Custom queries will be added in
 * later stages as needed (e.g. paginated listing, search by
 * description, filtering by status).</p>
 *
 * <p>Inherits standard CRUD operations from {@link JpaRepository}:
 * save, findById, findAll, deleteById, count, etc.</p>
 */
@Repository
public interface OrganizationRepository extends JpaRepository<Organization, UUID> {

    /**
     * List all Organizations belonging to a given Tenant.
     *
     * @param tenantId the Tenant's UUID
     * @return list of Organizations (empty if none); never null
     */
    List<Organization> findByTenantId(UUID tenantId);

    /**
     * Look up a specific Organization within a Tenant by ID. Used for
     * tenant-scoped access where a caller must not be able to read
     * another Tenant's Organization by guessing its UUID.
     *
     * @param tenantId the Tenant's UUID
     * @param id       the Organization's UUID
     * @return the matching Organization, or empty if none exists
     */
    Optional<Organization> findByTenantIdAndId(UUID tenantId, UUID id);

    /**
     * Check whether an Organization with the given name already exists
     * under the given Tenant. Used to enforce the (tenantId, name)
     * uniqueness rule from the service layer before attempting an
     * insert (cheaper than catching a DataIntegrityViolationException).
     *
     * @param tenantId the Tenant's UUID
     * @param name     the Organization name to check
     * @return true if an Organization with this name exists under this Tenant
     */
    boolean existsByTenantIdAndName(UUID tenantId, String name);
}
