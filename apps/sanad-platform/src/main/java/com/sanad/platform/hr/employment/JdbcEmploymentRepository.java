package com.sanad.platform.hr.employment;

import javax.sql.DataSource;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * JDBC implementation of {@link EmploymentRepository}.
 *
 * <p>Every operation MUST establish {@code app.tenant_id} on the SAME
 * database connection/transaction used for the query — never a different
 * connection. This preserves FORCE RLS semantics and does not bypass
 * tenant isolation.</p>
 *
 * <p>Task 2 RED skeleton: methods throw UnsupportedOperationException.
 * GREEN replaces with real JDBC persistence against hr_employees +
 * hr_employment_status_periods.</p>
 */
public final class JdbcEmploymentRepository implements EmploymentRepository {

    private final DataSource dataSource;

    public JdbcEmploymentRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void saveEmployment(Employment employment) {
        throw new UnsupportedOperationException(
                "JdbcEmploymentRepository.saveEmployment — Task 2 RED skeleton, implement in GREEN");
    }

    @Override
    public Optional<Employment> findEmploymentById(UUID tenantId, UUID employmentId) {
        throw new UnsupportedOperationException(
                "JdbcEmploymentRepository.findEmploymentById — Task 2 RED skeleton, implement in GREEN");
    }

    @Override
    public int countNonTerminalEmploymentsForPersonInLegalEntity(UUID tenantId, UUID personId, UUID legalEntityId) {
        throw new UnsupportedOperationException(
                "JdbcEmploymentRepository.countNonTerminalEmploymentsForPersonInLegalEntity — Task 2 RED skeleton, implement in GREEN");
    }

    @Override
    public void saveStatusPeriod(EmploymentStatusPeriod period) {
        throw new UnsupportedOperationException(
                "JdbcEmploymentRepository.saveStatusPeriod — Task 2 RED skeleton, implement in GREEN");
    }

    @Override
    public List<EmploymentStatusPeriod> statusPeriods(UUID tenantId, UUID employmentId) {
        throw new UnsupportedOperationException(
                "JdbcEmploymentRepository.statusPeriods — Task 2 RED skeleton, implement in GREEN");
    }

    @Override
    public void updateCurrentStatusProjection(UUID tenantId, UUID employmentId,
                                                EmploymentStatus newStatus, long expectedVersion) {
        throw new UnsupportedOperationException(
                "JdbcEmploymentRepository.updateCurrentStatusProjection — Task 2 RED skeleton, implement in GREEN");
    }
}
