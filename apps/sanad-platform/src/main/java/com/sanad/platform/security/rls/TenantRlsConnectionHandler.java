package com.sanad.platform.security.rls;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Dynamic-proxy invocation handler that lazily applies
 * {@code SET LOCAL app.tenant_id = '<uuid>'} to the underlying physical
 * connection the first time a statement is created within a transaction.
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
 * <p>If either condition is false the proxy is a transparent pass-through,
 * preserving full backward compatibility for non-transactional reads and
 * background jobs that intentionally operate across tenants.</p>
 */
final class TenantRlsConnectionHandler implements InvocationHandler {

    private final Connection delegate;
    private boolean tenantApplied = false;

    TenantRlsConnectionHandler(Connection delegate) {
        this.delegate = delegate;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        String name = method.getName();
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
        if (tenantApplied) {
            return;
        }
        // Only apply within an explicit transaction — SET LOCAL has no effect
        // (and would error) in autocommit mode.
        if (delegate.getAutoCommit()) {
            return;
        }
        UUID tenantId = currentTenantId();
        if (tenantId == null) {
            return;
        }
        try (var stmt = delegate.createStatement()) {
            // Parameterised SET LOCAL is not supported by PostgreSQL for GUC
            // assignment, but the value is a well-formed UUID read directly
            // from the validated JWT via the security context — never user-
            // supplied raw input at this layer.
            stmt.execute("SET LOCAL app.tenant_id = '" + tenantId.toString() + "'");
        }
        tenantApplied = true;
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
