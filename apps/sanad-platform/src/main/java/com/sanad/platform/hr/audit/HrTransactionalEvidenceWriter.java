package com.sanad.platform.hr.audit;

import com.sanad.platform.integration.events.DomainEventEnvelope;

import java.sql.Connection;

/**
 * Transactional evidence writer contract (WS4 Task 4).
 *
 * <p>Implemented by HR-owned adapters and wired into the concrete
 * transactional mutation boundaries (JdbcEmploymentRepository,
 * JdbcHrAssignmentRepository). Implementations MUST write all evidence on
 * the supplied {@link Connection} — the same transaction as the mutation —
 * and MUST NOT open independent transactions (no REQUIRES_NEW). Any failure
 * propagates so the caller's transaction rolls back atomically.</p>
 */
public interface HrTransactionalEvidenceWriter {

    void writeEvidence(Connection connection, HrAuditRecord auditRecord, DomainEventEnvelope event);
}
