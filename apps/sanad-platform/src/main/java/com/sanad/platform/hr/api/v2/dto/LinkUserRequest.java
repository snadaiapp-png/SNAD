package com.sanad.platform.hr.api.v2.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * HRM-G0 / WS5 Task 3 slice 2 — canonical typed request for
 * POST /api/v2/hr/people/{personId}/user-link.
 *
 * <p>Links a tenant-scoped platform User to the Person (1:1 max per
 * tenant, enforced by the database partial unique index).
 */
public record LinkUserRequest(
        @NotNull UUID userId
) {
}
