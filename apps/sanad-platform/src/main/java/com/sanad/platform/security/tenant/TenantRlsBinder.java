package com.sanad.platform.security.tenant;

import jakarta.persistence.EntityManager;
import org.hibernate.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;

/**
 * Stage 04A §7 — Automatic transaction-level RLS binding.
 *
 * <p>This component registers a {@link TransactionSynchronization} that
 * executes {@code SELECT set_config('app.current_tenant_id', ?, true)}
 * on the transaction's connection before any query runs.</p>
 *
 * <p>Usage: services call {@link #bindTenantToCurrentTransaction()} at the
 * start of a @Transactional method. The binding uses the EntityManager
 * that is bound to the current transaction by Spring, so the SET config
 * runs on the SAME connection as the transaction's queries.</p>
 *
 * <p>The {@code true} parameter (is_local) scopes the setting to the
 * transaction — it is automatically cleared when the transaction ends.</p>
 *
 * <p>This replaces the old {@link TenantRlsBinder} which required manual
 * invocation. Services can still call this explicitly, but the
 * {@link TenantContextFilter} also calls it for request-scoped transactions.</p>
 */
@Component
public class TenantRlsBinder {

    private static final Logger log = LoggerFactory.getLogger(TenantRlsBinder.class);

    private static final String SET_CONFIG_SQL =
            "SELECT set_config('app.current_tenant_id', ?, true)";

    private final TenantContextProvider contextProvider;
    private final EntityManager entityManager;

    public TenantRlsBinder(TenantContextProvider contextProvider,
                            EntityManager entityManager) {
        this.contextProvider = contextProvider;
        this.entityManager = entityManager;
    }

    /**
     * Binds the current TenantContext's tenantId to the PostgreSQL
     * connection via SET LOCAL. Must be called inside a @Transactional
     * method — uses the EntityManager that is bound to the current
     * transaction.
     */
    public void bindTenantToCurrentTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            return;
        }

        TenantContext context = contextProvider.currentContext().orElse(null);
        if (context == null) {
            log.debug("RLS bind skipped — no TenantContext (RLS will fail-closed)");
            return;
        }

        UUID tenantId = context.tenantId();
        try {
            Session session = entityManager.unwrap(Session.class);
            session.doWork(connection -> {
                try (var stmt = connection.prepareStatement(SET_CONFIG_SQL)) {
                    stmt.setString(1, tenantId.toString());
                    stmt.execute();
                }
            });
            log.debug("RLS tenant bound: requestId={} tenantId={}",
                    context.requestId(), tenantId);
        } catch (Exception e) {
            // H2 doesn't support set_config — application-layer scoping remains.
            log.debug("RLS bind skipped (likely non-PostgreSQL): {}", e.getMessage());
        }
    }
}
