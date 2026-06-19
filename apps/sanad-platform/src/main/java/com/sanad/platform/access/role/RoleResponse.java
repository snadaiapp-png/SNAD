package com.sanad.platform.access.role;

import java.time.Instant;
import java.util.UUID;

public record RoleResponse(
        UUID id,
        UUID tenantId,
        String code,
        String name,
        String description,
        RoleStatus status,
        Instant createdAt,
        Instant updatedAt) {

    public static RoleResponse from(Role role) {
        return new RoleResponse(role.getId(), role.getTenantId(), role.getCode(),
                role.getName(), role.getDescription(), role.getStatus(),
                role.getCreatedAt(), role.getUpdatedAt());
    }
}
