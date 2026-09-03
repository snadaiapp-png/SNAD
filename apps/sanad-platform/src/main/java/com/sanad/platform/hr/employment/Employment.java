package com.sanad.platform.hr.employment;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Canonical Employment projection over {@code hr_employees}.
 *
 * <p>An Employment represents an employment relationship between a Person
 * and a Legal Entity (employer of record). User != Person != Employment:
 * a Person may have multiple Employments over time, but at most one
 * NON_TERMINAL Employment per (tenant, person, legal_entity).</p>
 *
 * <p>Rehire creates a NEW Employment with {@code rehireOfEmployeeId} set
 * to the prior TERMINATED Employment — it does NOT reactivate the prior
 * Employment.</p>
 *
 * <p>Task 2 RED skeleton: this is the contract surface only. The real
 * projection behavior (mapping from hr_employees row + status history)
 * is implemented in GREEN. Tests assert real behavioral values; the
 * skeleton throws UnsupportedOperationException to produce the RED.</p>
 *
 * @param id                  Employment UUID
 * @param tenantId            owning tenant
 * @param personId            Person linked to this Employment (canonical Task 1A/1B)
 * @param legalEntityId       Legal Entity acting as employer of record
 * @param employeeNumber      tenant-unique employee number
 * @param workerClassificationCode classification (FULL_TIME, PART_TIME, etc.)
 * @param currentStatus       projection of the latest status period
 * @param employmentStartDate date employment begins (canonical)
 * @param terminationDate     date employment was terminated (nullable)
 * @param rehireOfEmployeeId  prior Employment UUID if this Employment is a rehire
 * @param version             optimistic-concurrency version
 */
public record Employment(
        UUID id,
        UUID tenantId,
        UUID personId,
        UUID legalEntityId,
        String employeeNumber,
        String workerClassificationCode,
        EmploymentStatus currentStatus,
        LocalDate employmentStartDate,
        LocalDate terminationDate,
        UUID rehireOfEmployeeId,
        long version
) {}
