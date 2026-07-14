package com.sanad.platform.crm.integration.infrastructure;

import com.sanad.platform.crm.integration.domain.AuditPort;
import com.sanad.platform.crm.integration.domain.CorrelationContextPort;
import com.sanad.platform.admin.service.PlatformAuditWriter;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.UUID;

@Component
public class JdbcAuditAdapter implements AuditPort {

    private final PlatformAuditWriter auditWriter;
    private final CorrelationContextPort correlationContext;

    public JdbcAuditAdapter(PlatformAuditWriter auditWriter, CorrelationContextPort correlationContext) {
        this.auditWriter = auditWriter;
        this.correlationContext = correlationContext;
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
                correlationContext.currentCorrelationId(),
                timestamp
        );
    }
}
