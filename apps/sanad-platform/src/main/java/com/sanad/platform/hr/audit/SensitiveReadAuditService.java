package com.sanad.platform.hr.audit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Sensitive-read audit service (WS4 Task 5) — restricted reads FAIL CLOSED.
 *
 * <p>Any read that returns restricted data (PII, COMPENSATION, protected
 * CONTRACT data) must append an audit ledger row BEFORE the restricted
 * response may be returned. If the audit append fails, {@link #recordOrThrow}
 * throws, so the caller can never complete the restricted read without
 * audit evidence.</p>
 *
 * <p>Evidence minimality: the read-audit row records identifiers,
 * classification, reason and correlation metadata ONLY. It never copies the
 * sensitive values themselves — {@code before_state}/{@code after_state} stay
 * {@code NULL} for read audits.</p>
 *
 * <p>Transaction semantics mirror {@link HrAuditService}: the canonical
 * variant joins the caller's active transaction via {@link DataSourceUtils}
 * and FAILS CLOSED when no transaction is active or the connection is in
 * auto-commit. {@code REQUIRES_NEW} is prohibited for critical audit — a
 * detached audit commit would let a failed read leave surviving evidence or,
 * worse, decouple evidence from the read outcome.</p>
 */
@Service
public class SensitiveReadAuditService {

    private final DataSource dataSource;
    private final HrRedactionGuard redactionGuard;
    private final JdbcHrAuditRepository repository;

    @Autowired
    public SensitiveReadAuditService(
            DataSource dataSource,
            HrRedactionGuard redactionGuard,
            JdbcHrAuditRepository repository) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.redactionGuard = Objects.requireNonNull(redactionGuard, "redactionGuard");
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    /**
     * Records a sensitive read in the caller's active transaction. Throws when
     * no transaction is active (fail closed) or when the audit append fails —
     * the restricted response must NOT be returned afterwards.
     */
    public void recordOrThrow(HrAuthenticatedContext actor, String action, String resourceType,
                              UUID resourceId, String classification, String reason) {
        requireTransactionalConnection();
        Connection connection = DataSourceUtils.getConnection(dataSource);
        recordOrThrow(connection, actor, action, resourceType, resourceId, classification, reason);
    }

    /**
     * Explicit connection-participation variant for infrastructure wiring and
     * tests. Returns the generated audit id so callers can correlate evidence.
     *
     * @throws IllegalStateException when the audit append fails (fail closed)
     */
    public UUID recordOrThrow(Connection connection, HrAuthenticatedContext actor, String action,
                              String resourceType, UUID resourceId, String classification, String reason) {
        Objects.requireNonNull(connection, "connection");
        Objects.requireNonNull(actor, "actor");
        requireText(action, "action");
        requireText(resourceType, "resourceType");
        requireText(classification, "classification");
        Objects.requireNonNull(resourceId, "resourceId");

        // Read audits record identifiers/classification/reason only. State
        // snapshots stay NULL — sensitive values must never be copied here.
        HrAuditRecord record = new HrAuditRecord(
                actor.tenantId(),
                actor.actorUserId(),
                action,
                resourceType,
                resourceId,
                null,
                null,
                classification,
                reason,
                null,
                null,
                "SUCCESS",
                actor.correlationId(),
                actor.requestId(),
                Instant.now());
        try {
            UUID auditId = repository.insertLedgerRow(connection, record);
            repository.insertDeliveryRow(connection, auditId, actor.tenantId());
            return auditId;
        } catch (SQLException e) {
            throw new IllegalStateException("HRM_SENSITIVE_READ_AUDIT_APPEND_FAILED: " + e.getMessage(), e);
        }
    }

    private void requireTransactionalConnection() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException(
                    "HRM_SENSITIVE_READ_AUDIT_NOT_TRANSACTIONAL: sensitive-read audit must join the caller's "
                            + "transaction; refusing to record without one (REQUIRES_NEW bypass is prohibited)");
        }
        try {
            Connection connection = DataSourceUtils.getConnection(dataSource);
            if (connection.getAutoCommit()) {
                throw new IllegalStateException(
                        "HRM_SENSITIVE_READ_AUDIT_NOT_TRANSACTIONAL: connection is in auto-commit; refusing to "
                                + "record sensitive-read audit outside a transaction");
            }
        } catch (SQLException e) {
            throw new IllegalStateException(
                    "HRM_SENSITIVE_READ_AUDIT_NOT_TRANSACTIONAL: unable to verify transaction state", e);
        }
    }

    private void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("HRM_SENSITIVE_READ_AUDIT_INVALID: " + field + " is required");
        }
    }
}
