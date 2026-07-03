package com.sanad.platform.access.role;

import java.time.Instant;
import java.util.UUID;

public record RoleAccessResponse(
        UUID id,
        UUID tenantId,
        UUID roleId,
        UUID capabilityId,
        String capabilityCode,
        Instant createdAt) {
}
