package com.sanad.platform.security;

import org.springframework.security.core.Authentication;

import java.util.Map;
import java.util.UUID;

/**
 * Utility for extracting tenant and user identity from Spring Security
 * {@link Authentication} objects.
 *
 * <p>The SNAD platform stores tenant_id and user_id in the Authentication's
 * details map (set by the JWT authentication filter). This utility provides
 * a single point of extraction for controllers that need tenant context.
 */
public final class SecurityContextUtils {

    private SecurityContextUtils() {}

    /** Extract the tenant_id from the authentication's details. */
    public static UUID tenantId(Authentication auth) {
        Object details = auth.getDetails();
        if (details instanceof Map<?, ?> map) {
            Object tid = map.get("tenant_id");
            if (tid instanceof String s) {
                return UUID.fromString(s);
            }
            if (tid instanceof UUID u) {
                return u;
            }
        }
        throw new IllegalStateException("No tenant_id in authentication details");
    }

    /** Extract the user_id from the authentication's details. */
    public static UUID userId(Authentication auth) {
        Object details = auth.getDetails();
        if (details instanceof Map<?, ?> map) {
            Object uid = map.get("user_id");
            if (uid instanceof String s) {
                return UUID.fromString(s);
            }
            if (uid instanceof UUID u) {
                return u;
            }
        }
        throw new IllegalStateException("No user_id in authentication details");
    }
}
