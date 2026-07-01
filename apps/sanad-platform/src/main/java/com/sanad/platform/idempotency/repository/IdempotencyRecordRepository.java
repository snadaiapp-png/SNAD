package com.sanad.platform.idempotency.repository;

import com.sanad.platform.idempotency.domain.IdempotencyRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Stage 05 §14 — Repository for {@link IdempotencyRecord}.
 *
 * <p>Every query method is tenant-scoped. The unique constraint on
 * {@code (tenant_id, operation, route, idempotency_key)} is enforced
 * at the database level — see V22 migration.</p>
 */
@Repository
public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, UUID> {

    @Query("SELECT r FROM IdempotencyRecord r WHERE r.tenantId = :tenantId "
            + "AND r.operation = :operation "
            + "AND r.route = :route "
            + "AND r.idempotencyKey = :key")
    Optional<IdempotencyRecord> findByTenantOperationRouteKey(
            @Param("tenantId") UUID tenantId,
            @Param("operation") String operation,
            @Param("route") String route,
            @Param("key") String key);

    @Query("SELECT r FROM IdempotencyRecord r WHERE r.tenantId = :tenantId "
            + "AND r.status = 'PROCESSING' "
            + "AND r.expiresAt < :now")
    List<IdempotencyRecord> findExpiredProcessing(
            @Param("tenantId") UUID tenantId,
            @Param("now") Instant now);

    @Query("SELECT r FROM IdempotencyRecord r WHERE r.tenantId = :tenantId "
            + "AND r.expiresAt < :now")
    List<IdempotencyRecord> findAllExpired(
            @Param("tenantId") UUID tenantId,
            @Param("now") Instant now);
}
