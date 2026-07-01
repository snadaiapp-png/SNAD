package com.sanad.platform.security.tenant;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.orm.jpa.EntityManagerHolder;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.sql.PreparedStatement;
import java.util.UUID;

/**
 * Stage 04A.3 §5 — Automatic transaction-level RLS binding.
 *
 * <p>Uses {@link TenantRlsProperties#isEnabled()} to determine whether
 * RLS binding is active (PostgreSQL) or no-op (H2 local). No exception-
 * message database detection.</p>
 *
 * <p>When RLS is enabled and any step fails, throws
 * {@link TenantRlsBindingException} — no silent return, no catch-all.</p>
 */
public class TenantAwareJpaTransactionManager extends JpaTransactionManager {

    private static final Logger log = LoggerFactory.getLogger(TenantAwareJpaTransactionManager.class);

    private static final String SET_CONFIG_SQL =
            "SELECT set_config('app.current_tenant_id', ?, true)";

    private final TenantContextProvider contextProvider;
    private final TenantRlsProperties rlsProperties;

    public TenantAwareJpaTransactionManager(EntityManagerFactory emf,
                                             TenantContextProvider contextProvider,
                                             TenantRlsProperties rlsProperties) {
        super(emf);
        this.contextProvider = contextProvider;
        this.rlsProperties = rlsProperties;
    }

    @Override
    protected void doBegin(Object transaction, TransactionDefinition definition) {
        super.doBegin(transaction, definition);

        // If RLS is not enabled (H2 local profile), skip binding entirely.
        if (!rlsProperties.isEnabled()) {
            return;
        }

        // RLS is enabled (PostgreSQL) — bind or fail.
        EntityManagerFactory emf = getEntityManagerFactory();
        Object resource = TransactionSynchronizationManager.getResource(emf);

        if (resource == null) {
            throw new TenantRlsBindingException(
                "Transaction resource missing — cannot bind RLS tenant context");
        }

        if (!(resource instanceof EntityManagerHolder holder)) {
            throw new TenantRlsBindingException(
                "EntityManagerHolder missing — cannot bind RLS tenant context");
        }

        EntityManager em = holder.getEntityManager();
        if (em == null) {
            throw new TenantRlsBindingException(
                "Bound EntityManager is null — cannot bind RLS tenant context");
        }

        TenantContext context = contextProvider.currentContext().orElse(null);
        if (context == null) {
            // Missing TenantContext with RLS enabled — fail-closed.
            // The transaction proceeds but RLS will return 0 rows for tenant-owned tables.
            log.debug("Transaction begun without TenantContext — RLS will fail-closed");
            return;
        }

        UUID tenantId = context.tenantId();

        try {
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
            throw new TenantRlsBindingException(
                "set_config failed during RLS binding on PostgreSQL", e);
        }
    }
}
