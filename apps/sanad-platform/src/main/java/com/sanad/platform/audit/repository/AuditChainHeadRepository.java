package com.sanad.platform.audit.repository;

import com.sanad.platform.audit.domain.AuditChainHead;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;

/**
 * Stage 05A.2 §7 — Repository for {@link AuditChainHead}.
 *
 * <p>Uses atomic INSERT ... ON CONFLICT DO NOTHING for initialization,
 * then SELECT ... FOR UPDATE for locking. This eliminates the
 * findForUpdate().orElseGet(save()) race condition.</p>
 */
@Repository
public interface AuditChainHeadRepository extends JpaRepository<AuditChainHead, UUID> {

    /**
     * Stage 05A.2 §7 — Atomically initializes the chain head for a tenant.
     * If the row already exists, does nothing. Safe to call concurrently.
     */
    @Modifying
    @Query(value = "INSERT INTO audit_chain_heads (tenant_id, head_sequence, head_hash, updated_at) "
            + "VALUES (:tenantId, 0, NULL, NOW()) "
            + "ON CONFLICT (tenant_id) DO NOTHING",
            nativeQuery = true)
    int atomicInit(@Param("tenantId") UUID tenantId);

    /**
     * Stage 05A.2 §7 — Locks the chain head row for update. Must be
     * called AFTER {@link #atomicInit} to ensure the row exists.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT h FROM AuditChainHead h WHERE h.tenantId = :tenantId")
    Optional<AuditChainHead> findByTenantIdForUpdate(@Param("tenantId") UUID tenantId);

    @Query("SELECT h FROM AuditChainHead h WHERE h.tenantId = :tenantId")
    Optional<AuditChainHead> findByTenantId(@Param("tenantId") UUID tenantId);
}
