package com.sanad.platform.audit.repository;

import com.sanad.platform.audit.domain.AuditChainHead;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;

/**
 * Stage 05A.1 §8 — Repository for {@link AuditChainHead}.
 *
 * <p>The {@link #findByTenantIdForUpdate} method acquires a pessimistic
 * write lock ({@code SELECT ... FOR UPDATE}) on the chain head row,
 * serializing concurrent audit writes within the same tenant.</p>
 */
@Repository
public interface AuditChainHeadRepository extends JpaRepository<AuditChainHead, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT h FROM AuditChainHead h WHERE h.tenantId = :tenantId")
    Optional<AuditChainHead> findByTenantIdForUpdate(@Param("tenantId") UUID tenantId);

    @Query("SELECT h FROM AuditChainHead h WHERE h.tenantId = :tenantId")
    Optional<AuditChainHead> findByTenantId(@Param("tenantId") UUID tenantId);
}
