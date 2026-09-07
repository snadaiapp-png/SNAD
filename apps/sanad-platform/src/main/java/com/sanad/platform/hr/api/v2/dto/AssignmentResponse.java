package com.sanad.platform.hr.api.v2.dto;

import com.sanad.platform.hr.assignment.domain.AssignmentType;
import com.sanad.platform.hr.assignment.domain.OccupancyMode;
import com.sanad.platform.hr.assignment.domain.HrAssignment;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * HRM-G0 / WS5 Task 4 — canonical typed view of an assignment period.
 * Each row is one effective-dated period; superseded periods keep their
 * identity with a closed {@code effectiveTo} (history is preserved).
 */
public record AssignmentResponse(
        UUID assignmentId,
        UUID employmentId,
        UUID organizationId,
        UUID orgUnitId,
        UUID positionId,
        UUID reportsToAssignmentId,
        AssignmentType assignmentType,
        OccupancyMode occupancyMode,
        BigDecimal allocationPercent,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        String status,
        long version
) {

    public static AssignmentResponse from(HrAssignment assignment) {
        return new AssignmentResponse(assignment.id(), assignment.employmentId(), assignment.organizationId(),
                assignment.orgUnitId(), assignment.positionId(), assignment.reportsToAssignmentId(),
                assignment.assignmentType(), assignment.occupancyMode(), assignment.allocationPercent(),
                assignment.effectiveFrom(), assignment.effectiveTo(), assignment.status(), assignment.version());
    }
}
