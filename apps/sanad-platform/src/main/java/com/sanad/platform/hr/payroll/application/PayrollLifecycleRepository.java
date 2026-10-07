package com.sanad.platform.hr.payroll.application;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

/**
 * G4-T5 application persistence port for governed payroll lifecycle changes.
 *
 * <p>Application services depend on this contract only. JDBC/PostgreSQL
 * implementation details remain behind the infrastructure adapter.</p>
 */
public interface PayrollLifecycleRepository {

    Optional<RunState> loadForUpdate(
            Connection connection,
            UUID tenantId,
            UUID runId) throws SQLException;

    RunState transition(
            Connection connection,
            RunState current,
            PayrollLifecycle target,
            UUID actorUserId) throws SQLException;

    RunState recalculate(
            Connection connection,
            RunState current) throws SQLException;

    record RunState(
            UUID id,
            UUID tenantId,
            UUID legalEntityId,
            PayrollLifecycle status,
            long version) {
    }
}
