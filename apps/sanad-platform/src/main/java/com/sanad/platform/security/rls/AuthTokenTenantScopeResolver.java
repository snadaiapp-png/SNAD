package com.sanad.platform.security.rls;

import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Restricted pre-auth tenant discovery for auth-token flows (D6 Batch 1,
 * SANAD-SEC-G1-D6-CLOSURE §5.3).
 *
 * <p>The refresh and password-reset flows look tokens up by hash BEFORE any
 * tenant is known. With fail-closed FORCE RLS on
 * {@code refresh_tokens}/{@code password_reset_tokens} (V20261028_2) those
 * hash-only lookups return nothing without a tenant GUC, so the flow must
 * first discover the tenant from a dedicated, audited discovery table.</p>
 *
 * <h3>Contract</h3>
 * <ul>
 *   <li>Reads ONLY {@code public.auth_token_tenant_index} — the minimal
 *       (kind, token_hash) → (tenant_id, user_id) mapping created by
 *       V20261028_2 and kept in sync by triggers. That table is
 *       intentionally NOT under RLS (same documented contract as
 *       {@code login_tenant_index}, V20261011_4).</li>
 *   <li>NEVER reads the token tables themselves before the caller applies
 *       the tenant GUC (fail-closed FORCE RLS stays authoritative).</li>
 *   <li>The tenant id is derived from a DB-verified hash mapping — never
 *       from client-supplied tenant parameters.</li>
 *   <li>No SECURITY DEFINER, no BYPASSRLS, no role changes — plain
 *       SECURITY INVOKER reads as the restricted {@code sanad} role.</li>
 * </ul>
 *
 * <p>Callers MUST immediately apply the returned tenant via
 * {@code TenantRlsTransactionContext#applyForCurrentTransaction} (or
 * {@code AuthService#applyTrustedUserScope}) inside the same transaction
 * before touching the token tables.</p>
 */
@Component
public class AuthTokenTenantScopeResolver {

    /** @see com.sanad.platform.security.domain.RefreshToken */
    public static final String KIND_REFRESH = "refresh";
    /** @see com.sanad.platform.security.domain.PasswordResetToken */
    public static final String KIND_PASSWORD_RESET = "password_reset";

    private final JdbcTemplate jdbc;

    public AuthTokenTenantScopeResolver(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Resolve the owning tenant of an auth token by its unguessable hash.
     *
     * @param kind {@link #KIND_REFRESH} or {@link #KIND_PASSWORD_RESET}
     * @param tokenHash the stored (immutable) hash of the raw token value;
     *        never the raw token itself
     * @return the owning tenant id, or empty when the hash is unknown
     *         (caller treats this exactly like "token not found")
     */
    public Optional<UUID> resolveTokenTenant(String kind, String tokenHash) {
        if (!KIND_REFRESH.equals(kind) && !KIND_PASSWORD_RESET.equals(kind)) {
            throw new IllegalArgumentException("Unsupported auth token kind");
        }
        if (tokenHash == null || tokenHash.isBlank()) {
            throw new IllegalArgumentException("Missing token hash");
        }
        try {
            return jdbc.query(
                    "SELECT tenant_id FROM public.auth_token_tenant_index "
                            + "WHERE kind = ? AND token_hash = ?",
                    (rs, rowNum) -> rs.getObject(1, UUID.class),
                    kind, tokenHash)
                    .stream()
                    .findFirst();
        } catch (DataAccessException exception) {
            // Preserve the cause in server-side diagnostics, but never log
            // the hash or any token material.
            throw new IllegalStateException(
                    "D6_AUTH_TOKEN_INDEX_CONTRACT_UNAVAILABLE: review V20261028_2 "
                            + "migration state and table grants",
                    exception);
        }
    }
}
