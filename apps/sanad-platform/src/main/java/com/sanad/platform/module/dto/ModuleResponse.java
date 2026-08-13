package com.sanad.platform.module.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO for a Module in the registry.
 */
public record ModuleResponse(
        UUID id,
        String code,
        String name,
        String description,
        String status,
        int displayOrder,
        String version,
        boolean enabled,
        Instant createdAt,
        Instant updatedAt
) {}
