package com.sanad.platform.organization.membership.repository;

import com.sanad.platform.organization.membership.domain.OrganizationMembership;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link OrganizationMembership}.
 *
 * <p>Every method is tenant-scoped. There is no {@code findAll()} that
 * crosses tenant boundaries.</p>
 */
@Repository
public interface OrganizationMembershipRepository
        extends JpaRepository<OrganizationMembership, UUID> {

    /**
     * List all memberships for a tenant (across all organizations).
     */
    @Query("SELECT m FROM OrganizationMembership m WHERE m.tenantId = :tenantId")
    List<OrganizationMembership> findByTenantId(@Param("tenantId") UUID tenantId);

    /**
     * List all memberships for a specific organization within a tenant.
     */
    @Query("SELECT m FROM OrganizationMembership m "
            + "WHERE m.tenantId = :tenantId AND m.organizationId = :organizationId")
    List<OrganizationMembership> findByTenantIdAndOrganizationId(
            @Param("tenantId") UUID tenantId,
            @Param("organizationId") UUID organizationId);

    /**
     * Fetch a single membership by id, scoped to (tenantId, organizationId).
     */
    @Query("SELECT m FROM OrganizationMembership m "
            + "WHERE m.tenantId = :tenantId "
            + "AND m.organizationId = :organizationId "
            + "AND m.id = :id")
    Optional<OrganizationMembership> findByTenantIdAndOrganizationIdAndId(
            @Param("tenantId") UUID tenantId,
            @Param("organizationId") UUID organizationId,
            @Param("id") UUID id);

    /**
     * Check if a membership with the given email already exists for the
     * (tenantId, organizationId) pair. Backs the unique constraint
     * {@code uk_org_memberships_tenant_org_email}.
     */
    @Query("SELECT CASE WHEN COUNT(m) > 0 THEN TRUE ELSE FALSE END "
            + "FROM OrganizationMembership m "
            + "WHERE m.tenantId = :tenantId "
            + "AND m.organizationId = :organizationId "
            + "AND m.email = :email")
    boolean existsByTenantIdAndOrganizationIdAndEmail(
            @Param("tenantId") UUID tenantId,
            @Param("organizationId") UUID organizationId,
            @Param("email") String email);
}
