package com.sanad.platform.access.api;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;

import java.util.Map;
import java.util.UUID;

/** Canonical extraction of tenant/user IDs from the authenticated JWT details map. */
public final class AccessPrincipalContext {
    private AccessPrincipalContext() {}

    public static UUID requireTenantId(Authentication authentication) {
        return require(authentication, "tenant_id");
    }

    public static UUID requireUserId(Authentication authentication) {
        return require(authentication, "user_id");
    }

    private static UUID require(Authentication authentication, String key) {
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getDetails() instanceof Map<?, ?> details)
                || details.get(key) == null) {
            throw new AccessDeniedException("Invalid authenticated authorization context");
        }
        try {
            return UUID.fromString(details.get(key).toString());
        } catch (IllegalArgumentException exception) {
            throw new AccessDeniedException("Invalid authenticated authorization context", exception);
        }
    }
}
