package com.sanad.platform.hr.payroll.application;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * G4-T5 application persistence port for the governed payroll lifecycle.
 *
 * <p>The application layer depends only on this contract. JDBC/PostgreSQL
 * implementation details remain behind an adapter.</p>
 */
public interface PayrollLifecycleRepository {

    PayrollRunRow lockRun(Connection connection, UUID tenantId, UUID runId) throws SQLException;

    long updateRunStatus(
            Connection connection,
            UUID tenantId,
            UUID runId,
            String targetStatus,
            long expectedVersion,
            UUID actorUserId) throws SQLException;

    long incrementForRecalculation(
            Connection connection,
            UUID tenantId,
            UUID runId,
            long expectedVersion) throws SQLException;

    void projectItemStatus(
            Connection connection,
            UUID tenantId,
            UUID runId,
            String targetStatus) throws SQLException;

    IdempotencyAdmission admit(
            Connection connection,
            UUID tenantId,
            UUID principalId,
            String operationCode,
            String idempotencyKey,
            String fingerprint) throws SQLException;

    void completeIdempotency(
            Connection connection,
            UUID id,
            UUID operationReference,
            String responseBody) throws SQLException;

    record PayrollRunRow(
            UUID id,
            UUID tenantId,
            UUID legalEntityId,
            LocalDate periodStart,
            LocalDate periodEnd,
            String currencyCode,
            String status,
            long version,
            UUID createdBy,
            UUID reviewedBy,
            Instant reviewedAt,
            UUID approvedBy,
            Instant approvedAt,
            Instant exportedAt) {
    }

    record IdempotencyAdmission(
            UUID id,
            boolean replay,
            Integer responseStatus,
            String responseBody) {
        public static IdempotencyAdmission fresh(UUID id) {
            return new IdempotencyAdmission(id, false, null, null);
        }

        public static IdempotencyAdmission replay(UUID id, int status, String body) {
            return new IdempotencyAdmission(id, true, status, body);
        }
    }
}
