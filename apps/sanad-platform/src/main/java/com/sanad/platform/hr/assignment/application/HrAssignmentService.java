package com.sanad.platform.hr.assignment.application;

import com.sanad.platform.hr.assignment.domain.AssignmentType;
import com.sanad.platform.hr.assignment.domain.HrAssignment;
import com.sanad.platform.hr.assignment.domain.OccupancyMode;
import com.sanad.platform.hr.assignment.infrastructure.JdbcHrAssignmentRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * HR Assignment service — application-layer facade for creating and
 * revising assignments with temporal, occupancy, and reporting-cycle
 * validation.
 *
 * <p>Task 4 RED skeleton — methods throw UnsupportedOperationException.
 * GREEN replaces with real implementation.</p>
 */
public final class HrAssignmentService {

    private final JdbcHrAssignmentRepository repository;

    public HrAssignmentService(JdbcHrAssignmentRepository repository) {
        this.repository = repository;
    }

    /**
     * Create a new assignment. Validates:
     * - allocation_percent > 0 and <= 100
     * - PRIMARY overlap (no two overlapping PRIMARY for same Employment)
     * - Position occupancy (no two OCCUPYING for same Position overlapping)
     * - Reporting cycle (if reports_to_assignment_id is set)
     *
     * All validation happens BEFORE any mutation (atomic).
     */
    public HrAssignment createAssignment(
            UUID tenantId, UUID employmentId, UUID organizationId,
            UUID orgUnitId, UUID positionId, UUID reportsToAssignmentId,
            UUID workLocationId,
            AssignmentType assignmentType, OccupancyMode occupancyMode,
            BigDecimal allocationPercent,
            LocalDate effectiveFrom, LocalDate effectiveTo) {
        throw new UnsupportedOperationException("HrAssignmentService.createAssignment — Task 4 RED skeleton");
    }

    /**
     * Revise an assignment: close the existing open assignment and
     * insert a new one. Validates cycle + occupancy before mutating.
     */
    public HrAssignment reviseAssignment(UUID tenantId, UUID assignmentId,
                                           LocalDate effectiveFrom,
                                           UUID newReportsToAssignmentId,
                                           UUID newPositionId,
                                           OccupancyMode newOccupancyMode,
                                           BigDecimal newAllocationPercent) {
        throw new UnsupportedOperationException("HrAssignmentService.reviseAssignment — Task 4 RED skeleton");
    }
}
