package com.sanad.platform.idempotency.service;

import com.sanad.platform.idempotency.domain.IdempotencyRecord;
import com.sanad.platform.idempotency.repository.IdempotencyRecordRepository;
import com.sanad.platform.security.tenant.TenantContextProvider;
import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Stage 05A.2.7 §7.2 — PostgreSQL reservation store using
 * TenantBoundNativeSqlExecutor for all native SQL.
 *
 * <p>Every INSERT/UPDATE/RETURNING goes through the executor which
 * explicitly sets and verifies app.current_tenant_id on the
 * transaction-bound connection before executing any SQL.</p>
 */
@Component
@Profile({"prod", "tenant-postgres-test"})
public class PostgresIdempotencyReservationStore implements IdempotencyReservationStore {

    private static final Logger log = LoggerFactory.getLogger(PostgresIdempotencyReservationStore.class);

    private final TenantBoundNativeSqlExecutor sqlExecutor;
    private final IdempotencyRecordRepository repository;

    public PostgresIdempotencyReservationStore(TenantBoundNativeSqlExecutor sqlExecutor,
                                                  IdempotencyRecordRepository repository) {
        this.sqlExecutor = sqlExecutor;
        this.repository = repository;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public Optional<LeaseGrant> atomicReserve(
            UUID tenantId, String idempotencyKey, String operation,
            String route, String resourceType, String requestFingerprint,
            Instant expiresAt, String leaseOwnerRequestId, Instant leaseExpiresAt) {

        UUID newId = UUID.randomUUID();

        LeaseGrant[] result = {null};

        sqlExecutor.execute(tenantId, connection -> {
            String sql = "INSERT INTO idempotency_records " +
                    "(id, tenant_id, idempotency_key, operation, route, resource_type, " +
                    " request_fingerprint, status, expires_at, created_at, updated_at, " +
                    " lease_owner_request_id, lease_expires_at, attempt_count, last_attempt_at, " +
                    " lease_version) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, 'PROCESSING', ?, NOW(), NOW(), " +
                    " ?, ?, 1, NOW(), 1) " +
                    "ON CONFLICT (tenant_id, operation, route, idempotency_key) DO NOTHING " +
                    "RETURNING id, tenant_id, lease_owner_request_id, lease_version, " +
                    "status, request_fingerprint, lease_expires_at";

            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setObject(1, newId);
                ps.setObject(2, tenantId);
                ps.setString(3, idempotencyKey);
                ps.setString(4, operation);
                ps.setString(5, route);
                ps.setString(6, resourceType);
                ps.setString(7, requestFingerprint);
                ps.setTimestamp(8, Timestamp.from(expiresAt));
                ps.setString(9, leaseOwnerRequestId);
                ps.setTimestamp(10, Timestamp.from(leaseExpiresAt));

                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        result[0] = new LeaseGrant(
                                rs.getObject("id", UUID.class),
                                rs.getObject("tenant_id", UUID.class),
                                rs.getString("lease_owner_request_id"),
                                rs.getLong("lease_version"),
                                rs.getString("status"),
                                rs.getString("request_fingerprint"),
                                rs.getTimestamp("lease_expires_at") != null
                                        ? rs.getTimestamp("lease_expires_at").toInstant() : null);
                    }
                }
            }
            return null; // void work
        });

        return Optional.ofNullable(result[0]);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public Optional<LeaseGrant> atomicTakeoverLease(
            UUID recordId, UUID tenantId,
            String newOwnerRequestId, Instant newLeaseExpiresAt) {

        LeaseGrant[] result = {null};

        sqlExecutor.execute(tenantId, connection -> {
            String sql = "UPDATE idempotency_records " +
                    "SET status = 'PROCESSING', " +
                    "    lease_owner_request_id = ?, " +
                    "    lease_expires_at = ?, " +
                    "    lease_version = lease_version + 1, " +
                    "    attempt_count = attempt_count + 1, " +
                    "    last_attempt_at = NOW(), " +
                    "    updated_at = NOW() " +
                    "WHERE id = ? " +
                    "AND tenant_id = ? " +
                    "AND (status = 'FAILED_RETRYABLE' OR lease_expires_at < NOW()) " +
                    "RETURNING id, tenant_id, lease_owner_request_id, lease_version, " +
                    "status, request_fingerprint, lease_expires_at";

            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, newOwnerRequestId);
                ps.setTimestamp(2, Timestamp.from(newLeaseExpiresAt));
                ps.setObject(3, recordId);
                ps.setObject(4, tenantId);

                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        result[0] = new LeaseGrant(
                                rs.getObject("id", UUID.class),
                                rs.getObject("tenant_id", UUID.class),
                                rs.getString("lease_owner_request_id"),
                                rs.getLong("lease_version"),
                                rs.getString("status"),
                                rs.getString("request_fingerprint"),
                                rs.getTimestamp("lease_expires_at") != null
                                        ? rs.getTimestamp("lease_expires_at").toInstant() : null);
                    }
                }
            }
            return null;
        });

        return Optional.ofNullable(result[0]);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public void atomicComplete(
            UUID recordId, UUID tenantId, String leaseOwnerRequestId, long leaseVersion,
            int responseStatus, String responseHeaders, String responseBody) {

        int[] updated = {0};

        sqlExecutor.execute(tenantId, connection -> {
            String sql = "UPDATE idempotency_records " +
                    "SET status = 'COMPLETED', " +
                    "    response_status = ?, " +
                    "    response_headers = ?, " +
                    "    response_body = ?, " +
                    "    completed_at = NOW(), " +
                    "    updated_at = NOW() " +
                    "WHERE id = ? " +
                    "AND tenant_id = ? " +
                    "AND status = 'PROCESSING' " +
                    "AND lease_owner_request_id = ? " +
                    "AND lease_version = ?";

            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setInt(1, responseStatus);
                ps.setString(2, responseHeaders);
                ps.setString(3, responseBody);
                ps.setObject(4, recordId);
                ps.setObject(5, tenantId);
                ps.setString(6, leaseOwnerRequestId);
                ps.setLong(7, leaseVersion);
                updated[0] = ps.executeUpdate();
            }
            return null;
        });

        if (updated[0] == 0) {
            throw new StaleIdempotencyLeaseException(recordId, leaseVersion, -1);
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public void atomicFail(
            UUID recordId, UUID tenantId, String leaseOwnerRequestId, long leaseVersion,
            String errorCode, String errorDetail, boolean retryable) {

        String newStatus = retryable ? "FAILED_RETRYABLE" : "FAILED_FINAL";
        int[] updated = {0};

        sqlExecutor.execute(tenantId, connection -> {
            String sql = "UPDATE idempotency_records " +
                    "SET status = ?, " +
                    "    error_code = ?, " +
                    "    error_detail = ?, " +
                    "    completed_at = NOW(), " +
                    "    updated_at = NOW() " +
                    "WHERE id = ? " +
                    "AND tenant_id = ? " +
                    "AND lease_owner_request_id = ? " +
                    "AND lease_version = ?";

            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, newStatus);
                ps.setString(2, errorCode);
                ps.setString(3, errorDetail);
                ps.setObject(4, recordId);
                ps.setObject(5, tenantId);
                ps.setString(6, leaseOwnerRequestId);
                ps.setLong(7, leaseVersion);
                updated[0] = ps.executeUpdate();
            }
            return null;
        });

        if (updated[0] == 0) {
            throw new StaleIdempotencyLeaseException(recordId, leaseVersion, -1);
        }
    }

    @Override
    public Optional<IdempotencyRecord> findByTenantOperationRouteKey(
            UUID tenantId, String operation, String route, String key) {
        return repository.findByTenantOperationRouteKey(tenantId, operation, route, key);
    }
}
