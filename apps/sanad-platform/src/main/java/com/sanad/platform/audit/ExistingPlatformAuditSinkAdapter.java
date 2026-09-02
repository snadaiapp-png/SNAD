package com.sanad.platform.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Adapts the existing Platform audit logging to the PlatformAuditSink contract.
 *
 * <p>This adapter NEVER receives raw PII secrets. Callers must redact
 * sensitive fields before constructing an AuditSinkRecord.</p>
 */
@Component
public class ExistingPlatformAuditSinkAdapter implements PlatformAuditSink {

    private static final Logger log = LoggerFactory.getLogger("SANAD-AUDIT");

    @Override
    public void accept(AuditSinkRecord record) {
        log.info(
                "AUDIT tenant={} org={} actor={} action={} resource={}/{} result={} correlation={}",
                record.tenantId(),
                record.organizationId(),
                record.actorUserId(),
                record.action(),
                record.resourceType(),
                record.resourceId(),
                record.result(),
                record.correlationId()
        );
    }
}
