package com.sanad.platform.audit.exception;

import java.util.UUID;

/**
 * Stage 05 §22 — Thrown when an audit event is not found within the
 * current tenant scope.
 */
public class AuditEventNotFoundException extends RuntimeException {

    private final UUID eventId;

    public AuditEventNotFoundException(UUID eventId) {
        super("Audit event not found: " + eventId);
        this.eventId = eventId;
    }

    public UUID getEventId() {
        return eventId;
    }
}
