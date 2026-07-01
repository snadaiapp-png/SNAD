package com.sanad.platform.security.tenant;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.sql.PreparedStatement;
import java.util.UUID;

/**
 * Stage 04A.2 §4 — Automatic transaction-level RLS binding.
 *
 * <p>Extends {@link JpaTransactionManager} to automatically execute
 * {@code SELECT set_config('app.current_tenant_id', ?, true)} on the
 * transaction's OWN connection at transaction begin.</p>
 *
 * <p><strong>Stage 04A.2 fix:</strong> The previous implementation created
 * a NEW EntityManager via {@code createEntityManager()}, which obtained a
 * DIFFERENT connection from the pool. This version uses
 * {@link TransactionSynchronizationManager#getResource(EntityManagerFactory)}
 * to get the EntityManager that is ALREADY bound to the current transaction
 * by {@code super.doBegin()}. This ensures the RLS setting is applied to
 * the SAME connection that will execute all subsequent queries.</p>
 *
 * <p>The setting is scoped to the transaction via the third parameter
 * {@code true} (equivalent to SET LOCAL). When the transaction ends,
 * the setting is automatically cleared by PostgreSQL.</p>
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
        // Step 1: Let JpaTransactionManager begin the transaction and bind
        // the EntityManager (and its connection) to the current thread.
        super.doBegin(transaction, definition);

        // Step 2: Get the EntityManager that is BOUND to this transaction.
        // TransactionSynchronizationManager holds the EntityManagerHolder
        // that super.doBegin() just registered.
        EntityManagerFactory emf = getEntityManagerFactory();
        Object resource = TransactionSynchronizationManager.getResource(emf);

        if (resource == null) {
            // No EntityManager bound — this happens for non-JPA transactions.
            // RLS will fail-closed for tenant-owned tables.
            log.debug("No EntityManager bound to transaction — RLS will fail-closed");
            return;
        }

        TenantContext context = contextProvider.currentContext().orElse(null);
        if (context == null) {
            log.debug("Transaction begun without TenantContext — RLS will fail-closed");
            return;
        }

        UUID tenantId = context.tenantId();

        // Step 3: Execute SET config on the transaction's connection using
        // the BOUND EntityManager. This is the SAME connection that all
        // subsequent repository queries will use.
        try {
            EntityManager em = null;
            if (resource instanceof org.springframework.orm.jpa.EntityManagerHolder holder) {
                em = holder.getEntityManager();
            }

            if (em == null) {
                log.warn("EntityManagerHolder found but EntityManager is null — RLS binding skipped");
                return;
            }

            Session session = em.unwrap(Session.class);
            session.doWork(connection -> {
                try (PreparedStatement stmt = connection.prepareStatement(SET_CONFIG_SQL)) {
                    stmt.setString(1, tenantId.toString());
                    stmt.execute();
                }
            });

            log.debug("RLS tenant bound to transaction connection: tenantId={} requestId={}",
                    tenantId, context.requestId());
        } catch (Exception e) {
            // Stage 04A.2 §11: Do NOT swallow PostgreSQL errors.
            // On H2 (local profile), set_config doesn't exist — we allow
            // this to be caught but only for non-PostgreSQL databases.
            // The static gate verifies this catch is H2-specific.
            String message = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
            if (message.contains("h2") || message.contains("function") && message.contains("set_config")) {
                log.debug("RLS bind skipped (H2 local profile — set_config not supported)");
            } else {
                // PostgreSQL error — fail the transaction immediately
                log.error("RLS binding FAILED on PostgreSQL: {}", e.getMessage(), e);
                throw new TenantRlsBindingException(
                    "Failed to bind tenant context to PostgreSQL transaction", e);
            }
        }
    }
}
