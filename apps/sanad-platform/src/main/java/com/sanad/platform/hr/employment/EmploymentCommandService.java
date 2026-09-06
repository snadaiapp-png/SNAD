package com.sanad.platform.hr.employment;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Employment lifecycle command service — explicit lifecycle commands,
 * NOT a generic setStatus PATCH API.
 *
 * <p>Approved lifecycle methods (per HRM-G0 Task 2 spec):
 * <ul>
 *   <li>{@link #submitOnboarding} — DRAFT → PENDING_ONBOARDING</li>
 *   <li>{@link #activate} — PENDING_ONBOARDING / ON_LEAVE / SUSPENDED → ACTIVE</li>
 *   <li>{@link #startLeave} — ACTIVE → ON_LEAVE</li>
 *   <li>{@link #returnFromLeave} — ON_LEAVE → ACTIVE</li>
 *   <li>{@link #suspend} — ACTIVE / ON_LEAVE → SUSPENDED</li>
 *   <li>{@link #reinstate} — SUSPENDED → ACTIVE</li>
 *   <li>{@link #terminate} — ACTIVE / ON_LEAVE / SUSPENDED → TERMINATED</li>
 *   <li>{@link #void} — DRAFT / PENDING_ONBOARDING → VOIDED</li>
 *   <li>{@link #rehire} — creates a NEW Employment with rehireOfEmployeeId set</li>
 * </ul>
 * </p>
 *
 * <p>Forbidden transitions:
 * <ul>
 *   <li>TERMINATED → any non-terminal state (must use {@link #rehire})</li>
 *   <li>VOIDED → any non-terminal state</li>
 * </ul>
 * </p>
 *
 * <p>Each transition is atomic: closes the open period, opens a new period,
 * updates the current_status projection, and increments the version.</p>
 *
 * <p>Task 2 RED skeleton: methods throw UnsupportedOperationException.
 * GREEN replaces with real implementation using EmploymentTransitionPolicy.</p>
 */
public interface EmploymentCommandService {

    EmploymentTransitionResult submitOnboarding(UUID tenantId, UUID employmentId, LocalDate effectiveDate, String reasonCode);
    EmploymentTransitionResult activate(UUID tenantId, UUID employmentId, LocalDate effectiveDate, String reasonCode);
    EmploymentTransitionResult startLeave(UUID tenantId, UUID employmentId, LocalDate effectiveDate, String reasonCode);
    EmploymentTransitionResult returnFromLeave(UUID tenantId, UUID employmentId, LocalDate effectiveDate, String reasonCode);
    EmploymentTransitionResult suspend(UUID tenantId, UUID employmentId, LocalDate effectiveDate, String reasonCode);
    EmploymentTransitionResult reinstate(UUID tenantId, UUID employmentId, LocalDate effectiveDate, String reasonCode);
    EmploymentTransitionResult terminate(UUID tenantId, UUID employmentId, LocalDate effectiveDate, String reasonCode);

    /**
     * Void an Employment. Only allowed from DRAFT or PENDING_ONBOARDING.
     * Voided Employments cannot be reactivated — rehire creates a new row.
     */
    EmploymentTransitionResult void_(UUID tenantId, UUID employmentId, LocalDate effectiveDate, String reasonCode);

    /**
     * Rehire a TERMINATED Person. Creates a NEW Employment row with
     * {@code rehireOfEmployeeId} set to the prior TERMINATED Employment UUID.
     * Does NOT reactivate the prior Employment.
     *
     * @return the new Employment
     */
    Employment rehire(UUID tenantId, UUID priorEmploymentId,
                      UUID personId, UUID legalEntityId,
                      String employeeNumber, String workerClassificationCode,
                      LocalDate effectiveDate, String reasonCode);
}
