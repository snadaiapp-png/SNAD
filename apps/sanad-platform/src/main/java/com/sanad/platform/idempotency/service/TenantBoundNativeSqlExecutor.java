package com.sanad.platform.idempotency.service;

import com.sanad.platform.security.tenant.TenantContext;
import com.sanad.platform.security.tenant.TenantContextProvider;
import jakarta.persistence.EntityManager;
import org.hibernate.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

/**
 * Stage 05A.2.7 §7.1 — Central executor for tenant-bound native SQL.
 *
 * <p>Guarantees that native SQL executes on the same connection that
 * has {@code app.current_tenant_id} set, within an active transaction.</p>
 *
 * <p>Flow:</p>
 * <ol>
 *   <li>Verify active transaction</li>
 *   <li>Verify TenantContext</li>
 *   <li>Verify requested tenantId matches TenantContext</li>
 *   <li>Obtain EntityManager transaction connection</li>
 *   <li>Verify autoCommit = false</li>
 *   <li>set_config on that same connection</li>
 *   <li>Verify current_setting matches</li>
 *   <li>Execute native SQL</li>
 * </ol>
 */
@Component
public class TenantBoundNativeSqlExecutor {

    private static final Logger log = LoggerFactory.getLogger(TenantBoundNativeSqlExecutor.class);

    private final EntityManager entityManager;
    private final TenantContextProvider contextProvider;

    public TenantBoundNativeSqlExecutor(EntityManager entityManager,
                                          TenantContextProvider contextProvider) {
        this.entityManager = entityManager;
        this.contextProvider = contextProvider;
    }

    /**
     * Executes native SQL on the transaction-bound connection with
     * app.current_tenant_id explicitly set and verified.
     *
     * @param tenantId the tenant ID to bind (must match TenantContext)
     * @param work the SQL work to execute
     * @param <T> the return type
     * @return the result of the work
     * @throws IllegalStateException if no active transaction, autoCommit is true,
     *         or RLS binding verification fails
     */
    public <T> T execute(UUID tenantId, SqlWork<T> work) {
        // 1. Verify active transaction
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException(
                    "Tenant-bound native SQL requires an active transaction");
        }

        // 2. Verify TenantContext
        TenantContext context = contextProvider.currentContext()
                .orElseThrow(() -> new IllegalStateException(
                        "Tenant-bound native SQL requires a TenantContext"));

        // 3. Verify requested tenantId matches TenantContext
        if (!tenantId.equals(context.tenantId())) {
            throw new IllegalStateException(
                    "Tenant-bound native SQL: requested tenantId " + tenantId
                    + " does not match TenantContext tenantId " + context.tenantId());
        }

        // 4. Obtain EntityManager transaction connection
        Session session = entityManager.unwrap(Session.class);

        // doReturningWork ensures we get the connection bound to this transaction
        return session.doReturningWork(connection -> {
            // 5. Verify autoCommit = false
            if (connection.getAutoCommit()) {
                throw new IllegalStateException(
                        "Tenant-bound native SQL cannot run with autoCommit=true");
            }

            // 6. set_config on that same connection
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT set_config('app.current_tenant_id', ?, true)")) {
                ps.setString(1, tenantId.toString());
                ps.execute();
            }

            // 7. Verify current_setting matches
            try (PreparedStatement verify = connection.prepareStatement(
                    "SELECT NULLIF(current_setting('app.current_tenant_id', true), '')::uuid")) {
                try (ResultSet rs = verify.executeQuery()) {
                    if (!rs.next()) {
                        throw new IllegalStateException(
                                "Tenant-bound native SQL: RLS verification failed — no result");
                    }
                    UUID verified = (UUID) rs.getObject(1);
                    if (!tenantId.equals(verified)) {
                        throw new IllegalStateException(
                                "Tenant-bound native SQL: RLS verification failed — expected "
                                + tenantId + " but got " + verified);
                    }
                }
            }

            // 8. Execute native SQL
            return work.execute(connection);
        });
    }
}
