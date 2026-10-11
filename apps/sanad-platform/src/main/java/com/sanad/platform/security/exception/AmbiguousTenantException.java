package com.sanad.platform.security.exception;

import com.sanad.platform.user.domain.User;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Thrown when email-only login finds the same email in multiple tenants.
 * Maps to HTTP 409 Conflict with a list of tenant IDs for selection.
 */
public class AmbiguousTenantException extends RuntimeException {

    private final List<UUID> tenantIds;

    public AmbiguousTenantException(String message, List<User> users) {
        super(message);
        this.tenantIds = users.stream()
                .map(User::getTenantId)
                .distinct()
                .collect(Collectors.toList());
    }

    /**
     * UUID-only alternative for G1 pre-auth discovery. No User entities
     * cross the RLS boundary.
     */
    public AmbiguousTenantException(String message, java.util.Collection<UUID> tenantIds) {
        super(message);
        this.tenantIds = tenantIds.stream().distinct().toList();
    }

    public List<UUID> getTenantIds() {
        return tenantIds;
    }
}
