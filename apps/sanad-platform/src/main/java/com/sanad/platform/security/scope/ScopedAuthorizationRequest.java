package com.sanad.platform.security.scope;

import com.sanad.platform.hr.security.HrAuthorizationResourceContext;

import java.time.Instant;
import java.util.UUID;

public record ScopedAuthorizationRequest(
        UUID tenantId,
        UUID userId,
        String capabilityCode,
        HrAuthorizationResourceContext resource,
        Instant authorizationTime) {
}
