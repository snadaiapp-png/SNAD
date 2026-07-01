package com.sanad.platform.security.tenant;

import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;

/**
 * Stage 04A.3 §5 — Manual RLS binding for services that need explicit control.
 *
 * <p>Uses {@link TenantRlsProperties#isEnabled()} to determine whether RLS
 * binding is active (PostgreSQL) or no-op (H2 local). No exception-message
 * database detection.</p>
 */
@Component
public class TenantRlsBinder {

    private static final Logger log = LoggerFactory.getLogger(TenantRlsBinder.class);

    private static final String SET_CONFIG_SQL =
            "SELECT set_config('app.current_tenant_id', ?, true)";

    private final TenantContextProvider contextProvider;
    private final EntityManager entityManager;
    private final TenantRlsProperties rlsProperties;

    public TenantRlsBinder(TenantContextProvider contextProvider,
                            EntityManager entityManager,
                            TenantRlsProperties rlsProperties) {
        this.contextProvider = contextProvider;
        this.entityManager = entityManager;
        this.rlsProperties = rlsProperties;
    }

    public void bindTenantToCurrentTransaction() {
        if (!rlsProperties.isEnabled()) {
            // H2 local profile — no-op
            return;
        }

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
            var session = entityManager.unwrap(org.hibernate.Session.class);
            session.doWork(connection -> {
                try (var stmt = connection.prepareStatement(SET_CONFIG_SQL)) {
                    stmt.setString(1, tenantId.toString());
                    stmt.execute();
                }
            });
            log.debug("RLS tenant bound: requestId={} tenantId={}",
                    context.requestId(), tenantId);
        } catch (Exception e) {
            throw new TenantRlsBindingException(
                "Failed to bind tenant context to PostgreSQL transaction", e);
        }
    }
}
