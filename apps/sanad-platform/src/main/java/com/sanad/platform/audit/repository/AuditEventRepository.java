package com.sanad.platform.audit.repository;

import com.sanad.platform.audit.domain.AuditEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Stage 05 §5 — Repository for {@link AuditEvent}.
 *
 * <p>Every query method is tenant-scoped. Declared query methods are
 * explicitly transactional so {@code TenantAwareJpaTransactionManager}
 * can bind {@code app.current_tenant_id} before PostgreSQL evaluates RLS.</p>
 */
@Repository
public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID> {

    @Transactional(readOnly = true)
    @Query("SELECT a FROM AuditEvent a WHERE a.tenantId = :tenantId AND a.id = :id")
    Optional<AuditEvent> findByTenantIdAndId(@Param("tenantId") UUID tenantId,
                                               @Param("id") UUID id);

    @Transactional(readOnly = true)
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

    @Transactional(readOnly = true)
    @Query(value = "SELECT * FROM audit_events WHERE tenant_id = :tenantId "
            + "ORDER BY occurred_at DESC, id DESC LIMIT 1",
            nativeQuery = true)
    Optional<AuditEvent> findLatestByTenantId(@Param("tenantId") UUID tenantId);

    @Transactional(readOnly = true)
    @Query(value = "SELECT * FROM audit_events WHERE tenant_id = :tenantId "
            + "ORDER BY sequence_number ASC",
            nativeQuery = true)
    List<AuditEvent> findAllByTenantIdOrderedForVerification(@Param("tenantId") UUID tenantId);
}
