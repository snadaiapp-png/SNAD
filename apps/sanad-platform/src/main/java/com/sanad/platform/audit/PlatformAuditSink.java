package com.sanad.platform.audit;

import java.time.Instant;
import java.util.UUID;

/**
 * Platform audit sink contract.
 *
 * <p>Audit events are NOT domain events. This contract is a transport-neutral
 * abstraction. The existing PlatformAuditWriter is adapted via
 * ExistingPlatformAuditSinkAdapter.</p>
 *
 * <p>Never receive raw PII (National ID, passport, bank secret, crypto key,
 * credentials) in audit records.</p>
 */
public interface PlatformAuditSink {

    void accept(AuditSinkRecord record);

    record AuditSinkRecord(
            UUID tenantId,
            UUID organizationId,
            UUID actorUserId,
            String action,
            String resourceType,
            UUID resourceId,
            Instant occurredAt,
            String correlationId,
            String result,
            String sanitizedDetails
    ) {}
}
