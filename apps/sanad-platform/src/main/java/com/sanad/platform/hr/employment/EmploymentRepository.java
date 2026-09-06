package com.sanad.platform.hr.employment;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Employment repository port — persistence boundary for Employment and
 * EmploymentStatusPeriod.
 *
 * <p>Implementations MUST enforce tenant-scoped access on the same physical
 * database connection used for queries (FORCE RLS + set_config('app.tenant_id', ?, true)
 * on the same transaction).</p>
 *
 * <p>Task 2 RED skeleton: methods throw UnsupportedOperationException.
 * GREEN replaces with real JDBC implementation.</p>
 */
public interface EmploymentRepository {

    /** Persist a new Employment row (canonical projection over hr_employees). */
    void saveEmployment(Employment employment);

    /** Find a single Employment by id within a tenant scope. */
    Optional<Employment> findEmploymentById(UUID tenantId, UUID employmentId);

    /**
     * List employments within a tenant scope, newest first (WS5 Task 3 v2
     * directory read). RLS tenant isolation applies on the same connection.
     */
    List<Employment> listEmployments(UUID tenantId);

    /**
     * Count non-terminal Employments for a Person within a Legal Entity.
     * Used to enforce the "max one non-terminal Employment" invariant.
     */
    int countNonTerminalEmploymentsForPersonInLegalEntity(UUID tenantId, UUID personId, UUID legalEntityId);

    /** Persist a new Employment status period (history append). */
    void saveStatusPeriod(EmploymentStatusPeriod period);

    /** Return the ordered history of status periods for an Employment. */
    List<EmploymentStatusPeriod> statusPeriods(UUID tenantId, UUID employmentId);

    /** Update the Employment's current_status projection and increment version. */
    void updateCurrentStatusProjection(UUID tenantId, UUID employmentId,
                                         EmploymentStatus newStatus, long expectedVersion);
}
