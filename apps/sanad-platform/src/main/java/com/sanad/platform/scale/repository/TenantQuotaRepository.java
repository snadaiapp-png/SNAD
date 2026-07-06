package com.sanad.platform.scale.repository;

import com.sanad.platform.scale.domain.TenantQuota;
import com.sanad.platform.scale.domain.TenantQuota.Dimension;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Stage 08 Sprint 1 — ST8-S1-002 Tenant Quota Repository.
 *
 * All queries MUST be tenant-scoped. No method may return quotas across tenants.
 */
@Repository
public interface TenantQuotaRepository extends JpaRepository<TenantQuota, Long> {

    Optional<TenantQuota> findByTenantIdAndDimension(String tenantId, Dimension dimension);

    List<TenantQuota> findByTenantId(String tenantId);

    List<TenantQuota> findByResetAtBefore(Instant cutoff);

    long countByTenantId(String tenantId);
}
