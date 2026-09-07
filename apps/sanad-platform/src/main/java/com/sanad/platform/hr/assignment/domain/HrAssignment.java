package com.sanad.platform.hr.assignment.domain;

import java.time.LocalDate;
import java.util.UUID;

/**
 * HR Assignment — effective-dated assignment of an Employment to an
 * organizational context (Org Unit, Position, Job) with a reporting
 * relationship.
 *
 * <p>User != Person != Employment != Assignment. An Assignment links
 * an Employment to an organizational context for a specific period.</p>
 *
 * @param id                    assignment UUID
 * @param tenantId              owning tenant
 * @param employmentId          Employment this assignment belongs to
 * @param organizationId        canonical Organization
 * @param orgUnitId             Org Unit (nullable)
 * @param positionId            Position seat (nullable — optional by policy)
 * @param reportsToAssignmentId reporting manager's Assignment (nullable)
 * @param workLocationId        Work Location (nullable)
 * @param assignmentType        PRIMARY or SECONDARY
 * @param occupancyMode         OCCUPYING or NON_OCCUPYING
 * @param allocationPercent     0 < x <= 100
 * @param effectiveFrom         inclusive start date
 * @param effectiveTo           exclusive end date (null = open)
 * @param status                ACTIVE / ENDED / VOIDED
 * @param version               optimistic concurrency version
 */
public record HrAssignment(
        UUID id,
        UUID tenantId,
        UUID employmentId,
        UUID organizationId,
        UUID orgUnitId,
        UUID positionId,
        UUID reportsToAssignmentId,
        UUID workLocationId,
        AssignmentType assignmentType,
        OccupancyMode occupancyMode,
        java.math.BigDecimal allocationPercent,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        String status,
        long version
) {}
