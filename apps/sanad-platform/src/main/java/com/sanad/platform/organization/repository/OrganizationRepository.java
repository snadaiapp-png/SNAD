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
 * <p>Each method is tenant-scoped: callers MUST pass the tenant ID
 * explicitly so that data isolation is enforced at the persistence
 * layer. There is no {@code findAll()} that crosses tenant boundaries
 * — to list organizations across tenants you need a privileged service
 * that uses a separate repository method (added in a future stage).</p>
 */
@Repository
public interface OrganizationRepository extends JpaRepository<Organization, UUID> {

    /**
     * List all organizations belonging to a specific tenant.
     *
     * @param tenantId the tenant scope
     * @return list of organizations (empty if none)
     */
    List<Organization> findByTenantId(UUID tenantId);

    /**
     * Look up a single organization within a tenant by ID.
     * Tenant-scoped so cross-tenant access by ID is impossible.
     *
     * @param tenantId the tenant scope
     * @param id       the organization ID
     * @return the matching organization, or empty if not found / not in this tenant
     */
    Optional<Organization> findByTenantIdAndId(UUID tenantId, UUID id);

    /**
     * Check whether an organization with the given name already exists
     * within a specific tenant. Backs the unique constraint
     * {@code uk_organizations_tenant_name}.
     *
     * @param tenantId the tenant scope
     * @param name     the organization name (case-sensitive)
     * @return true if an organization with this name exists in this tenant
     */
    boolean existsByTenantIdAndName(UUID tenantId, String name);
}
