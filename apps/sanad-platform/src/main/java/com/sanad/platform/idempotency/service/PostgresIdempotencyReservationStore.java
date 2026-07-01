package com.sanad.platform.idempotency.service;

import com.sanad.platform.idempotency.domain.IdempotencyRecord;
import com.sanad.platform.idempotency.domain.IdempotencyStatus;
import com.sanad.platform.idempotency.repository.IdempotencyRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Stage 05A.2.1 §5 — PostgreSQL-specific reservation store.
 *
 * <p>Uses native INSERT ... ON CONFLICT DO NOTHING RETURNING and
 * UPDATE ... RETURNING for atomic reservation and lease takeover.
 * Active under {@code prod} and {@code tenant-postgres-test} profiles.</p>
 */
@Component
@Profile({"prod", "tenant-postgres-test"})
public class PostgresIdempotencyReservationStore implements IdempotencyReservationStore {

    private static final Logger log = LoggerFactory.getLogger(PostgresIdempotencyReservationStore.class);

    private final NamedParameterJdbcTemplate jdbc;
    private final IdempotencyRecordRepository repository;

    public PostgresIdempotencyReservationStore(DataSource dataSource,
                                                  IdempotencyRecordRepository repository) {
        this.jdbc = new NamedParameterJdbcTemplate(dataSource);
        this.repository = repository;
    }

    @Override
    public Optional<UUID> atomicReserve(
            UUID tenantId, String idempotencyKey, String operation,
            String route, String resourceType, String requestFingerprint,
            Instant expiresAt, String leaseOwnerRequestId, Instant leaseExpiresAt) {

        UUID newId = UUID.randomUUID();
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", newId)
                .addValue("tenantId", tenantId)
                .addValue("key", idempotencyKey)
                .addValue("operation", operation)
                .addValue("route", route)
                .addValue("resourceType", resourceType)
                .addValue("fingerprint", requestFingerprint)
                .addValue("expiresAt", Timestamp.from(expiresAt))
                .addValue("leaseOwner", leaseOwnerRequestId)
                .addValue("leaseExpiresAt", Timestamp.from(leaseExpiresAt));

        List<UUID> ids = jdbc.queryForList(
                "INSERT INTO idempotency_records "
                + "(id, tenant_id, idempotency_key, operation, route, resource_type, "
                + " request_fingerprint, status, expires_at, created_at, updated_at, "
                + " lease_owner_request_id, lease_expires_at, attempt_count, last_attempt_at, "
                + " lease_version) "
                + "VALUES (:id, :tenantId, :key, :operation, :route, :resourceType, "
                + " :fingerprint, 'PROCESSING', :expiresAt, NOW(), NOW(), "
                + " :leaseOwner, :leaseExpiresAt, 1, NOW(), 1) "
                + "ON CONFLICT (tenant_id, operation, route, idempotency_key) DO NOTHING "
                + "RETURNING id",
                params, UUID.class);

        if (ids.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(ids.get(0));
    }

    @Override
    public Optional<IdempotencyRecord> atomicTakeoverLease(
            UUID recordId, String newOwnerRequestId, Instant newLeaseExpiresAt) {

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("recordId", recordId)
                .addValue("newOwner", newOwnerRequestId)
                .addValue("newLeaseExpiry", Timestamp.from(newLeaseExpiresAt));

        // Update with RETURNING — returns the updated row if the WHERE matched.
        List<Map<String, Object>> rows = jdbc.queryForList(
                "UPDATE idempotency_records "
                + "SET status = 'PROCESSING', "
                + "    lease_owner_request_id = :newOwner, "
                + "    lease_expires_at = :newLeaseExpiry, "
                + "    lease_version = lease_version + 1, "
                + "    attempt_count = attempt_count + 1, "
                + "    last_attempt_at = NOW(), "
                + "    updated_at = NOW() "
                + "WHERE id = :recordId "
                + "AND (status = 'FAILED_RETRYABLE' OR lease_expires_at < NOW()) "
                + "RETURNING id, tenant_id, idempotency_key, operation, route, "
                + "resource_type, request_fingerprint, status, response_status, "
                + "response_headers, response_body, locked_at, processing_started_at, "
                + "completed_at, expires_at, created_at, updated_at, owner_request_id, "
                + "error_code, error_detail, lease_owner_request_id, lease_expires_at, "
                + "attempt_count, last_attempt_at, lease_version",
                params);

        if (rows.isEmpty()) {
            return Optional.empty();
        }
        // Re-read via JPA to get a managed entity
        return repository.findById(recordId);
    }

    @Override
    public void atomicComplete(
            UUID recordId, String leaseOwnerRequestId, long leaseVersion,
            int responseStatus, String responseHeaders, String responseBody) {

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("recordId", recordId)
                .addValue("leaseOwner", leaseOwnerRequestId)
                .addValue("leaseVersion", leaseVersion)
                .addValue("responseStatus", responseStatus)
                .addValue("responseHeaders", responseHeaders)
                .addValue("responseBody", responseBody);

        List<UUID> updated = jdbc.queryForList(
                "UPDATE idempotency_records "
                + "SET status = 'COMPLETED', "
                + "    response_status = :responseStatus, "
                + "    response_headers = :responseHeaders, "
                + "    response_body = :responseBody, "
                + "    completed_at = NOW(), "
                + "    updated_at = NOW() "
                + "WHERE id = :recordId "
                + "AND status = 'PROCESSING' "
                + "AND lease_owner_request_id = :leaseOwner "
                + "AND lease_version = :leaseVersion "
                + "RETURNING id",
                params, UUID.class);

        if (updated.isEmpty()) {
            // Read the actual state to provide a useful error
            IdempotencyRecord rec = repository.findById(recordId).orElse(null);
            long actualVersion = rec != null && rec.getLeaseVersion() != null
                    ? rec.getLeaseVersion() : -1;
            throw new StaleIdempotencyLeaseException(recordId, leaseVersion, actualVersion);
        }
    }

    @Override
    public void atomicFail(
            UUID recordId, String leaseOwnerRequestId, long leaseVersion,
            String errorCode, String errorDetail, boolean retryable) {

        String newStatus = retryable ? "FAILED_RETRYABLE" : "FAILED_FINAL";
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("recordId", recordId)
                .addValue("leaseOwner", leaseOwnerRequestId)
                .addValue("leaseVersion", leaseVersion)
                .addValue("newStatus", newStatus)
                .addValue("errorCode", errorCode)
                .addValue("errorDetail", errorDetail);

        List<UUID> updated = jdbc.queryForList(
                "UPDATE idempotency_records "
                + "SET status = :newStatus, "
                + "    error_code = :errorCode, "
                + "    error_detail = :errorDetail, "
                + "    completed_at = NOW(), "
                + "    updated_at = NOW() "
                + "WHERE id = :recordId "
                + "AND lease_owner_request_id = :leaseOwner "
                + "AND lease_version = :leaseVersion "
                + "RETURNING id",
                params, UUID.class);

        if (updated.isEmpty()) {
            IdempotencyRecord rec = repository.findById(recordId).orElse(null);
            long actualVersion = rec != null && rec.getLeaseVersion() != null
                    ? rec.getLeaseVersion() : -1;
            throw new StaleIdempotencyLeaseException(recordId, leaseVersion, actualVersion);
        }
    }

    @Override
    public Optional<IdempotencyRecord> findByTenantOperationRouteKey(
            UUID tenantId, String operation, String route, String key) {
        return repository.findByTenantOperationRouteKey(tenantId, operation, route, key);
    }
}
