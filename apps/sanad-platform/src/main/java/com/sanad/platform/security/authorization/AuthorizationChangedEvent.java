package com.sanad.platform.security.authorization;

import java.util.UUID;

/** Immutable notification emitted after a committed authorization mutation. */
public record AuthorizationChangedEvent(
        UUID tenantId,
        UUID userId,
        String eventType,
        long authorizationVersion) {
}
