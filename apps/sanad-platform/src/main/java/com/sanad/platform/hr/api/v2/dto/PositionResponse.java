package com.sanad.platform.hr.api.v2.dto;

import com.sanad.platform.hr.structure.infrastructure.JdbcHrStructureRepository.PositionWithStaffability;

import java.time.LocalDate;
import java.util.UUID;

/**
 * HRM-G0 / WS5 Task 4 — canonical Position view. {@code staffability} is the
 * root-row status (ACTIVE/INACTIVE/ARCHIVED); freeze/close act ONLY on it.
 */
public record PositionResponse(
        UUID positionId,
        String staffability,
        String title,
        UUID jobId,
        UUID orgUnitId,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        String status
) {

    public static PositionResponse from(PositionWithStaffability position) {
        if (position.version() == null) {
            return new PositionResponse(position.positionId(), position.staffability(),
                    null, null, null, null, null, null);
        }
        return new PositionResponse(position.positionId(), position.staffability(),
                position.version().title(), position.version().jobId(), position.version().orgUnitId(),
                position.version().effectiveFrom(), position.version().effectiveTo(),
                position.version().status());
    }
}
