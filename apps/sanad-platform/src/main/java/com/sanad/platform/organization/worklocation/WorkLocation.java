package com.sanad.platform.organization.worklocation;

import java.time.Instant;
import java.util.UUID;

public record WorkLocation(
        UUID id,
        UUID tenantId,
        String code,
        String name,
        String countryCode,
        String city,
        String timezone,
        WorkLocationStatus status,
        Instant createdAt,
        Instant updatedAt
) {
    public boolean isActive() {
        return status == WorkLocationStatus.ACTIVE;
    }
}
