package com.sanad.platform.idempotency.repository;

import com.sanad.platform.idempotency.domain.IdempotencyRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Stage 05A.2 §13 — Repository for {@link IdempotencyRecord}.
 *
 * <p>Uses atomic PostgreSQL INSERT ... ON CONFLICT DO NOTHING RETURNING
 * for reservation, eliminating the save/flush/exception-recovery pattern.</p>
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

    /**
     * Stage 05A.2 §13 — Atomic reservation using PostgreSQL ON CONFLICT.
     * Returns the ID only if the INSERT succeeded. If the row already
     * exists (conflict), returns empty — caller must re-read in a new
     * transaction.
     */
    @Query(value = "INSERT INTO idempotency_records "
            + "(id, tenant_id, idempotency_key, operation, route, resource_type, "
            + "request_fingerprint, status, expires_at, created_at, updated_at, "
            + "lease_owner_request_id, lease_expires_at, attempt_count, last_attempt_at) "
            + "VALUES (:id, :tenantId, :key, :operation, :route, :resourceType, "
            + ":fingerprint, 'PROCESSING', :expiresAt, NOW(), NOW(), "
            + ":leaseOwner, :leaseExpiresAt, 1, NOW()) "
            + "ON CONFLICT (tenant_id, operation, route, idempotency_key) DO NOTHING "
            + "RETURNING id",
            nativeQuery = true)
    Optional<UUID> atomicReserve(
            @Param("id") UUID id,
            @Param("tenantId") UUID tenantId,
            @Param("key") String key,
            @Param("operation") String operation,
            @Param("route") String route,
            @Param("resourceType") String resourceType,
            @Param("fingerprint") String fingerprint,
            @Param("expiresAt") Instant expiresAt,
            @Param("leaseOwner") String leaseOwner,
            @Param("leaseExpiresAt") Instant leaseExpiresAt);

    /**
     * Stage 05A.2 §14 — Atomic lease takeover. Only succeeds if the
     * record is FAILED_RETRYABLE or the lease has expired.
     */
    @Modifying
    @Query(value = "UPDATE idempotency_records "
            + "SET status = 'PROCESSING', "
            + "    lease_owner_request_id = :newOwner, "
            + "    lease_expires_at = :newLeaseExpiry, "
            + "    attempt_count = attempt_count + 1, "
            + "    last_attempt_at = NOW(), "
            + "    updated_at = NOW() "
            + "WHERE id = :recordId "
            + "AND (status = 'FAILED_RETRYABLE' OR lease_expires_at < NOW()) "
            + "RETURNING id",
            nativeQuery = true)
    Optional<UUID> atomicTakeoverLease(
            @Param("recordId") UUID recordId,
            @Param("newOwner") String newOwner,
            @Param("newLeaseExpiry") Instant newLeaseExpiry);

    /**
     * Stage 05A.2 §11 — Locks the reservation row for update within
     * the business transaction.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM IdempotencyRecord r WHERE r.id = :id")
    Optional<IdempotencyRecord> findByIdForUpdate(@Param("id") UUID id);

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
