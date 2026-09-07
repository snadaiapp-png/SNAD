package com.sanad.platform.hr.audit;

import java.util.Objects;
import java.util.UUID;

/**
 * Identity of the authenticated principal performing an HR operation
 * (WS4 Task 5). Carries correlation metadata only — never credentials.
 *
 * @param tenantId      tenant the actor is operating in (mandatory)
 * @param actorUserId   authenticated user id (mandatory)
 * @param correlationId correlation id linking the operation trail
 * @param requestId     request id linking the API call trail
 */
public record HrAuthenticatedContext(
        UUID tenantId,
        UUID actorUserId,
        UUID correlationId,
        UUID requestId) {

    public HrAuthenticatedContext {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(actorUserId, "actorUserId");
    }
}
