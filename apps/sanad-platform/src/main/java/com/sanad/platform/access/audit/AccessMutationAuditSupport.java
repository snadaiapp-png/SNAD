package com.sanad.platform.access.audit;

import com.sanad.platform.admin.service.PlatformAuditWriter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Central audit adapter for legacy access mutations without changing their public signatures. */
@Component
public class AccessMutationAuditSupport {
    private final PlatformAuditWriter writer;

    public AccessMutationAuditSupport(PlatformAuditWriter writer) {
        this.writer = writer;
    }

    public void success(UUID targetTenantId, String action, String resourceType,
                        String resourceId, Object beforeState, Object afterState) {
        Actor actor = actor();
        writer.writeSuccess(actor.tenantId(), actor.userId(), targetTenantId,
                action, resourceType, resourceId, "ACCESS_ADMIN_MUTATION",
                beforeState, afterState, null, Instant.now());
    }

    private static Actor actor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getDetails() instanceof Map<?, ?> details)) {
            return new Actor(null, null);
        }
        return new Actor(uuid(details.get("tenant_id")), uuid(details.get("user_id")));
    }

    private static UUID uuid(Object value) {
        if (value == null) return null;
        try {
            return UUID.fromString(value.toString());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private record Actor(UUID tenantId, UUID userId) {}
}
