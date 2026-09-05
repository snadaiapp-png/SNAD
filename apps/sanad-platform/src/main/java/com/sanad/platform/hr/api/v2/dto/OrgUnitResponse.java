package com.sanad.platform.hr.api.v2.dto;

import com.sanad.platform.hr.structure.domain.HrOrgUnitVersion;

import java.time.LocalDate;
import java.util.UUID;

/** HRM-G0 / WS5 Task 4 — canonical Org Unit view (root id + effective version fields). */
public record OrgUnitResponse(
        UUID orgUnitId,
        String name,
        String code,
        String unitType,
        UUID parentOrgUnitId,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        String status
) {

    public static OrgUnitResponse from(HrOrgUnitVersion version) {
        return new OrgUnitResponse(version.orgUnitId(), version.name(), version.code(), version.unitType(),
                version.parentOrgUnitId(), version.effectiveFrom(), version.effectiveTo(), version.status());
    }
}
