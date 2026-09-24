package com.sanad.platform.module.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO for a Module Capability.
 */
public record ModuleCapabilityResponse(
        UUID id,
        UUID moduleId,
        String code,
        String name,
        String description,
        String capabilityType,
        String defaultValue,
        String status,
        Instant createdAt,
        Instant updatedAt
) {}
