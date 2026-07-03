package com.sanad.platform.access.capability;

import java.time.Instant;
import java.util.UUID;

public record CapabilityResponse(
        UUID id,
        String code,
        String name,
        String description,
        CapabilityStatus status,
        Instant createdAt,
        Instant updatedAt) {

    public static CapabilityResponse from(AccessCapability capability) {
        return new CapabilityResponse(capability.getId(), capability.getCode(),
                capability.getName(), capability.getDescription(), capability.getStatus(),
                capability.getCreatedAt(), capability.getUpdatedAt());
    }
}
