package com.sanad.platform.security.scope;

import java.time.Instant;
import java.util.UUID;

public record AccessScopeGrant(
        UUID id,
        UUID tenantId,
        UUID roleId,
        UUID userId,
        AccessScopeType scopeType,
        UUID organizationId,
        UUID orgUnitId,
        UUID legalEntityId,
        boolean directException,
        String reason,
        UUID grantedBy,
        Instant effectiveFrom,
        Instant effectiveTo) {
}
