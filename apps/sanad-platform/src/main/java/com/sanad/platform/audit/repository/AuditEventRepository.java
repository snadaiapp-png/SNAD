package com.sanad.platform.audit.repository;

import com.sanad.platform.audit.domain.AuditEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Stage 05 §5 — Repository for {@link AuditEvent}.
 *
 * <p>Every query method is tenant-scoped. There is no
 * {@code findAll()} — the runtime role can only see audit events
 * for the current tenant (enforced by RLS).</p>
 *
 * <p>INSERT is the only mutation method exposed. UPDATE and DELETE
 * are blocked by PostgreSQL triggers (V23 migration). The
 * {@link #save(Object)} method inherited from JpaRepository performs
 * INSERT for new entities; calling it on a detached entity with an
 * existing ID would trigger an UPDATE which the DB rejects.</p>
 */
@Repository
public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID> {

    @Query("SELECT a FROM AuditEvent a WHERE a.tenantId = :tenantId AND a.id = :id")
    Optional<AuditEvent> findByTenantIdAndId(@Param("tenantId") UUID tenantId,
                                              @Param("id") UUID id);

    @Query(
        value = "SELECT a FROM AuditEvent a WHERE a.tenantId = :tenantId "
                + "AND (:actorUserId IS NULL OR a.actorUserId = :actorUserId) "
                + "AND (:action IS NULL OR a.action = :action) "
                + "AND (:resourceType IS NULL OR a.resourceType = :resourceType) "
                + "AND (:resourceId IS NULL OR a.resourceId = :resourceId) "
                + "AND (:outcome IS NULL OR a.outcome = :outcome) "
                + "AND (:correlationId IS NULL OR a.correlationId = :correlationId) "
                + "AND (CAST(:from AS timestamp) IS NULL OR a.occurredAt >= :from) "
                + "AND (CAST(:to AS timestamp) IS NULL OR a.occurredAt < :to) "
                + "ORDER BY a.occurredAt DESC, a.id DESC",
        countQuery = "SELECT COUNT(a) FROM AuditEvent a WHERE a.tenantId = :tenantId "
                + "AND (:actorUserId IS NULL OR a.actorUserId = :actorUserId) "
                + "AND (:action IS NULL OR a.action = :action) "
                + "AND (:resourceType IS NULL OR a.resourceType = :resourceType) "
                + "AND (:resourceId IS NULL OR a.resourceId = :resourceId) "
                + "AND (:outcome IS NULL OR a.outcome = :outcome) "
                + "AND (:correlationId IS NULL OR a.correlationId = :correlationId) "
                + "AND (CAST(:from AS timestamp) IS NULL OR a.occurredAt >= :from) "
                + "AND (CAST(:to AS timestamp) IS NULL OR a.occurredAt < :to)")
    Page<AuditEvent> findFiltered(
            @Param("tenantId") UUID tenantId,
            @Param("actorUserId") UUID actorUserId,
            @Param("action") String action,
            @Param("resourceType") String resourceType,
            @Param("resourceId") String resourceId,
            @Param("outcome") com.sanad.platform.audit.domain.AuditOutcome outcome,
            @Param("correlationId") String correlationId,
            @Param("from") Instant from,
            @Param("to") Instant to,
            Pageable pageable);

    /**
     * Stage 05 §11 — Returns the most recent audit event for a tenant,
     * used to seed the hash chain's {@code previousHash} for the next
     * event.
     */
    @Query(value = "SELECT * FROM audit_events WHERE tenant_id = :tenantId "
            + "ORDER BY occurred_at DESC, id DESC LIMIT 1",
            nativeQuery = true)
    Optional<AuditEvent> findLatestByTenantId(@Param("tenantId") UUID tenantId);

    /**
     * Stage 05A.1 §9 — Returns all audit events for a tenant ordered by
     * sequence_number ASC for hash-chain verification. Used by
     * {@link com.sanad.platform.audit.service.AuditIntegrityVerificationService}.
     */
    @Query(value = "SELECT * FROM audit_events WHERE tenant_id = :tenantId "
            + "ORDER BY sequence_number ASC",
            nativeQuery = true)
    List<AuditEvent> findAllByTenantIdOrderedForVerification(@Param("tenantId") UUID tenantId);
}
