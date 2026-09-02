package com.sanad.platform.hr.structure.domain;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Effective-dated version of an Org Unit. Versions of the same stable
 * Org Unit MUST NOT overlap (enforced by PostgreSQL EXCLUDE constraint).
 *
 * @param id               version UUID
 * @param tenantId         owning tenant
 * @param orgUnitId        stable Org Unit this version belongs to
 * @param name             version name
 * @param code             version code
 * @param unitType         BUSINESS_UNIT / DIVISION / DEPARTMENT / TEAM
 * @param parentOrgUnitId  parent stable Org Unit (nullable for root)
 * @param effectiveFrom    inclusive start date
 * @param effectiveTo      exclusive end date (null = open)
 * @param status           version status
 */
public record HrOrgUnitVersion(
        UUID id,
        UUID tenantId,
        UUID orgUnitId,
        String name,
        String code,
        String unitType,
        UUID parentOrgUnitId,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        String status
) {}
