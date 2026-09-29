package com.sanad.platform.platformiam.dto;

import com.sanad.platform.access.role.RoleStatus;
import com.sanad.platform.platformiam.domain.PlatformRoleMetadata;

import java.util.UUID;

public record PlatformRoleResponse(
        UUID id,
        String code,
        String name,
        String description,
        RoleStatus status,
        PlatformRoleMetadata.RoleType roleType,
        boolean protectedRole,
        boolean ownerRole) {
}
