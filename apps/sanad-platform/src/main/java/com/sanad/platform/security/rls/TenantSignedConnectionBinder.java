package com.sanad.platform.security.rls;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

/**
 * Signed tenant binding for an already-open, caller-managed JDBC transaction.
 *
 * <p>The caller must have authorized the tenant independently. A valid MAC is
 * transaction scoping, NOT authorization. The connection must be in a single
 * explicit transaction (autoCommit=false). No raw tenant GUC is written here.
 *
 * <p>The companion connection proxy clears the thread-scoped marker on
 * commit/rollback/close. Manually managed unwrapped connections MUST call
 * {@link #clearThreadBinding()} in a finally block after finishing the
 * transaction, including rollback paths.
 */
public final class TenantSignedConnectionBinder {

    private TenantSignedConnectionBinder() { }

    public static void bind(Connection connection, UUID authorizedTenant) throws SQLException {
        if (connection == null || authorizedTenant == null) {
            throw new IllegalArgumentException("Connection and authorized tenant are required");
        }
        // An earlier manual connection may have been used by the same pool
        // thread. A new bind never inherits its marker.
        TenantRlsSignedBinding.clear();
        if (connection.getAutoCommit()) {
            throw new SQLException("Signed tenant binding requires an explicit JDBC transaction");
        }
        TenantContextSigner signer = TenantRlsRuntimePolicy.current().signer();
        if (signer == null) {
            throw new SQLException("Tenant context signing key unavailable; refusing unsigned binding");
        }

        TenantRlsSignedBinding.beginProtocol();
        try {
            String txid;
            int pid;
            try (Statement statement = connection.createStatement();
                 ResultSet result = statement.executeQuery(
                         "SELECT ctx_txid, ctx_pid FROM public.app_tenant_ctx_slot()")) {
                if (!result.next()) {
                    throw new SQLException("Tenant context slot unavailable");
                }
                txid = result.getString("ctx_txid");
                pid = result.getInt("ctx_pid");
            }
            String mac = signer.macHex(authorizedTenant, txid, pid);
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT public.app_bind_tenant_ctx(?::uuid, ?)")) {
                statement.setString(1, authorizedTenant.toString());
                statement.setString(2, mac);
                try (ResultSet result = statement.executeQuery()) {
                    if (!result.next() || !result.getBoolean(1)) {
                        throw new SQLException("Database rejected signed tenant binding");
                    }
                }
            }
            try (Statement statement = connection.createStatement();
                 ResultSet result = statement.executeQuery(
                         "SELECT public.app_tenant_ctx_text()")) {
                if (!result.next() || !authorizedTenant.toString().equals(result.getString(1))) {
                    throw new SQLException("Signed tenant context validation failed");
                }
            }
            TenantRlsSignedBinding.complete(authorizedTenant);
        } catch (SQLException | RuntimeException error) {
            TenantRlsSignedBinding.clear();
            throw error;
        }
    }

    /** Call after a manual transaction if no RLS connection proxy owns cleanup. */
    public static void clearThreadBinding() {
        TenantRlsSignedBinding.clear();
    }
}
