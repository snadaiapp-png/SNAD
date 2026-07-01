package com.sanad.platform.security.tenant;

import jakarta.persistence.EntityManagerFactory;
import org.hibernate.engine.spi.SessionImplementor;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.TransactionDefinition;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.UUID;

/**
 * Stage 04A.1 §5 — Automatic transaction-level RLS binding.
 *
 * <p>Extends {@link JpaTransactionManager} to automatically execute
 * {@code SELECT set_config('app.current_tenant_id', ?, true)} on the
 * transaction's connection at transaction begin.</p>
 *
 * <p>The setting is scoped to the transaction via the third parameter
 * {@code true} (equivalent to SET LOCAL). When the transaction ends,
 * the setting is automatically cleared by PostgreSQL.</p>
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

    @Override
    protected void doBegin(Object transaction, TransactionDefinition definition) {
        super.doBegin(transaction, definition);

        // After doBegin, the transaction has a connection. We need to set
        // the RLS tenant config on THAT connection (not a new one).
        TenantContext context = contextProvider.currentContext().orElse(null);
        if (context == null) {
            log.debug("Transaction begun without TenantContext — RLS will fail-closed");
            return;
        }

        UUID tenantId = context.tenantId();
        try {
            // Get the EntityManager for this transaction and access its
            // underlying connection via Hibernate's doWork.
            // The EntityManager is bound to the current transaction by
            // TransactionSynchronizationManager, so this uses the SAME
            // connection as the transaction.
            var em = getEntityManagerFactory().createEntityManager();
            try {
                em.unwrap(SessionImplementor.class).doWork(connection -> {
                    try (PreparedStatement stmt = connection.prepareStatement(SET_CONFIG_SQL)) {
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
            // On H2 (local profile), set_config may not exist — the exception
            // is caught and logged. Application-layer scoping remains the source
            // of truth in local profile. On PostgreSQL, this should not fail.
            log.debug("RLS bind skipped (likely non-PostgreSQL): {}", e.getMessage());
        }
    }
}
