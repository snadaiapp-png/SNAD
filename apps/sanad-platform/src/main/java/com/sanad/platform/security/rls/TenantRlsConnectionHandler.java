package com.sanad.platform.security.rls;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Dynamic-proxy invocation handler that lazily applies a trusted tenant GUC
 * before SQL executes. Transactional calls use {@code SET LOCAL}; autocommit
 * calls use a session setting that is reset before the connection is returned.
 *
 * <p>The {@code SET LOCAL} scoping ensures the GUC is scoped to the current
 * transaction and automatically resets on commit/rollback — safe for
 * connection-pool reuse.</p>
 *
 * <h3>Activation conditions</h3>
 * <ul>
 *   <li>{@code autoCommit == false} — we are inside a Spring
 *       {@code @Transactional} boundary (the primary CRM data-access path).</li>
 *   <li>A tenant id is present in the {@link SecurityContextHolder}.</li>
 * </ul>
 *
 * <p>When no trusted tenant context exists, the handler installs an empty tenant GUC
 * so policies using {@code nullif(current_setting('app.tenant_id', true), '')}
 * fail closed. Autocommit uses a session GUC
 * that is reset before the pooled connection is returned. A real tenant may
 * replace the sentinel once during bootstrap, but a real tenant cannot switch
 * to another tenant on the same borrowed connection.</p>
 */
final class TenantRlsConnectionHandler implements InvocationHandler {

    private final Connection delegate;
    private UUID appliedTenantId;
    private boolean sessionScoped;
    private static final UUID FAIL_CLOSED_TENANT = new UUID(0L, 0L);

    TenantRlsConnectionHandler(Connection delegate) {
        this.delegate = delegate;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        String name = method.getName();
        // Session-level GUCs are used only for autocommit operations and must
        // be cleared before a pooled physical connection is returned.
        if ("close".equals(name) && sessionScoped) {
            SQLException resetFailure = null;
            try (var stmt = delegate.createStatement()) {
                stmt.execute("RESET app.tenant_id");
            } catch (SQLException e) {
                resetFailure = e;
                try {
                    delegate.abort(Runnable::run);
                } catch (SQLException abortFailure) {
                    e.addSuppressed(abortFailure);
                }
            } finally {
                sessionScoped = false;
                appliedTenantId = null;
            }
            try {
                delegate.close();
            } catch (SQLException closeFailure) {
                if (resetFailure != null) {
                    resetFailure.addSuppressed(closeFailure);
                } else {
                    throw closeFailure;
                }
            }
            if (resetFailure != null) {
                throw resetFailure;
            }
            return null;
        }
        // Intercept statement-creation entry points to guarantee the GUC is
        // set before any SQL reaches the engine.
        if (isStatementCreation(name)) {
            ensureTenantContext();
        }
        return method.invoke(delegate, args);
    }

    private boolean isStatementCreation(String methodName) {
        return "createStatement".equals(methodName)
                || "prepareStatement".equals(methodName)
                || "prepareCall".equals(methodName)
                || "nativeSQL".equals(methodName);
    }

    private void ensureTenantContext() throws SQLException {
        boolean autoCommit = delegate.getAutoCommit();
        UUID requestedTenantId = currentTenantId();
        if (FAIL_CLOSED_TENANT.equals(requestedTenantId)) {
            throw new SQLException("Reserved fail-closed tenant UUID cannot be used as an identity");
        }
        UUID effectiveTenantId = requestedTenantId == null ? FAIL_CLOSED_TENANT : requestedTenantId;
        String tenantSettingValue = requestedTenantId == null ? "" : requestedTenantId.toString();

        if (!autoCommit && sessionScoped) {
            // Remove any prior autocommit scope before transaction-local scope
            // is installed; otherwise the old session value would reappear at
            // commit/rollback.
            try (var stmt = delegate.createStatement()) {
                stmt.execute("RESET app.tenant_id");
            }
            sessionScoped = false;
            appliedTenantId = null;
        }

        if (effectiveTenantId.equals(appliedTenantId)) {
            return;
        }
        if (appliedTenantId != null && !appliedTenantId.equals(FAIL_CLOSED_TENANT)) {
            // Never switch between real tenants on one borrowed connection.
            throw new SQLException("Refusing tenant context switch on an active connection");
        }

        String scopeCommand = autoCommit ? "SET app.tenant_id = '" : "SET LOCAL app.tenant_id = '";
        try (var stmt = delegate.createStatement()) {
            // An empty setting is intentionally not castable to a tenant UUID without
            // nullif() in the policy; approved policies must use nullif() and
            // fail closed when the GUC is absent or empty.
            stmt.execute(scopeCommand + tenantSettingValue + "'");
        }
        appliedTenantId = effectiveTenantId;
        sessionScoped = autoCommit;
    }

    /**
     * Read the tenant id from the Spring security context.
     *
     * <p>Mirrors the extraction logic in {@code SpringTenantContextAdapter}
     * and {@code CrmContractController#requiredTenant} — the tenant id is
     * placed into {@code Authentication.getDetails()} by
     * {@code JwtAuthenticationFilter} after JWT validation.</p>
     */
    private static UUID currentTenantId() {
        UUID explicitScope = TenantRlsExecutionContext.currentTenantId();
        if (explicitScope != null) {
            return explicitScope;
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return null;
        }
        if (!(auth.getDetails() instanceof java.util.Map<?, ?> details)) {
            return null;
        }
        Object raw = details.get("tenant_id");
        if (raw == null) {
            return null;
        }
        try {
            return UUID.fromString(raw.toString());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
