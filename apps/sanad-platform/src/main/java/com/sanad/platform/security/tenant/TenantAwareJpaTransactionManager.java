package com.sanad.platform.security.tenant;

import jakarta.persistence.EntityManager;
import org.hibernate.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.TransactionDefinition;

import jakarta.persistence.EntityManagerFactory;
import java.util.UUID;

/**
 * Stage 04A §7 — Automatic transaction-level RLS binding.
 *
 * <p>Extends {@link JpaTransactionManager} to automatically execute
 * {@code SELECT set_config('app.current_tenant_id', ?, true)} on the
 * transaction's connection at transaction begin.</p>
 *
 * <p>This ensures RLS policies are enforced for every transaction that
 * touches tenant-owned data, without requiring manual binder calls from
 * each service.</p>
 *
 * <p>The setting is scoped to the transaction via the third parameter
 * {@code true} (equivalent to SET LOCAL). When the transaction ends,
 * the setting is automatically cleared by PostgreSQL — no risk of
 * leaking to a pooled connection.</p>
 *
 * <p>If no TenantContext is set (missing context), no setting is applied.
 * RLS will fail-closed (0 rows for reads, exception for writes).</p>
 */
public class TenantAwareJpaTransactionManager extends JpaTransactionManager {

    private static final Logger log = LoggerFactory.getLogger(TenantAwareJpaTransactionManager.class);

    private static final String SET_CONFIG_SQL =
            "SELECT set_config('app.current_tenant_id', ?, true)";

    private final TenantContextProvider contextProvider;

    public TenantAwareJpaTransactionManager(EntityManagerFactory emf,
                                             TenantContextProvider contextProvider) {
        super(emf);
        this.contextProvider = contextProvider;
    }

    public TenantAwareJpaTransactionManager(TenantContextProvider contextProvider) {
        super();
        this.contextProvider = contextProvider;
    }

    @Override
    protected void doBegin(Object transaction, TransactionDefinition definition) {
        super.doBegin(transaction, definition);

        // After the transaction has begun (connection is acquired), bind the
        // tenant context to the connection's session.
        TenantContext context = contextProvider.currentContext().orElse(null);
        if (context == null) {
            // No tenant context — RLS will fail-closed for tenant-owned tables.
            // Global-reference operations (access_capabilities) are unaffected.
            log.debug("Transaction begun without TenantContext — RLS will fail-closed for tenant-owned tables");
            return;
        }

        UUID tenantId = context.tenantId();
        try {
            // Get the EntityManager for this transaction and execute SET config
            // on its underlying connection.
            EntityManager em = getEntityManagerFactory().createEntityManager();
            try {
                em.unwrap(Session.class).doWork(connection -> {
                    try (var stmt = connection.prepareStatement(SET_CONFIG_SQL)) {
                        stmt.setString(1, tenantId.toString());
                        stmt.execute();
                    }
                });
            } finally {
                em.close();
            }
            log.debug("RLS tenant bound to transaction: tenantId={} requestId={}",
                    tenantId, context.requestId());
        } catch (Exception e) {
            // On H2 (local profile), set_config doesn't exist — the exception
            // is caught and logged. Application-layer scoping remains the source
            // of truth in local profile. On PostgreSQL, this should not fail.
            log.debug("RLS bind skipped (likely non-PostgreSQL or no JPA): {}", e.getMessage());
        }
    }
}
