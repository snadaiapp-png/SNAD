package com.sanad.platform.security.rls;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Dynamic-proxy invocation handler that lazily applies transaction-local tenant
 * scope before the first statement is created in a transaction.
 */
final class TenantRlsConnectionHandler implements InvocationHandler {

    private final Connection delegate;
    private UUID tenantAppliedFor;

    TenantRlsConnectionHandler(Connection delegate) {
        this.delegate = delegate;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        String name = method.getName();
        if (isStatementCreation(name)) {
            ensureTenantContext();
        }
        try {
            return method.invoke(delegate, args);
        } catch (InvocationTargetException exception) {
            // Preserve JDBC's declared exception contract through the proxy.
            throw exception.getCause();
        } finally {
            // SET LOCAL is scoped to a transaction. A pooled connection may be
            // reused for a different tenant after any transaction boundary.
            if ("commit".equals(name) || "rollback".equals(name)
                    || "close".equals(name) || "setAutoCommit".equals(name)) {
                tenantAppliedFor = null;
            }
        }
    }

    private boolean isStatementCreation(String methodName) {
        return "createStatement".equals(methodName)
                || "prepareStatement".equals(methodName)
                || "prepareCall".equals(methodName)
                || "nativeSQL".equals(methodName);
    }

    private void ensureTenantContext() throws SQLException {
        // SET LOCAL is only meaningful inside an explicit transaction.
        if (delegate.getAutoCommit()) {
            return;
        }

        UUID tenantId = currentTenantId();
        if (tenantAppliedFor != null) {
            if (!tenantAppliedFor.equals(tenantId)) {
                throw new SQLException("Tenant identity changed inside an active RLS transaction");
            }
            return;
        }
        if (tenantId == null) {
            return;
        }

        // UUID.fromString validates the identity before it reaches SQL. JDBC
        // does not support a bind parameter in SET LOCAL's assignment syntax.
        try (var stmt = delegate.createStatement()) {
            stmt.execute("SET LOCAL app.tenant_id = '" + tenantId + "'");
        }
        tenantAppliedFor = tenantId;
    }

    private static UUID currentTenantId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()
                || !(auth.getDetails() instanceof java.util.Map<?, ?> details)) {
            return null;
        }
        Object raw = details.get("tenant_id");
        if (raw == null) {
            return null;
        }
        try {
            return UUID.fromString(raw.toString());
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
