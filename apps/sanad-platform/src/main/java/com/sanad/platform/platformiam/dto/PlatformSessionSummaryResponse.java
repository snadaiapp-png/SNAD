package com.sanad.platform.platformiam.dto;

import java.time.Instant;
import java.util.UUID;

public record PlatformSessionSummaryResponse(
        UUID userId,
        long sessionVersion,
        Instant lastLoginAt) {
}
