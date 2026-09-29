package com.sanad.platform.platformiam.dto;

import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record ReplacePlatformRolesRequest(
        List<UUID> roleIds,
        @Size(max = 500) String reason) {
    public ReplacePlatformRolesRequest {
        roleIds = roleIds == null ? List.of() : List.copyOf(roleIds);
    }
}
