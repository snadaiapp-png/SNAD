package com.sanad.platform.security.rls;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Unit tests for {@link TenantRlsConnectionHandler} — the SIGNED tenant-context
 * binding contract (G1 F-INT-01/03/04). Verifies handler mechanics without a
 * database: slot query → HMAC mint → bind, rejection paths, identity-swap
 * guard, pre-auth classification and pooling lifecycle. These are MECHANISM
 * tests; the end-to-end proof that the real application generates valid signed
 * bindings lives in SignedTenantContextServiceBindingPostgresTest (real
 * services + real DAL + real PostgreSQL).
 */
class TenantRlsConnectionHandlerTest {

    private static final String SLOT_SQL =
            "SELECT ctx_txid, ctx_pid FROM public.app_tenant_ctx_slot()";
    private static final String BIND_SQL =
            "SELECT public.app_bind_tenant_ctx(?::uuid, ?)";
    private static final String TEST_KEY_HEX =
            "fedcba9876543210fedcba9876543210fedcba9876543210fedcba9876543210";

    private Connection delegate;
    private Statement slotStatement;
    private PreparedStatement bindStatement;
    private ResultSet slotResult;
    private ResultSet bindResult;
    private Connection proxy;

    private static final String STUB_TXID = "12345";
    private static final int STUB_PID = 4242;

    @BeforeEach
    void setUp() throws Exception {
        TenantRlsRuntimePolicy.install(new TenantRlsRuntimePolicy.Policy(
                new TenantContextSigner(HexFormat.of().parseHex(TEST_KEY_HEX), false), true));
        delegate = mock(Connection.class);
        slotStatement = mock(Statement.class);
        bindStatement = mock(PreparedStatement.class);
        slotResult = mock(ResultSet.class);
        bindResult = mock(ResultSet.class);
        when(slotResult.next()).thenReturn(true);
        when(slotResult.getString("ctx_txid")).thenReturn(STUB_TXID);
        when(slotResult.getInt("ctx_pid")).thenReturn(STUB_PID);
        when(bindResult.next()).thenReturn(true);
        when(bindResult.getBoolean(1)).thenReturn(true);
        when(slotStatement.executeQuery(SLOT_SQL)).thenReturn(slotResult);
        when(bindStatement.executeQuery()).thenReturn(bindResult);
        // Every createStatement() on the delegate answers the slot query
        // (protocol calls); the caller's statement object is irrelevant to
        // these mechanics tests.
        when(delegate.createStatement()).thenReturn(slotStatement);
        when(delegate.prepareStatement(BIND_SQL)).thenReturn(bindStatement);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        TenantRlsRuntimePolicy.reset();
    }

