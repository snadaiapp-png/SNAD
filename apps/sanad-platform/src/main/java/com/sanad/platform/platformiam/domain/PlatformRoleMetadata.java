package com.sanad.platform.platformiam.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Platform-only metadata layered over the shared tenant-scoped role model.
 */
public record PlatformRoleMetadata(
        UUID controlTenantId,
        UUID roleId,
        RoleType roleType,
        boolean protectedRole,
        boolean ownerRole,
        Instant createdAt,
        Instant updatedAt) {

    public enum RoleType {
        SYSTEM,
        CUSTOM
    }
}
