package com.sanad.platform.access.override.dto;

import java.time.Instant;
import java.util.UUID;

/** Override administration response (Wave 1 Task 9). */
public record OverrideResponse(
        UUID id,
        UUID userId,
        String capabilityCode,
        String effect,
        String scopeType,
        UUID scopeReference,
        String reason,
        Instant validFrom,
        Instant validUntil,
        int version) {
}
