package com.sanad.platform.test;

import java.io.PrintWriter;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.logging.Logger;

import javax.sql.DataSource;

/**
 * Test-only DataSource wrapper that presents the tenant identity to the
 * fail-closed RLS contract on every physical connection it hands out, by
 * executing the SAME HMAC-SIGNED binding protocol the production
 * application executes (V20261124_1 contract — app_tenant_ctx_slot /
 * app_bind_tenant_ctx with an HMAC-SHA256 mac over tenant:txid:pid).
 *
 * <p><b>G1-REV3 harness migration (2026-11-26).</b> The previous mechanism
 * issued a session-scoped RAW {@code set_config('app.tenant_id', ?, false)}.
 * Since V20261124_2 the enforced policies validate the HMAC-verified signed
 * context ({@code public.app_tenant_ctx_text()}), and a raw tenant GUC
 * without a valid mac fails closed on every tenant-scoped FORCE-RLS table.
 * The wrapper therefore now executes the full signed binding protocol on
 * each acquired connection. It does not bypass, weaken, or disable RLS in
 * any way — connections with no bound tenant keep failing closed, and
 * {@link #asNoTenant(Runnable)} still exposes the raw fail-closed surface
 * for negative tests.</p>
 *
 * <h3>Transaction model (autocommit-compatibility)</h3>
 *
 * <p>The signed binding is TRANSACTION-LOCAL ({@code SET LOCAL} inside
 * {@code app_bind_tenant_ctx}; the mac is minted over {@code txid_current()}),
 * so the wrapper holds ONE explicit transaction open per acquired connection
 * and commits it on {@code close()}. This preserves the observable semantics
 * of the legacy autocommit fixtures (every statement executed on an acquired
 * connection was persisted): with the wrapper, everything executed before the
 * connection is closed is committed exactly once. Explicit
 * {@code commit()}/{@code rollback()} calls pass through and the wrapper
 * automatically re-binds for the NEXT transaction, so multi-transaction
 * fixtures keep working without changes. Explicit
 * {@code setAutoCommit(true)} hands control back to the caller (the wrapper
 * stops managing the connection and stops re-binding — tenant-scoped
 * statements then fail closed, as the enforced catalog requires).</p>
 *
 * <h3>Scope rules</h3>
 * <ul>
 *   <li>Default tenant (or per-thread {@link #runAsTenant(UUID, Runnable)}
 *       override) is bound at acquisition and re-bound after every explicit
 *       transaction boundary.</li>
 *   <li>{@link #asNoTenant(Runnable)} hands out an unbound connection for
 *       tests that exercise the missing-context fail-closed rejection
 *       itself.</li>
 * </ul>
 *
 * <p>Test support only — MUST NOT be used in production code.</p>
 */
public final class TenantGucDataSource implements DataSource {

    private final DataSource delegate;

    /** Default tenant presented on connections acquired without an override. */
    private volatile UUID defaultTenant;

    /** Per-thread tenant override (cross-tenant seed/verify scopes). */
    private static final ThreadLocal<UUID> OVERRIDE = new ThreadLocal<>();

    /** Per-thread "no tenant" switch for missing-context negative tests. */
    private static final ThreadLocal<Boolean> SUPPRESS = ThreadLocal.withInitial(() -> Boolean.FALSE);

    public TenantGucDataSource(DataSource delegate, UUID defaultTenant) {
        this.delegate = delegate;
        this.defaultTenant = defaultTenant;
    }

    /** Point the session-scope GUC at the given (usually fresh) test tenant. */
    public void setDefaultTenant(UUID tenantId) {
        this.defaultTenant = tenantId;
    }

    /**
     * Run the action with every connection acquired on this thread bound to
     * {@code tenantId} (signed-bound). Seeds/reads owned by a second tenant
     * must run under this scope so RLS sees the legitimate owner identity.
     */
    public static void runAsTenant(UUID tenantId, Runnable action) {
        UUID previous = OVERRIDE.get();
        Boolean previousSuppress = SUPPRESS.get();
        OVERRIDE.set(tenantId);
        SUPPRESS.set(Boolean.FALSE);
        try {
            action.run();
        } finally {
            if (previous != null) {
                OVERRIDE.set(previous);
            } else {
                OVERRIDE.remove();
            }
            SUPPRESS.set(previousSuppress);
        }
    }

    /** {@link #runAsTenant(UUID, Runnable)} returning a value. */
    public static <T> T readAsTenant(UUID tenantId, Supplier<T> action) {
        java.util.concurrent.atomic.AtomicReference<T> box = new java.util.concurrent.atomic.AtomicReference<>();
        runAsTenant(tenantId, () -> box.set(action.get()));
        return box.get();
    }

    /**
     * Run the action with connections handed out WITHOUT any signed tenant
     * binding — the raw fail-closed surface. Used by tests that prove the
     * missing-context rejection itself.
     */
    public static void asNoTenant(Runnable action) {
        Boolean previousSuppress = SUPPRESS.get();
        UUID previous = OVERRIDE.get();
        SUPPRESS.set(Boolean.TRUE);
        try {
            action.run();
        } finally {
            SUPPRESS.set(previousSuppress);
            if (previous != null) {
                OVERRIDE.set(previous);
            } else {
                OVERRIDE.remove();
            }
        }
    }

