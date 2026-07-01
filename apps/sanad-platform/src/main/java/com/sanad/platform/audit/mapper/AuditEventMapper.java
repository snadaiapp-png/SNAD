package com.sanad.platform.audit.mapper;

import com.sanad.platform.audit.domain.AuditEvent;
import com.sanad.platform.audit.dto.AuditEventResponse;
import org.springframework.stereotype.Component;

/**
 * Stage 05 §22 — Maps {@link AuditEvent} entities to
 * {@link AuditEventResponse} DTOs.
 */
@Component
public class AuditEventMapper {

    public AuditEventResponse toResponse(AuditEvent e) {
        if (e == null) return null;
        return new AuditEventResponse(
                e.getId(),
                e.getTenantId(),
                e.getActorType() != null ? e.getActorType().name() : null,
                e.getActorUserId(),
                e.getActorService(),
                e.getActorDisplayName(),
                e.getSessionId(),
                e.getJwtId(),
                e.getRequestId(),
                e.getCorrelationId(),
                e.getTraceId(),
                e.getAction(),
                e.getCategory(),
                e.getResourceType(),
                e.getResourceId(),
                e.getOperation(),
                e.getOutcome() != null ? e.getOutcome().name() : null,
                e.getHttpStatus(),
                e.getErrorCode(),
                e.getFailureReason(),
                e.getSourceIp(),
                e.getUserAgent(),
                e.getChannel(),
                e.getBeforeState(),
                e.getAfterState(),
                e.getChangedFields(),
                e.getMetadata(),
                e.getPreviousHash(),
                e.getEventHash(),
                e.getHashAlgorithm(),
                e.getSchemaVersion(),
                e.getOccurredAt(),
                e.getRecordedAt(),
                e.getCreatedAt()
        );
    }
}
