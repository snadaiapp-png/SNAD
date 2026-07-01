package com.sanad.platform.security.tenant;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Stage 04A §7 — Transaction synchronization that auto-binds RLS tenant context.
 *
 * <p>Registered as a bean, this synchronization is activated by
 * {@link #registerIfApplicable()} which is called from a
 * {@link org.springframework.transaction.interceptor.TransactionInterceptor}
 * aspect or directly from services.</p>
 *
 * <p>The synchronization executes {@link TenantRlsBinder#bindTenantToCurrentTransaction()}
 * at transaction begin (before any query runs).</p>
 */
@Component
public class TenantRlsTransactionSynchronization implements TransactionSynchronization {

    private final TenantRlsBinder binder;

    public TenantRlsTransactionSynchronization(TenantRlsBinder binder) {
        this.binder = binder;
    }

    /**
     * Register this synchronization for the current transaction if a
     * TenantContext is available. Called by services or a
     * TransactionInterceptor advice.
     */
    public void registerIfApplicable() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            // Check if already registered to avoid duplicates
            for (TransactionSynchronization sync : TransactionSynchronizationManager.getSynchronizations()) {
                if (sync instanceof TenantRlsTransactionSynchronization) {
                    return; // Already registered
                }
            }
            TransactionSynchronizationManager.registerSynchronization(this);
            // Bind immediately (beforeCompletion runs before commit, but we need
            // the binding at the START of the transaction, not the end).
            binder.bindTenantToCurrentTransaction();
        }
    }

    @Override
    public void beforeCommit(boolean readOnly) {
        // RLS binding happens at registration time (transaction begin),
        // not at commit. This method is a no-op.
    }

    @Override
    public void afterCompletion(int status) {
        // SET LOCAL is automatically cleared by PostgreSQL when the
        // transaction ends. No cleanup needed here.
    }
}
