package com.sanad.platform.hr.api.v2.dto;

import java.util.UUID;

/**
 * HRM-G0 / WS5 Task 4 — result of freeze/close. Staffability only: occupancy
 * and version history are structurally untouched by these operations.
 */
public record StaffabilityResponse(
        UUID positionId,
        String staffability
) {
}
