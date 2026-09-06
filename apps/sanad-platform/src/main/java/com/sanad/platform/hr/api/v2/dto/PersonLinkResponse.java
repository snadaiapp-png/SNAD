package com.sanad.platform.hr.api.v2.dto;

import java.util.UUID;

/**
 * HRM-G0 / WS5 Task 3 slice 2 — result of the user-link operations.
 * {@code linked} states the resulting link presence after the operation
 * (DELETE is idempotent and reports {@code false} when no link existed).
 */
public record PersonLinkResponse(
        UUID personId,
        UUID userId,
        boolean linked
) {
}
