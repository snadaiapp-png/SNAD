package com.sanad.platform.hr.employment;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Immutable record of a single Employment status period.
 *
 * <p>History is append-only: closed periods are immutable.
 * Exactly one open period (effective_to IS NULL) exists at a time
 * for a given Employment, except during atomic transitions where
 * the old period is closed and a new period is opened in the same
 * transaction.</p>
 *
 * @param id                period UUID
 * @param tenantId          owning tenant
 * @param employmentId      Employment this period belongs to
 * @param status            EmploymentStatus during this period
 * @param effectiveFrom     inclusive start date
 * @param effectiveTo       exclusive end date (nullable = open period)
 * @param reasonCode        machine-readable transition reason
 * @param reasonText        human-readable reason
 * @param changedBy         actor who triggered the transition
 * @param transitionEventId event UUID for end-to-end correlation
 */
public record EmploymentStatusPeriod(
        UUID id,
        UUID tenantId,
        UUID employmentId,
        EmploymentStatus status,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        String reasonCode,
        String reasonText,
        UUID changedBy,
        UUID transitionEventId
) {}
