package com.sanad.platform.crm.integration.infrastructure;

import com.sanad.platform.crm.integration.domain.AuditPort;
import com.sanad.platform.admin.service.PlatformAuditService;
import com.sanad.platform.admin.service.PlatformAuditWriter;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.UUID;

/**
 * CRM AuditPort adapter that delegates to the central PlatformAuditWriter.
 * Uses the correct platform_audit_logs schema.
 * No synthetic Authentication — calls PlatformAuditWriter directly.
 */
@Component
public class JdbcAuditAdapter implements AuditPort {

    private final PlatformAuditWriter auditWriter;

    public JdbcAuditAdapter(PlatformAuditWriter auditWriter) {
        this.auditWriter = auditWriter;
    }

    @Override
    public void record(UUID tenantId, UUID actorId, String action, String entityType, UUID entityId,
                       AuditChange change, Instant timestamp) {
        auditWriter.writeSuccess(
                tenantId,
                actorId,
                tenantId,
                action,
                entityType,
                entityId == null ? null : entityId.toString(),
                null,
                change.beforeState(),
                change.afterState(),
                null,
                timestamp
        );
    }
}
