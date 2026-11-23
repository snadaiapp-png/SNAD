package com.sanad.platform.test;

import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.logging.Logger;

import javax.sql.DataSource;

/**
 * Test-only DataSource wrapper that presents the tenant identity to the
 * fail-closed RLS contract on every physical connection it hands out, via the
 * SIGNED TENANT-CONTEXT BIND protocol (V20261124_1/V20261124_2): connections
 * are lazily bound (slot + HMAC + app_bind_tenant_ctx) before the first
 * statement inside an active transaction.
 *
 * <p>This is the PostgreSQL Direct fixture counterpart of the two production
 * binding paths — {@code TenantRlsConnectionHandler} (Spring-managed
 * transactions) and {@code TenantRlsTransactionContext} (trusted background
 * work): it binds the SAME {@code app.tenant_id} GUC the application binds
 * per authenticated request. It does not bypass, weaken, or disable RLS in
 * any way — the policy predicate
 * {@code tenant_id::text = NULLIF(current_setting('app.tenant_id', true), '')}
 * stays authoritative and connections with no bound tenant keep failing
 * closed.</p>
 *
 * <h3>Scope rules</h3>
 * <ul>
 *   <li>Transaction scope: the signed bind lives for the current transaction
 *       only (SET LOCAL semantics) — nothing leaks across tenants, tests, or
 *       pooled connections. Autocommit acquisitions stay unbound and observe
 *       the fail-closed zero-row surface.</li>
 *   <li>{@link #runAsTenant(UUID, Runnable)} / {@link #readAsTenant(UUID, Supplier)}
 *       re-scope the calling thread while seeding or verifying rows owned by
 *       a SECOND tenant (cross-tenant isolation batteries).</li>
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
     * {@code tenantId} (session scope). Seeds/reads owned by a second tenant
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
     * Run the action with connections handed out WITHOUT any
     * {@code app.tenant_id} binding — the raw fail-closed surface. Used by
     * tests that prove the missing-context rejection itself.
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
     * G1 P0-B: the trusted context is the SIGNED TENANT-CONTEXT BIND protocol
     * (V20261124_1/V20261124_2) — the raw session-scoped GUC write is retired.
     * The bind is transaction-scoped, so the fixture wraps every connection in
     * a lazy-binding proxy: on the FIRST statement creation inside an active
     * transaction (autoCommit=false), the proxy issues the slot + bind protocol
     * with the fixture signer — mirroring the production
     * {@code TenantRlsConnectionHandler}. Autocommit acquisitions stay
     * unbound: the fail-closed policies yield zero rows (which is exactly what
     * the seed/verify helpers expect — tenant-visible work always runs inside
     * a transaction in this fixture family).
     */
    private Connection bind(Connection connection) throws SQLException {
        if (Boolean.TRUE.equals(SUPPRESS.get())) {
            return connection;
        }
        UUID tenant = OVERRIDE.get() != null ? OVERRIDE.get() : defaultTenant;
        if (tenant == null) {
            return connection;
        }
        return (Connection) java.lang.reflect.Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[] { Connection.class },
                new LazyBindingHandler(connection, tenant));
    }

    /**
     * Lazy signed-bind proxy: binds the tenant context once per transaction,
     * before the first statement reaches the engine.
     */
    /**
     * Autocommit fixture path: suspends auto-commit for exactly one statement
     * so the signed bind and the statement share one transaction. Test-support
     * only — mirrors what the production transaction manager guarantees.
     */
    private static final class AutoCommitStatementHandler implements java.lang.reflect.InvocationHandler {

        private final java.sql.Statement delegate;
        private final LazyBindingHandler owner;

        AutoCommitStatementHandler(java.sql.Statement delegate, LazyBindingHandler owner) {
            this.delegate = delegate;
            this.owner = owner;
        }

        @Override
        public Object invoke(Object proxy, java.lang.reflect.Method method, Object[] args) throws Throwable {
            String name = method.getName();
            boolean executes = "execute".equals(name) || "executeQuery".equals(name)
                    || "executeUpdate".equals(name) || "executeLargeUpdate".equals(name)
                    || "executeBatch".equals(name) || "executeLargeBatch".equals(name);
            if (!executes) {
                try {
                    return method.invoke(delegate, args);
                } catch (java.lang.reflect.InvocationTargetException e) {
                    throw e.getCause();
                }
            }
            java.sql.Connection connection = delegate.getConnection();
            boolean wasAutoCommit = connection.getAutoCommit();
            if (!wasAutoCommit) {
                // Inside a transaction: the connection-level bind already
                // applied — just run the statement.
                try {
                    return method.invoke(delegate, args);
                } catch (java.lang.reflect.InvocationTargetException e) {
                    throw e.getCause();
                }
            }
            connection.setAutoCommit(false);
            try {
                owner.bindSignedNow(connection);
                try {
                    Object result = method.invoke(delegate, args);
                    connection.commit();
                    return result;
                } catch (java.lang.reflect.InvocationTargetException statementFailure) {
                    // Statement failed (constraint violation etc.): roll back
                    // the suspended transaction and rethrow the ORIGINAL
                    // cause so caller-side exception contracts hold.
                    try {
                        connection.rollback();
                    } catch (SQLException suppressed) {
                        // preserve the statement failure
                    }
                    throw statementFailure.getCause();
                }
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }


    private static final class LazyBindingHandler implements java.lang.reflect.InvocationHandler {

        private final Connection delegate;
        private final UUID tenant;
        private final com.sanad.platform.security.rls.TenantContextSigner signer =
                com.sanad.platform.test.TenantCtxTestSupport.fixtureSigner();
        private boolean bound = false;

        LazyBindingHandler(Connection delegate, UUID tenant) {
            this.delegate = delegate;
            this.tenant = tenant;
        }

        @Override
        public Object invoke(Object proxy, java.lang.reflect.Method method, Object[] args) throws Throwable {
            String name = method.getName();
            if ("createStatement".equals(name) || "prepareStatement".equals(name)
                    || "prepareCall".equals(name)) {
                if (!bound && !delegate.getAutoCommit()) {
                    bindSigned();
                    bound = true;
                }
                Object stmt = null;
                try {
                    stmt = method.invoke(delegate, args);
                } catch (java.lang.reflect.InvocationTargetException e) {
                    throw e.getCause();
                }
                if (stmt instanceof java.sql.Statement real && delegate.getAutoCommit()) {
                    // Autocommit fixture path: every statement is its own
                    // transaction — wrap it so slot + bind + the statement
                    // share one transaction (auto-commit suspended for the
                    // duration).
                    return java.lang.reflect.Proxy.newProxyInstance(
                            real.getClass().getClassLoader(),
                            new Class<?>[] { real.getClass().getInterfaces().length > 0
                                    ? real.getClass().getInterfaces()[0]
                                    : java.sql.Statement.class },
                            new AutoCommitStatementHandler(real, this));
                }
                return stmt;
            }
            if ("commit".equals(name) || "rollback".equals(name)
                    || "close".equals(name) || "setAutoCommit".equals(name)) {
                bound = false;
            }
            try {
                return method.invoke(delegate, args);
            } catch (java.lang.reflect.InvocationTargetException e) {
                throw e.getCause();
            }
        }

        private void bindSigned() throws SQLException {
            bindSignedNow(delegate);
        }

        private void bindSignedNow(Connection target) throws SQLException {
            String txid;
            int pid;
            try (var stmt = target.createStatement();
                 var rs = stmt.executeQuery(
                         "SELECT s.ctx_txid, s.ctx_pid FROM public.app_tenant_ctx_slot() s")) {
                rs.next();
                txid = rs.getString(1);
                pid = rs.getInt(2);
            }
            String mac = signer.macFor(tenant, txid, pid);
            try (var ps = target.prepareStatement("SELECT public.app_bind_tenant_ctx(?::uuid, ?)")) {
                ps.setString(1, tenant.toString());
                ps.setString(2, mac);
                try (var rs = ps.executeQuery()) {
                    rs.next();
                }
            }
        }
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
