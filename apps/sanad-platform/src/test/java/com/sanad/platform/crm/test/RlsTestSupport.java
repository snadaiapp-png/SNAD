package com.sanad.platform.crm.test;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Test-only helper for establishing PostgreSQL RLS tenant context in
 * integration tests that directly invoke application services or perform
 * raw JDBC cleanup on FORCE-RLS tables.
 *
 * <p>Two patterns:
 * <ul>
 *   <li>{@link #setSecurityContext(UUID, UUID)} — sets SecurityContextHolder
 *       with tenant_id in Authentication.details so that
 *       {@code TenantRlsConnectionHandler} applies
 *       a signed context via {@code app_bind_tenant_ctx} inside {@code @Transactional}
 *       service boundaries.</li>
 *   <li>{@link #deleteTenantRows(NamedParameterJdbcTemplate, TransactionTemplate, UUID, List)}
 *       — runs per-tenant cleanup with an authenticated tenant and signed scope so
 *       FORCE-RLS tables accept the DELETE.</li>
 * </ul>
 *
 * <p>This helper MUST NOT be used in production code. It is test-only.
 */
public final class RlsTestSupport {

    private RlsTestSupport() {}

    /**
     * Set SecurityContextHolder with the given tenant/user identity.
     * The tenant_id is placed in Authentication.details so
     * TenantRlsConnectionHandler can extract it and apply
     * SET LOCAL app.tenant_id inside @Transactional boundaries.
     */
    public static void setSecurityContext(UUID tenantId, UUID userId) {
        UsernamePasswordAuthenticationToken auth = UsernamePasswordAuthenticationToken.authenticated(
                userId.toString(), "n/a", List.of());
        auth.setDetails(Map.of(
                "tenant_id", tenantId.toString(),
                "user_id", userId.toString()));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    /**
     * Clear SecurityContextHolder.
     */
    public static void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    /**
     * Delete rows from the given FORCE-RLS tables for the specified tenant,
     * using the production signed binding in a tenant-authenticated transaction so RLS accepts
     * the DELETE.
     *
     * @param jdbc         NamedParameterJdbcTemplate bound to the same DataSource
     * @param transactions TransactionTemplate bound to the same DataSource
     * @param tenantId     the tenant whose rows should be deleted
     * @param tables       list of table names that have tenant_id column + FORCE RLS
     */
    public static void deleteTenantRows(NamedParameterJdbcTemplate jdbc,
                                         TransactionTemplate transactions,
                                         UUID tenantId,
                                         List<String> tables) {
        Authentication previous = SecurityContextHolder.getContext().getAuthentication();
        try {
            // Fixture-only authenticated identity. The production handler
            // performs the HMAC-signed transaction bind before any SQL.
            setSecurityContext(tenantId, UUID.randomUUID());
            transactions.executeWithoutResult(status -> {
                for (String table : tables) {
                    jdbc.update("DELETE FROM " + table + " WHERE tenant_id = :t",
                            new MapSqlParameterSource("t", tenantId));
                }
            });
        } finally {
            SecurityContextHolder.getContext().setAuthentication(previous);
        }
    }
}
