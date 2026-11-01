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
 * fail-closed RLS contract (V20261029_1) on every physical connection it
 * hands out, by issuing {@code SELECT set_config('app.tenant_id', ?, false)}
 * (session-scoped) before returning the connection.
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
 *   <li>Session scope ({@code is_local=false}) so autocommit PostgreSQL
 *       Direct fixtures (driver scans, seed/verify SQL) observe the bound
 *       tenant. The value dies with the connection: the wrapped
 *       non-pooling DataSource opens a fresh physical connection per
 *       acquisition, so nothing leaks across tenants or tests.</li>
 *   <li>Transaction-local overrides inside {@code TransactionTemplate} (the
 *       {@code set_config(..., true)} pattern used by the converted CRM
 *       fixtures) still win inside their transaction — the session value
 *       only fills the autocommit gaps.</li>
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

    private Connection bind(Connection connection) throws SQLException {
        if (Boolean.TRUE.equals(SUPPRESS.get())) {
            return connection;
        }
        UUID tenant = OVERRIDE.get() != null ? OVERRIDE.get() : defaultTenant;
        if (tenant == null) {
            return connection;
        }
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT set_config('app.tenant_id', ?, false)")) {
            ps.setString(1, tenant.toString());
            ps.execute();
        }
        return connection;
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
