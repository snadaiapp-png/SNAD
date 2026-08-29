package com.sanad.platform.hr.employment;

import java.util.UUID;

/**
 * Result of an Employment lifecycle transition command.
 *
 * @param employmentId      the Employment whose status changed
 * @param previousStatus    the status before the transition
 * @param newStatus         the status after the transition
 * @param closedPeriodId    UUID of the period that was closed (null if first transition)
 * @param newPeriodId       UUID of the new open period created by the transition
 */
public record EmploymentTransitionResult(
        UUID employmentId,
        EmploymentStatus previousStatus,
        EmploymentStatus newStatus,
        UUID closedPeriodId,
        UUID newPeriodId
) {}
