package com.sanad.platform.tenant.repository;

import com.sanad.platform.tenant.domain.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link Tenant} aggregates.
 *
 * <p>Stage 0 exposes only the two query methods strictly required by the
 * upcoming application service layer. Custom queries will be added in
 * later stages as needed.</p>
 *
 * <p>Inherits standard CRUD operations from {@link JpaRepository}:
 * save, findById, findAll, deleteById, count, etc.</p>
 */
@Repository
public interface TenantRepository extends JpaRepository<Tenant, UUID> {

    /**
     * Look up a tenant by its URL subdomain.
     *
     * @param subdomain the case-sensitive subdomain (typically lowercase)
     * @return the matching tenant, or empty if none exists
     */
    Optional<Tenant> findBySubdomain(String subdomain);

    /**
     * Check whether a tenant already exists for the given subdomain.
     * Cheaper than {@link #findBySubdomain(String)} because it does not
     * hydrate the entity.
     *
     * @param subdomain the subdomain to check
     * @return true if a tenant with this subdomain exists
     */
    boolean existsBySubdomain(String subdomain);
}
