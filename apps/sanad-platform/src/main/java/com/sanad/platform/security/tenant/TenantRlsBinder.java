package com.sanad.platform.security.tenant;

import jakarta.persistence.EntityManager;
import org.hibernate.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;

/**
 * Stage 04 §17 — Binds the tenant context to the PostgreSQL session
 * inside each transaction so RLS policies can enforce isolation.
 *
 * <p>Called by services (or a transaction interceptor) at the start of
 * a transactional method. Uses {@code SET LOCAL} so the setting is
 * scoped to the current transaction and is automatically cleared when
 * the transaction ends — no risk of leaking to a pooled connection.</p>
 *
 * <p>If the current database is H2 (local profile), the SET LOCAL is
 * a no-op (H2 ignores unknown session variables). The application-layer
 * tenant scoping remains the source of truth in that profile.</p>
 */
@Component
public class TenantRlsBinder {

    private static final Logger log = LoggerFactory.getLogger(TenantRlsBinder.class);

    private static final String SET_LOCAL_SQL =
            "SET LOCAL app.current_tenant_id = '%s'";

    private final TenantContextProvider contextProvider;
    private final EntityManager entityManager;

    public TenantRlsBinder(TenantContextProvider contextProvider,
                            EntityManager entityManager) {
        this.contextProvider = contextProvider;
        this.entityManager = entityManager;
    }

    /**
     * Sets the {@code app.current_tenant_id} session variable on the
     * current PostgreSQL connection, scoped to the active transaction.
     *
     * <p>Must be called inside a {@code @Transactional} method. If no
     * transaction is active, the call is a no-op (RLS will fail-closed
     * because the setting is missing).</p>
     */
    public void bindTenantToCurrentTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            return;
        }

        TenantContext context = contextProvider.currentContext().orElse(null);
        if (context == null) {
            // No tenant context — RLS will fail-closed (no rows returned).
            // This is the safe default; do NOT set a sentinel value.
            return;
        }

        UUID tenantId = context.tenantId();
        String sql = String.format(SET_LOCAL_SQL, tenantId.toString());

        try {
            Session session = entityManager.unwrap(Session.class);
            session.doWork(connection -> {
                try (var stmt = connection.createStatement()) {
                    stmt.execute(sql);
                }
            });
            log.debug("RLS tenant bound: requestId={} tenantId={}",
                    context.requestId(), tenantId);
        } catch (Exception e) {
            // Likely H2 (which doesn't support SET LOCAL the same way) —
            // application-layer scoping remains the source of truth.
            log.debug("RLS bind skipped (likely non-PostgreSQL): {}", e.getMessage());
        }
    }
}
