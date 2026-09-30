package com.sanad.platform.access.override.dto;

import java.time.Instant;
import java.util.UUID;

/** Create-override request (Wave 1 Task 9). */
public record CreateOverrideRequest(
        UUID targetUserId,
        String capabilityCode,
        String effect,
        String scopeType,
        UUID scopeReference,
        String reason,
        Instant validFrom,
        Instant validUntil) {
}