    private String expectedMac(UUID tenantId) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(HexFormat.of().parseHex(TEST_KEY_HEX), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(
                    (tenantId + ":" + STUB_TXID + ":" + STUB_PID).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void bindsSignedContextWhenInTransactionWithTenant() throws Exception {
        UUID tenantId = UUID.randomUUID();
        setSecurityContext(tenantId);
        when(delegate.getAutoCommit()).thenReturn(false);

        proxy = wrap();
        proxy.createStatement();

        // The full signed protocol ran: slot query, HMAC mint (deterministic),
        // bind with (tenant, mac).
        verify(slotStatement, times(1)).executeQuery(SLOT_SQL);
        verify(bindStatement, times(1)).setString(1, tenantId.toString());
        verify(bindStatement, times(1)).setString(2, expectedMac(tenantId));
        verify(bindStatement, times(1)).executeQuery();
    }

    @Test
    void doesNotBindWhenAutoCommit() throws Exception {
        UUID tenantId = UUID.randomUUID();
        setSecurityContext(tenantId);
        when(delegate.getAutoCommit()).thenReturn(true); // autocommit mode

        proxy = wrap();
        proxy.createStatement();

        verify(slotStatement, never()).executeQuery(anyString());
        verify(bindStatement, never()).executeQuery();
    }

    /**
     * G1-i red-contract closure: a tenant-data statement issued inside an
     * active transaction WITHOUT any tenant context is rejected loudly. After
     * V20261124_2 the RLS policies fail closed on unbound sessions, so the
     * historical silent pass-through could only ever mask misrouting with
     * empty result sets.
     */
    @Test
    void rejectsMissingTenantContextInsideTransaction() throws Exception {
        // No security context set
        when(delegate.getAutoCommit()).thenReturn(false);

        proxy = wrap();
        assertThatThrownBy(() -> proxy.createStatement())
                .isInstanceOf(java.sql.SQLException.class)
                .hasMessageContaining("Tenant RLS context missing");

        verify(bindStatement, never()).executeQuery();
    }

    /** Genuinely pre-authentication paths are explicitly classified and stay transparent. */
    @Test
    void allowsExplicitPreAuthScopeInsideTransaction() throws Exception {
        when(delegate.getAutoCommit()).thenReturn(false);
        proxy = wrap();

        TenantRlsPreAuthScope.run(() -> {
            try {
                proxy.createStatement(); // must NOT throw, must NOT bind
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        });

        verify(slotStatement, never()).executeQuery(anyString());
        verify(bindStatement, never()).executeQuery();
    }

    /** The governed escape hatch (strict=false via the runtime policy) restores the legacy pass-through. */
    @Test
    void governedPassThroughWhenStrictModeDisabled() throws Exception {
        TenantRlsRuntimePolicy.install(new TenantRlsRuntimePolicy.Policy(
                new TenantContextSigner(HexFormat.of().parseHex(TEST_KEY_HEX), false), false));
        when(delegate.getAutoCommit()).thenReturn(false);

        proxy = wrap();
        proxy.createStatement(); // must NOT throw, must NOT bind

        verify(bindStatement, never()).executeQuery();
    }

    /** The pre-auth classification is thread-local and restores prior state (including nested scopes). */
    @Test
    void preAuthScopeIsThreadLocalAndRestoresPriorState() throws Exception {
        assertThat(TenantRlsPreAuthScope.isActive()).isFalse();
        TenantRlsPreAuthScope.run(() -> {
            assertThat(TenantRlsPreAuthScope.isActive()).isTrue();
            TenantRlsPreAuthScope.run(() ->
                    assertThat(TenantRlsPreAuthScope.isActive()).isTrue());
            assertThat(TenantRlsPreAuthScope.isActive()).isTrue();
        });
        assertThat(TenantRlsPreAuthScope.isActive()).isFalse();

        java.util.concurrent.atomic.AtomicBoolean otherThreadSawFalse = new java.util.concurrent.atomic.AtomicBoolean(true);
        Thread t = new Thread(() -> otherThreadSawFalse.set(!TenantRlsPreAuthScope.isActive()));
        TenantRlsPreAuthScope.activate();
        try {
            t.start();
            t.join();
            assertThat(otherThreadSawFalse.get()).isTrue();
        } finally {
            TenantRlsPreAuthScope.clear();
        }
    }

    /**
     * G1-i contract review: an UNAUTHENTICATED principal carries no tenant
     * context — inside an active transaction the statement is rejected loudly
     * (same red-contract closure as the missing-context case), not silently
     * passed through to fail-closed RLS with empty results.
     */
    @Test
    void rejectsUnauthenticatedInTransactionStatement() throws Exception {
        Authentication unauthenticated = new UsernamePasswordAuthenticationToken(
                "user", "creds"); // isAuthenticated() = false until granted authorities
        SecurityContextHolder.getContext().setAuthentication(unauthenticated);
        when(delegate.getAutoCommit()).thenReturn(false);

        proxy = wrap();
        assertThatThrownBy(() -> proxy.createStatement())
                .isInstanceOf(java.sql.SQLException.class)
                .hasMessageContaining("Tenant RLS context missing");

        verify(bindStatement, never()).executeQuery();
    }

    @Test
    void bindsSignedContextOnlyOncePerConnection() throws Exception {
        UUID tenantId = UUID.randomUUID();
        setSecurityContext(tenantId);
        when(delegate.getAutoCommit()).thenReturn(false);

        proxy = wrap();
        proxy.createStatement(); // first call — binds
        proxy.createStatement(); // second call — should NOT re-bind
        proxy.prepareStatement("SELECT 1"); // third call — should NOT re-bind

        verify(bindStatement, times(1)).setString(2, expectedMac(tenantId));
        verify(bindStatement, times(1)).executeQuery();
    }

    /**
     * A CLEARED security context (null current tenant) is not an identity
     * change: the already-applied transaction-local binding keeps every
     * statement of the transaction scoped to the original tenant (fail-closed
     * under FORCE RLS). Deferred flushes and post-authentication verification
     * queries must not be rejected by the guard. Regression guard for the
     * null-context path.
     */
    @Test
    void keepsAppliedTenantScopeWhenContextIsClearedMidTransaction() throws Exception {
        UUID tenantId = UUID.randomUUID();
        setSecurityContext(tenantId);
        when(delegate.getAutoCommit()).thenReturn(false);

        proxy = wrap();
        proxy.prepareStatement("SELECT 1"); // binds for tenantId

        // Actor context ends (request cleared it); a deferred statement follows.
        SecurityContextHolder.clearContext();
        proxy.prepareStatement("SELECT 2"); // must NOT throw; must NOT re-bind

        verify(bindStatement, times(1)).setString(2, expectedMac(tenantId));
    }

    /** Swapping to a DIFFERENT tenant inside one transaction remains rejected. */
    @Test
    void rejectsRealIdentityChangeInsideActiveTransaction() throws Exception {
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();
        setSecurityContext(tenantA);
        when(delegate.getAutoCommit()).thenReturn(false);

        proxy = wrap();
        proxy.prepareStatement("SELECT 1"); // binds for tenantA

        setSecurityContext(tenantB);
        assertThatThrownBy(() -> proxy.prepareStatement("SELECT 2"))
                .isInstanceOf(java.sql.SQLException.class)
                .hasMessageContaining("Tenant identity changed inside an active RLS transaction");
    }

    /**
     * G1-i contract review: a MALFORMED tenant id in the security context
     * yields no usable tenant context — inside an active transaction the
     * statement is rejected loudly. (In autocommit mode the statement remains
     * a transparent pass-through; see {@link #doesNotBindWhenAutoCommit()}.)
     */
    @Test
    void rejectsMalformedTenantIdInsideTransaction() throws Exception {
        // Set a malformed tenant_id in the details map
        Map<String, Object> details = Map.of("tenant_id", "not-a-uuid");
        UsernamePasswordAuthenticationToken auth =
                UsernamePasswordAuthenticationToken.authenticated("user", "creds", java.util.List.of());
        auth.setDetails(details);
        SecurityContextHolder.getContext().setAuthentication(auth);
        when(delegate.getAutoCommit()).thenReturn(false);

        proxy = wrap();
        assertThatThrownBy(() -> proxy.createStatement())
                .isInstanceOf(java.sql.SQLException.class)
                .hasMessageContaining("Tenant RLS context missing");

        verify(bindStatement, never()).executeQuery();
    }

    @Test
    void rebindsAfterCommitForPooledConnectionReuse() throws Exception {
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();
        when(delegate.getAutoCommit()).thenReturn(false);
        proxy = wrap();

        setSecurityContext(tenantA);
        proxy.prepareStatement("SELECT 1");
        proxy.commit();

        setSecurityContext(tenantB);
        proxy.prepareStatement("SELECT 1");

        verify(bindStatement, times(1)).setString(2, expectedMac(tenantA));
        verify(bindStatement, times(1)).setString(2, expectedMac(tenantB));
    }

    @Test
    void rejectsTenantSwitchWithinSingleTransaction() throws Exception {
        when(delegate.getAutoCommit()).thenReturn(false);
        proxy = wrap();
        setSecurityContext(UUID.randomUUID());
        proxy.createStatement();

        setSecurityContext(UUID.randomUUID());
        org.junit.jupiter.api.Assertions.assertThrows(java.sql.SQLException.class,
                () -> proxy.prepareStatement("SELECT 1"));
    }

    /**
     * F-INT-01/03/04 fail-closed contract: with NO signing key configured the
     * handler must NOT fall back to the raw untrusted GUC — the bind is
     * refused with an explicit error.
     */
    @Test
    void refusesBindWhenNoSignerConfigured() throws Exception {
        TenantRlsRuntimePolicy.install(new TenantRlsRuntimePolicy.Policy(null, true));
        setSecurityContext(UUID.randomUUID());
        when(delegate.getAutoCommit()).thenReturn(false);

        proxy = wrap();
        assertThatThrownBy(() -> proxy.createStatement())
                .isInstanceOf(java.sql.SQLException.class)
                .hasMessageContaining("signed-context binding refused");

        verify(delegate, never()).prepareStatement(anyString());
    }

    /**
     * The database verifier rejecting the mac (no ACTIVE key matches) fails
     * the transaction closed — the caller sees the SQLException.
     */
    @Test
    void bindRejectionFailsClosed() throws Exception {
        when(bindResult.next()).thenReturn(true);
        when(bindResult.getBoolean(1)).thenReturn(false);
        setSecurityContext(UUID.randomUUID());
        when(delegate.getAutoCommit()).thenReturn(false);

        proxy = wrap();
        assertThatThrownBy(() -> proxy.createStatement())
                .isInstanceOf(java.sql.SQLException.class)
                .hasMessageContaining("REJECTED");
    }

    /** A failed slot query (function missing) propagates the SQL error — the transaction fails closed. */
    @Test
    void slotFailureFailsClosed() throws Exception {
        when(slotStatement.executeQuery(SLOT_SQL))
                .thenThrow(new java.sql.SQLException("function public.app_tenant_ctx_slot() does not exist"));
        setSecurityContext(UUID.randomUUID());
        when(delegate.getAutoCommit()).thenReturn(false);

        proxy = wrap();
        assertThatThrownBy(() -> proxy.createStatement())
                .isInstanceOf(java.sql.SQLException.class)
                .hasMessageContaining("does not exist");

        verify(delegate, never()).prepareStatement(BIND_SQL);
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private void setSecurityContext(UUID tenantId) {
        Map<String, Object> details = Map.of(
                "tenant_id", tenantId.toString(),
                "user_id", UUID.randomUUID().toString());
        UsernamePasswordAuthenticationToken auth =
                UsernamePasswordAuthenticationToken.authenticated("user", "creds", java.util.List.of());
        auth.setDetails(details);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private Connection wrap() {
        return (Connection) java.lang.reflect.Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[] { Connection.class },
                new TenantRlsConnectionHandler(delegate));
    }
}
