package com.sanad.platform.hr.structure.domain;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Effective-dated version of a Job. Versions of the same stable Job
 * MUST NOT overlap (enforced by PostgreSQL EXCLUDE constraint).
 *
 * @param id            version UUID
 * @param tenantId      owning tenant
 * @param jobId         stable Job this version belongs to
 * @param title         version title
 * @param description   version description (nullable)
 * @param grade         version grade (nullable)
 * @param effectiveFrom inclusive start date
 * @param effectiveTo   exclusive end date (null = open)
 * @param status        version status
 */
public record HrJobVersion(
        UUID id,
        UUID tenantId,
        UUID jobId,
        String title,
        String description,
        String grade,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        String status
) {}
