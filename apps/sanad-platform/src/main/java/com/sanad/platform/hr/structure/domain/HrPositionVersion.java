package com.sanad.platform.hr.structure.domain;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Effective-dated version of a Position (stable seat). The stable identity
 * is the existing {@code hr_positions.id}. Versions MUST NOT overlap for
 * the same position.
 *
 * @param id              version UUID
 * @param tenantId        owning tenant
 * @param positionId      stable Position (hr_positions.id)
 * @param organizationId  canonical Organization context (nullable until backfill)
 * @param jobId           canonical Job reference (nullable)
 * @param orgUnitId       canonical Org Unit reference (nullable)
 * @param title           version title
 * @param effectiveFrom   inclusive start date
 * @param effectiveTo     exclusive end date (null = open)
 * @param status          version status
 */
public record HrPositionVersion(
        UUID id,
        UUID tenantId,
        UUID positionId,
        UUID organizationId,
        UUID jobId,
        UUID orgUnitId,
        String title,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        String status
) {}
