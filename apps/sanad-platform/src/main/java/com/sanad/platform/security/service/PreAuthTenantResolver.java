package com.sanad.platform.security.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Restricted pre-auth tenant discovery. Only UUIDs are retrieved; never
 * cross-tenant User entities, password hashes, emails or usernames.
 *
 * The database function is an explicitly reviewed SECURITY DEFINER boundary,
 * not a reason to give the application runtime BYPASSRLS.
 */
@Component
public final class PreAuthTenantResolver {
    private final JdbcTemplate jdbc;

    public PreAuthTenantResolver(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<UUID> resolve(String kind, String normalizedIdentifier) {
        if (!"email".equals(kind) && !"username".equals(kind)) {
            throw new IllegalArgumentException("Unsupported login identifier");
        }
        if (normalizedIdentifier == null || normalizedIdentifier.isBlank()) {
            throw new IllegalArgumentException("Missing login identifier");
        }
        return jdbc.query(
            "SELECT tenant_id FROM public.g1_resolve_login_tenants(?, ?)",
            (rs, rowNum) -> rs.getObject(1, UUID.class),
            kind, normalizedIdentifier);
    }
}
