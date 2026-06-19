package com.sanad.platform.access;

import com.sanad.platform.access.grant.UserGrantStatus;

import java.time.Instant;
import java.util.UUID;

public record UserAccessResponse(
        UUID id,
        UUID tenantId,
        UUID userId,
        UUID roleId,
        String roleCode,
        UUID organizationId,
        UserGrantStatus status,
        Instant createdAt,
        Instant updatedAt) {
}
