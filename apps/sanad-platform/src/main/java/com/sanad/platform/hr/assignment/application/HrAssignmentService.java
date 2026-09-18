package com.sanad.platform.hr.assignment.application;

import com.sanad.platform.hr.assignment.domain.AssignmentType;
import com.sanad.platform.hr.assignment.domain.HrAssignment;
import com.sanad.platform.hr.assignment.domain.OccupancyMode;
import com.sanad.platform.hr.assignment.infrastructure.JdbcHrAssignmentRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * HR Assignment service — delegates to JdbcHrAssignmentRepository for
 * atomic validate-before-mutate operations.
 */
public final class HrAssignmentService {

    private final JdbcHrAssignmentRepository repository;

    public HrAssignmentService(JdbcHrAssignmentRepository repository) {
        this.repository = repository;
    }

    public HrAssignment createAssignment(
            UUID tenantId, UUID employmentId, UUID organizationId,
            UUID orgUnitId, UUID positionId, UUID reportsToAssignmentId,
            UUID workLocationId,
            AssignmentType assignmentType, OccupancyMode occupancyMode,
            BigDecimal allocationPercent,
            LocalDate effectiveFrom, LocalDate effectiveTo) {
        return repository.createAssignmentAtomically(
                tenantId, employmentId, organizationId,
                orgUnitId, positionId, reportsToAssignmentId,
                workLocationId, null,
                assignmentType, occupancyMode,
                allocationPercent, effectiveFrom, effectiveTo);
    }

    /**
     * T8 — canonical assignment creation inside the CALLER's transaction
     * (governed hire conversion §7.1 step 6, with the in-transaction
     * position-occupancy re-check). Does NOT commit.
     */
    public HrAssignment createAssignmentWithinTransaction(
            java.sql.Connection connection, UUID tenantId, UUID employmentId,
            UUID organizationId, UUID orgUnitId, UUID positionId,
            UUID reportsToAssignmentId, UUID workLocationId,
            AssignmentType assignmentType, OccupancyMode occupancyMode,
            BigDecimal allocationPercent, LocalDate effectiveFrom, LocalDate effectiveTo) {
        return repository.createAssignmentWithinTransaction(
                connection, tenantId, employmentId, organizationId,
                orgUnitId, positionId, reportsToAssignmentId,
                workLocationId, null,
                assignmentType, occupancyMode,
                allocationPercent, effectiveFrom, effectiveTo);
    }

    public HrAssignment reviseAssignment(UUID tenantId, UUID assignmentId,
                                           LocalDate effectiveFrom,
                                           UUID newReportsToAssignmentId,
                                           UUID newPositionId,
                                           OccupancyMode newOccupancyMode,
                                           BigDecimal newAllocationPercent) {
        return repository.reviseAssignmentAtomically(
                tenantId, assignmentId, effectiveFrom,
                newReportsToAssignmentId, newPositionId,
                newOccupancyMode, newAllocationPercent);
    }
}
