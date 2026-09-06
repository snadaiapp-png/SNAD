package com.sanad.platform.hr.api.v2.dto;

import com.sanad.platform.hr.structure.domain.HrJobVersion;

import java.time.LocalDate;
import java.util.UUID;

/** HRM-G0 / WS5 Task 4 — canonical Job view (root id + effective version fields). */
public record JobResponse(
        UUID jobId,
        String title,
        String description,
        String grade,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        String status
) {

    public static JobResponse from(HrJobVersion version) {
        return new JobResponse(version.jobId(), version.title(), version.description(), version.grade(),
                version.effectiveFrom(), version.effectiveTo(), version.status());
    }
}
