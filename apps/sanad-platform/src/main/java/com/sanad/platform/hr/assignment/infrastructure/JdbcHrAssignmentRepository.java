package com.sanad.platform.hr.assignment.infrastructure;

import com.sanad.platform.hr.assignment.domain.HrAssignment;

import javax.sql.DataSource;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * JDBC implementation of HR Assignment repository. Every operation sets
 * tenant context on the same connection used for the query (FORCE RLS).
 *
 * <p>Task 4 RED skeleton — methods throw UnsupportedOperationException.
 * GREEN replaces with real JDBC.</p>
 */
public final class JdbcHrAssignmentRepository {

    private final DataSource dataSource;

    public JdbcHrAssignmentRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void saveAssignment(HrAssignment assignment) {
        throw new UnsupportedOperationException("JdbcHrAssignmentRepository.saveAssignment — Task 4 RED skeleton");
    }

    public Optional<HrAssignment> findAssignmentById(UUID tenantId, UUID assignmentId) {
        throw new UnsupportedOperationException("JdbcHrAssignmentRepository.findAssignmentById — Task 4 RED skeleton");
    }

    public List<HrAssignment> assignmentsForEmployment(UUID tenantId, UUID employmentId) {
        throw new UnsupportedOperationException("JdbcHrAssignmentRepository.assignmentsForEmployment — Task 4 RED skeleton");
    }

    /**
     * Check if setting reportsToAssignmentId as manager of assignmentId
     * creates a reporting cycle during the given effective period.
     */
    public boolean createsReportingCycle(UUID tenantId, UUID assignmentId,
                                           UUID reportsToAssignmentId,
                                           java.time.LocalDate effectiveFrom,
                                           java.time.LocalDate effectiveTo) {
        throw new UnsupportedOperationException("JdbcHrAssignmentRepository.createsReportingCycle — Task 4 RED skeleton");
    }
}
