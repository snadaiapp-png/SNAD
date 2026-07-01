package com.sanad.platform.security.tenant;

/**
 * Stage 04A.2 §11 — Thrown when RLS binding fails on PostgreSQL.
 *
 * <p>This is a runtime exception that causes the transaction to fail
 * immediately. It is NOT caught by a generic catch-all — the transaction
 * rolls back and the error propagates to the caller.</p>
 */
public class TenantRlsBindingException extends RuntimeException {

    public TenantRlsBindingException(String message) {
        super(message);
    }

    public TenantRlsBindingException(String message, Throwable cause) {
        super(message, cause);
    }
}
