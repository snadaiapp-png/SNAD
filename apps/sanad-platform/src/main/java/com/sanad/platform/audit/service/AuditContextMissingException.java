package com.sanad.platform.audit.service;

/**
 * Stage 05A.1 §12 — Thrown when an audit event cannot be recorded
 * because the required verified tenant context is missing.
 *
 * <p>This is a fail-closed exception: the audit service NEVER silently
 * skips recording or returns null. If a tenant-scoped action has no
 * verified tenant, the entire business transaction must fail.</p>
 */
public class AuditContextMissingException extends RuntimeException {

    public AuditContextMissingException(String message) {
        super(message);
    }

    public AuditContextMissingException(String message, Throwable cause) {
        super(message, cause);
    }
}
