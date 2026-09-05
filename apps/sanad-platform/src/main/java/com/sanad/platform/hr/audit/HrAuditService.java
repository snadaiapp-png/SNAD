package com.sanad.platform.hr.audit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;
import java.util.UUID;

/**
 * HR audit application service (WS4 Task 4).
 *
 * <p>Appends immutable mutation evidence (ledger fact + delivery state) in
 * the SAME database transaction as the canonical mutation. Two entry
 * points:</p>
 * <ul>
 *   <li>{@link #appendMutationAudit(Connection, HrAuditRecord)} — explicit
 *       connection participation (used by the transactional evidence writer
 *       wired into the Employment/Assignment repositories).</li>
 *   <li>{@link #appendMutationAudit(HrAuditRecord)} — joins the caller's
 *       Spring-managed transaction via {@link DataSourceUtils}; FAILS CLOSED
 *       when no transaction is active or the connection is in auto-commit,
 *       so audit evidence can never commit independently of the business
 *       mutation (REQUIRES_NEW is prohibited for critical audit).</li>
 * </ul>
 */
@Service
public class HrAuditService {

    private final DataSource dataSource;
    private final JdbcHrAuditRepository repository;

    @Autowired
    public HrAuditService(DataSource dataSource, HrRedactionGuard redactionGuard, JdbcHrAuditRepository repository) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    public UUID appendMutationAudit(Connection connection, HrAuditRecord record) {
        Objects.requireNonNull(connection, "connection");
        Objects.requireNonNull(record, "record");
        try {
            UUID auditId = repository.insertLedgerRow(connection, record);
            repository.insertDeliveryRow(connection, auditId, record.tenantId());
            return auditId;
        } catch (SQLException e) {
            throw new IllegalStateException("HRM_AUDIT_APPEND_FAILED: " + e.getMessage(), e);
        }
    }

    public UUID appendMutationAudit(HrAuditRecord record) {
        requireTransactionalConnection();
        Connection connection = DataSourceUtils.getConnection(dataSource);
        return appendMutationAudit(connection, record);
    }

    private void requireTransactionalConnection() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException(
                    "HRM_AUDIT_NOT_TRANSACTIONAL: audit evidence must never commit outside the business transaction");
        }
        try {
            Connection connection = DataSourceUtils.getConnection(dataSource);
            if (connection.getAutoCommit()) {
                throw new IllegalStateException(
                        "HRM_AUDIT_NOT_TRANSACTIONAL: connection is in auto-commit; refusing to append audit evidence");
            }
        } catch (SQLException e) {
            throw new IllegalStateException("HRM_AUDIT_NOT_TRANSACTIONAL: unable to verify transaction state", e);
        }
    }
}
