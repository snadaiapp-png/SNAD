package com.sanad.platform.security.service;

import com.sanad.platform.security.rls.TenantRlsPreAuthScope;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataAccessException;
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
        try {
            // G1 signed-context contract (V20261124_1/_2): login is the
            // canonical pre-authentication path. The strict connection handler
            // rejects context-less statements inside an active transaction —
            // this discovery SELECT runs BEFORE any tenant is known, so it is
            // explicitly classified as pre-auth here (deliberate, code-visible,
            // reviewable — the same contract TenantRlsPreAuthScope documents for
            // login flows). This does NOT bypass Row-Level Security: the query
            // reads only login_tenant_index (pre-auth discovery table, no RLS
            // by design, UUID-only result) and every subsequent tenant-data
            // statement still requires the HMAC-signed binding.
            return TenantRlsPreAuthScope.call(() ->
                jdbc.query(
                    "SELECT tenant_id FROM public.g1_resolve_login_tenants(?, ?)",
                    (rs, rowNum) -> rs.getObject(1, UUID.class),
                    kind, normalizedIdentifier));
        } catch (DataAccessException exception) {
            // Preserve the cause in server-side diagnostics, but never log
            // the identifier, SQL parameters, or password material.
            throw new IllegalStateException(
                "G1_PREAUTH_DATABASE_CONTRACT_UNAVAILABLE: review isolated database migration and EXECUTE privileges",
                exception);
        }
    }
}
