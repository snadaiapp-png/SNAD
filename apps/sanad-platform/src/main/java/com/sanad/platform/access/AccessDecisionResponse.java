package com.sanad.platform.access;

import java.util.UUID;

public record AccessDecisionResponse(
        UUID tenantId,
        UUID userId,
        UUID organizationId,
        String capabilityCode,
        boolean allowed,
        String reason,
        UUID matchedRoleId,
        String matchedRoleCode) {
}
