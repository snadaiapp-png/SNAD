package com.sanad.platform.hr.integration;

import com.sanad.platform.hr.audit.HrAuditRecord;
import com.sanad.platform.hr.audit.HrAuditService;
import com.sanad.platform.hr.compliance.application.ComplianceAuditPort;
import com.sanad.platform.hr.compliance.domain.ComplianceOverrideAuditEntry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;

/**
 * WS4 durable adapter for the WS3-facing {@link ComplianceAuditPort}
 * (WS4 Task 4).
 *
 * <p>Translates override lifecycle audit entries into immutable
 * {@code hr_audit_ledger} facts (+ separate {@code hr_audit_delivery}
 * state) inside the caller's current transaction. Fails closed when no
 * transaction is active — override evidence can never commit independently.
 * Dependency direction is preserved: WS3 compliance depends on the port,
 * WS4 integration supplies this adapter (no WS3 -> WS4 infrastructure
 * import).</p>
 */
@Service
public class Ws4ComplianceAuditAdapter implements ComplianceAuditPort {

    private final DataSource dataSource;
    private final HrAuditService auditService;

    @Autowired
    public Ws4ComplianceAuditAdapter(DataSource dataSource, HrAuditService auditService) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.auditService = Objects.requireNonNull(auditService, "auditService");
    }

    @Override
    public void recordOverrideAction(ComplianceOverrideAuditEntry entry) {
        Objects.requireNonNull(entry, "entry");
        Connection connection = requireTransactionalConnection();
        HrAuditRecord record = new HrAuditRecord(
                entry.tenantId(),
                entry.actorUserId(),
                "HRM.COMPLIANCE_OVERRIDE." + entry.action(),
                "COMPLIANCE_OVERRIDE_REQUEST",
                entry.requestId(),
                null,
                null,
                "OPERATIONAL",
                entry.reasonCode(),
                null,
                null,
                "FAILURE".equalsIgnoreCase(entry.result()) ? "FAILURE" : "SUCCESS",
                null,
                entry.requestId(),
                null);
        auditService.appendMutationAudit(connection, record);
    }

    private Connection requireTransactionalConnection() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException(
                    "HRM_AUDIT_NOT_TRANSACTIONAL: override evidence must never commit outside the business transaction");
        }
        try {
            Connection connection = DataSourceUtils.getConnection(dataSource);
            if (connection.getAutoCommit()) {
                throw new IllegalStateException(
                        "HRM_AUDIT_NOT_TRANSACTIONAL: connection is in auto-commit; refusing to append evidence");
            }
            return connection;
        } catch (SQLException e) {
            throw new IllegalStateException("HRM_AUDIT_NOT_TRANSACTIONAL: unable to verify transaction state", e);
        }
    }
}
