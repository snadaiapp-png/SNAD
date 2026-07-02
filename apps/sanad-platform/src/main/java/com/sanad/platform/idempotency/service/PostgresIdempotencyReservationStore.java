package com.sanad.platform.idempotency.service;

import com.sanad.platform.idempotency.domain.IdempotencyRecord;
import com.sanad.platform.idempotency.domain.IdempotencyStatus;
import com.sanad.platform.idempotency.repository.IdempotencyRecordRepository;
import jakarta.persistence.EntityManager;
import org.hibernate.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Stage 05A.2.6 §2 — PostgreSQL reservation store that executes native SQL
 * through the EntityManager's transaction-bound connection.
 *
 * <p>This ensures the INSERT/UPDATE runs on the SAME connection that
 * TenantAwareJpaTransactionManager has already bound to the transaction
 * (with app.current_tenant_id set via SET LOCAL). Using a separate
 * NamedParameterJdbcTemplate would grab a different connection from the
 * pool — one without the tenant setting — causing RLS violations.</p>
 */
@Component
@Profile({"prod", "tenant-postgres-test"})
public class PostgresIdempotencyReservationStore implements IdempotencyReservationStore {

    private static final Logger log = LoggerFactory.getLogger(PostgresIdempotencyReservationStore.class);

    private final EntityManager entityManager;
    private final IdempotencyRecordRepository repository;

    public PostgresIdempotencyReservationStore(EntityManager entityManager,
                                                  IdempotencyRecordRepository repository) {
        this.entityManager = entityManager;
        this.repository = repository;
    }

    @Override
    public Optional<LeaseGrant> atomicReserve(
            UUID tenantId, String idempotencyKey, String operation,
            String route, String resourceType, String requestFingerprint,
            Instant expiresAt, String leaseOwnerRequestId, Instant leaseExpiresAt) {

        UUID newId = UUID.randomUUID();
        Session session = entityManager.unwrap(Session.class);

        Boolean[] inserted = {Boolean.FALSE};
        session.doWork(connection -> {
            String sql = "INSERT INTO idempotency_records " +
                    "(id, tenant_id, idempotency_key, operation, route, resource_type, " +
                    " request_fingerprint, status, expires_at, created_at, updated_at, " +
                    " lease_owner_request_id, lease_expires_at, attempt_count, last_attempt_at, " +
                    " lease_version) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, 'PROCESSING', ?, NOW(), NOW(), " +
                    " ?, ?, 1, NOW(), 1) " +
                    "ON CONFLICT (tenant_id, operation, route, idempotency_key) DO NOTHING " +
                    "RETURNING id";

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
                    inserted[0] = rs.next();
                }
            }
        });

        if (Boolean.TRUE.equals(inserted[0])) {
            return Optional.of(new LeaseGrant(
                    newId, tenantId, leaseOwnerRequestId, 1L,
                    "PROCESSING", requestFingerprint, leaseExpiresAt));
        }
        return Optional.empty();
    }

    @Override
    public Optional<LeaseGrant> atomicTakeoverLease(
            UUID recordId, String newOwnerRequestId, Instant newLeaseExpiresAt) {

        Session session = entityManager.unwrap(Session.class);
        LeaseGrant[] result = {null};

        session.doWork(connection -> {
            String sql = "UPDATE idempotency_records " +
                    "SET status = 'PROCESSING', " +
                    "    lease_owner_request_id = ?, " +
                    "    lease_expires_at = ?, " +
                    "    lease_version = lease_version + 1, " +
                    "    attempt_count = attempt_count + 1, " +
                    "    last_attempt_at = NOW(), " +
                    "    updated_at = NOW() " +
                    "WHERE id = ? " +
                    "AND (status = 'FAILED_RETRYABLE' OR lease_expires_at < NOW()) " +
                    "RETURNING id, tenant_id, lease_owner_request_id, lease_version, " +
                    "status, request_fingerprint, lease_expires_at";

            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, newOwnerRequestId);
                ps.setTimestamp(2, Timestamp.from(newLeaseExpiresAt));
                ps.setObject(3, recordId);

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
        });

        return Optional.ofNullable(result[0]);
    }

    @Override
    public void atomicComplete(
            UUID recordId, UUID tenantId, String leaseOwnerRequestId, long leaseVersion,
            int responseStatus, String responseHeaders, String responseBody) {

        Session session = entityManager.unwrap(Session.class);
        int[] updated = {0};

        session.doWork(connection -> {
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
        });

        if (updated[0] == 0) {
            throw new StaleIdempotencyLeaseException(recordId, leaseVersion, -1);
        }
    }

    @Override
    public void atomicFail(
            UUID recordId, UUID tenantId, String leaseOwnerRequestId, long leaseVersion,
            String errorCode, String errorDetail, boolean retryable) {

        String newStatus = retryable ? "FAILED_RETRYABLE" : "FAILED_FINAL";
        Session session = entityManager.unwrap(Session.class);
        int[] updated = {0};

        session.doWork(connection -> {
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
