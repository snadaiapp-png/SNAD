package com.sanad.platform.organization.repository;

import com.sanad.platform.organization.domain.Organization;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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
 *
 * <h2>Aggregate-Consistent Query Strategy</h2>
 *
 * <p>The {@link Organization} entity does NOT expose a bare
 * {@code tenantId} field; the tenant identity is accessible only
 * through the {@code @ManyToOne Tenant tenant} relationship (per DDD
 * aggregate consistency rules enforced by EXEC-PROMPT-005-FIX).</p>
 *
 * <p>To keep the public repository method signatures unchanged
 * ({@code findByTenantId(UUID)}, {@code findByTenantIdAndId(UUID, UUID)},
 * {@code existsByTenantIdAndName(UUID, String)}) while still
 * traversing the {@code tenant} relationship, each method is annotated
 * with an explicit JPQL {@link Query} that navigates
 * {@code o.tenant.id}. This preserves the DDD invariant at the
 * entity level without leaking it to callers of the repository.</p>
 */
@Repository
public interface OrganizationRepository extends JpaRepository<Organization, UUID> {

    /**
     * List all organizations belonging to a specific tenant.
     *
     * @param tenantId the tenant scope
     * @return list of organizations (empty if none)
     */
    @Query("SELECT o FROM Organization o WHERE o.tenant.id = :tenantId")
    List<Organization> findByTenantId(@Param("tenantId") UUID tenantId);

    /**
     * Stage 03A — Paginated, tenant-scoped query. The {@code Pageable}
     * carries sort + offset + limit; tenant isolation is enforced at the
     * database level via the WHERE clause.
     */
    @Query(
        value = "SELECT o FROM Organization o WHERE o.tenant.id = :tenantId",
        countQuery = "SELECT COUNT(o) FROM Organization o WHERE o.tenant.id = :tenantId")
    Page<Organization> findByTenantId(@Param("tenantId") UUID tenantId, Pageable pageable);

    /**
     * Look up a single organization within a tenant by ID.
     * Tenant-scoped so cross-tenant access by ID is impossible.
     *
     * @param tenantId the tenant scope
     * @param id       the organization ID
     * @return the matching organization, or empty if not found / not in this tenant
     */
    @Query("SELECT o FROM Organization o WHERE o.tenant.id = :tenantId AND o.id = :id")
    Optional<Organization> findByTenantIdAndId(@Param("tenantId") UUID tenantId,
                                               @Param("id") UUID id);

    /**
     * Check whether an organization with the given name already exists
     * within a specific tenant. Backs the unique constraint
     * {@code uk_organizations_tenant_name}.
     *
     * @param tenantId the tenant scope
     * @param name     the organization name (case-sensitive)
     * @return true if an organization with this name exists in this tenant
     */
    @Query("SELECT CASE WHEN COUNT(o) > 0 THEN TRUE ELSE FALSE END " +
           "FROM Organization o WHERE o.tenant.id = :tenantId AND o.name = :name")
    boolean existsByTenantIdAndName(@Param("tenantId") UUID tenantId,
                                    @Param("name") String name);
}
