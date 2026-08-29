package com.sanad.platform.hr.employment;

import javax.sql.DataSource;
import java.time.LocalDate;
import java.util.UUID;

/**
 * JDBC implementation of {@link EmploymentCommandService}.
 *
 * <p>Each lifecycle command:
 * <ol>
 *   <li>Loads Employment (with optimistic version check)</li>
 *   <li>Validates transition via EmploymentTransitionPolicy</li>
 *   <li>Atomically: closes open status period, opens new status period,
 *       updates current_status projection, increments version</li>
 * </ol>
 * </p>
 *
 * <p>Task 2 RED skeleton: methods throw UnsupportedOperationException.
 * GREEN replaces with real transition logic using the repository + transition policy.</p>
 */
public final class JdbcEmploymentCommandService implements EmploymentCommandService {

    private final EmploymentRepository repository;

    public JdbcEmploymentCommandService(EmploymentRepository repository) {
        this.repository = repository;
    }

    @Override
    public EmploymentTransitionResult submitOnboarding(UUID tenantId, UUID employmentId,
                                                        LocalDate effectiveDate, String reasonCode) {
        throw new UnsupportedOperationException(
                "JdbcEmploymentCommandService.submitOnboarding — Task 2 RED skeleton, implement in GREEN");
    }

    @Override
    public EmploymentTransitionResult activate(UUID tenantId, UUID employmentId,
                                                LocalDate effectiveDate, String reasonCode) {
        throw new UnsupportedOperationException(
                "JdbcEmploymentCommandService.activate — Task 2 RED skeleton, implement in GREEN");
    }

    @Override
    public EmploymentTransitionResult startLeave(UUID tenantId, UUID employmentId,
                                                  LocalDate effectiveDate, String reasonCode) {
        throw new UnsupportedOperationException(
                "JdbcEmploymentCommandService.startLeave — Task 2 RED skeleton, implement in GREEN");
    }

    @Override
    public EmploymentTransitionResult returnFromLeave(UUID tenantId, UUID employmentId,
                                                       LocalDate effectiveDate, String reasonCode) {
        throw new UnsupportedOperationException(
                "JdbcEmploymentCommandService.returnFromLeave — Task 2 RED skeleton, implement in GREEN");
    }

    @Override
    public EmploymentTransitionResult suspend(UUID tenantId, UUID employmentId,
                                               LocalDate effectiveDate, String reasonCode) {
        throw new UnsupportedOperationException(
                "JdbcEmploymentCommandService.suspend — Task 2 RED skeleton, implement in GREEN");
    }

    @Override
    public EmploymentTransitionResult reinstate(UUID tenantId, UUID employmentId,
                                                 LocalDate effectiveDate, String reasonCode) {
        throw new UnsupportedOperationException(
                "JdbcEmploymentCommandService.reinstate — Task 2 RED skeleton, implement in GREEN");
    }

    @Override
    public EmploymentTransitionResult terminate(UUID tenantId, UUID employmentId,
                                                  LocalDate effectiveDate, String reasonCode) {
        throw new UnsupportedOperationException(
                "JdbcEmploymentCommandService.terminate — Task 2 RED skeleton, implement in GREEN");
    }

    @Override
    public EmploymentTransitionResult void_(UUID tenantId, UUID employmentId,
                                              LocalDate effectiveDate, String reasonCode) {
        throw new UnsupportedOperationException(
                "JdbcEmploymentCommandService.void_ — Task 2 RED skeleton, implement in GREEN");
    }

    @Override
    public Employment rehire(UUID tenantId, UUID priorEmploymentId,
                              UUID personId, UUID legalEntityId,
                              String employeeNumber, String workerClassificationCode,
                              LocalDate effectiveDate, String reasonCode) {
        throw new UnsupportedOperationException(
                "JdbcEmploymentCommandService.rehire — Task 2 RED skeleton, implement in GREEN");
    }
}