    /**
     * Sign and bind the given tenant to the CURRENT transaction of the
     * physical connection — the exact protocol the production application
     * executes (HMAC-SHA256 over tenant:txid:pid, verified against all
     * ACTIVE keys by the SECURITY DEFINER verifier; failure raises and the
     * transaction fails closed).
     */
    private static void bindSigned(Connection connection, UUID tenant) throws SQLException {
        String keyHex = signingKeyHex();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT public.app_bind_tenant_ctx(?::uuid, "
                        + "encode(hmac((?::text||':'||txid_current()::text||':'||pg_backend_pid()::text)::bytea, "
                        + "decode(?,'hex'),'sha256'),'hex'))")) {
            ps.setObject(1, tenant);
            ps.setString(2, tenant.toString());
            ps.setString(3, keyHex);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next() || !rs.getBoolean(1)) {
                    throw new SQLException(
                            "TenantGucDataSource signed bind REJECTED by the database (no ACTIVE key "
                                    + "validated the mac) — transaction fails closed.");
                }
            }
        }
    }

    /**
     * Public test-infrastructure entry point for ONE-OFF signed binding on an
     * explicit connection: fixtures that manage their own transactions (JUnit
     * per-test transactions, Spring TransactionTemplate blocks, direct JDBC
     * blocks) execute this inside their OPEN transaction so the transaction-
     * local signed context covers the statements that follow. The caller owns
     * the transaction lifecycle; the bind itself is {@code SET LOCAL}-scoped
     * by {@code app_bind_tenant_ctx} and dies with the transaction.
     *
     * <p>The connection MUST have autocommit disabled and an open transaction
     * (the mac is minted over {@code txid_current()}); the fixture re-binds
     * after every commit/rollback exactly like the managed proxy does.</p>
     */
    public static void bindSignedContext(Connection connection, UUID tenant) throws SQLException {
        bindSigned(connection, tenant);
    }

    /**
     * HMAC key material for the signed binding protocol. Resolved from the
     * environment (the operator key) with a fallback to the documented
     * fixture key — this class is TEST-ONLY and the fixture key is valid
     * exclusively on disposable topologies (F-INT-05); the V20261124_2 chain
     * provisions that same key DB-side on those topologies.
     */
    public static String signingKeyHex() {
        String env = System.getenv("SANAD_TENANT_CTX_HMAC_KEY");
        if (env == null || env.isBlank()) {
            env = System.getenv("SNAD_RLS_CONTEXT_HMAC_KEY");
        }
        if (env != null && !env.isBlank()) {
            return env.trim();
        }
        return "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    }

    /**
     * Connection proxy: one explicit transaction per acquired connection,
     * committed on close (legacy autocommit persistence semantics), with
     * automatic signed re-binding after explicit commit/rollback boundaries.
     */
    private Connection managed(final Connection connection) throws SQLException {
        final boolean[] needsRebind = { false };
        return (Connection) Proxy.newProxyInstance(
                TenantGucDataSource.class.getClassLoader(),
                new Class<?>[] { Connection.class },
                new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                        String name = method.getName();
                        try {
                            if ("createStatement".equals(name) || "prepareStatement".equals(name)
                                    || "prepareCall".equals(name)) {
                                if (needsRebind[0]) {
                                    bindSigned(connection, currentTenant());
                                    needsRebind[0] = false;
                                }
                            } else if ("commit".equals(name) || "rollback".equals(name)) {
                                needsRebind[0] = true;
                            } else if ("setAutoCommit".equals(name)) {
                                boolean target = (Boolean) args[0];
                                if (target) {
                                    // Caller takes over: implicit commit per JDBC spec;
                                    // no further automatic re-binding.
                                    needsRebind[0] = false;
                                } else {
                                    needsRebind[0] = true;
                                }
                            } else if ("close".equals(name)) {
                                // Preserve legacy autocommit persistence semantics:
                                // everything executed on this connection is committed
                                // exactly once when the fixture releases it.
                                try {
                                    if (!connection.getAutoCommit()) {
                                        connection.commit();
                                    }
                                } finally {
                                    needsRebind[0] = false;
                                }
                            }
                            return method.invoke(connection, args);
                        } catch (InvocationTargetException exception) {
                            throw exception.getCause();
                        }
                    }
                });
    }

    private UUID currentTenant() {
        UUID tenant = OVERRIDE.get() != null ? OVERRIDE.get() : defaultTenant;
        if (tenant == null) {
            throw new IllegalStateException(
                    "TenantGucDataSource: no default tenant and no runAsTenant override — "
                            + "cannot establish the signed binding (use asNoTenant for the "
                            + "fail-closed negative surface).");
        }
        return tenant;
    }

    private Connection bind(Connection connection) throws SQLException {
        if (Boolean.TRUE.equals(SUPPRESS.get())) {
            return connection;
        }
        UUID tenant = OVERRIDE.get() != null ? OVERRIDE.get() : defaultTenant;
        if (tenant == null) {
            return connection;
        }
        // One explicit transaction per acquired connection; the signed binding
        // is transaction-local, so it covers every statement until the next
        // commit/rollback (which the proxy handles with automatic re-binding).
        connection.setAutoCommit(false);
        bindSigned(connection, tenant);
        return managed(connection);
    }

    @Override
    public Connection getConnection() throws SQLException {
        return bind(delegate.getConnection());
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return bind(delegate.getConnection(username, password));
    }

    @Override
    public PrintWriter getLogWriter() throws SQLException {
        return delegate.getLogWriter();
    }

    @Override
    public void setLogWriter(PrintWriter out) throws SQLException {
        delegate.setLogWriter(out);
    }

    @Override
    public void setLoginTimeout(int seconds) throws SQLException {
        delegate.setLoginTimeout(seconds);
    }

    @Override
    public int getLoginTimeout() throws SQLException {
        return delegate.getLoginTimeout();
    }

    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        return delegate.getParentLogger();
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        return delegate.unwrap(iface);
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        return delegate.isWrapperFor(iface);
    }
}
