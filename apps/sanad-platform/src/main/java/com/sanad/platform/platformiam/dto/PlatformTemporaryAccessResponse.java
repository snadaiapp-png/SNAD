package com.sanad.platform.platformiam.dto;

import java.time.Instant;
import java.util.UUID;

public record PlatformTemporaryAccessResponse(
        UUID id, UUID userId, UUID capabilityId, String capabilityCode,
        Instant effectiveFrom, Instant effectiveTo, String reason,
        UUID grantedBy, String status) {
}
