package com.sanad.platform.audit.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Stage 05 §22 — Read-only response DTO for audit events.
 *
 * <p>Returned by {@code GET /api/v1/audit-events} and
 * {@code GET /api/v1/audit-events/{id}}. Never contains raw secrets —
 * all state-change JSON was redacted at write time by
 * {@link com.sanad.platform.audit.service.AuditRedactionService}.</p>
 */
public record AuditEventResponse(
        UUID id,
        UUID tenantId,
        String actorType,
        UUID actorUserId,
        String actorService,
        String actorDisplayName,
        String sessionId,
        String jwtId,
        String requestId,
        String correlationId,
        String traceId,
        String action,
        String category,
        String resourceType,
        String resourceId,
        String operation,
        String outcome,
        Integer httpStatus,
        String errorCode,
        String failureReason,
        String sourceIp,
        String userAgent,
        String channel,
        String beforeState,
        String afterState,
        String changedFields,
        String metadata,
        String previousHash,
        String eventHash,
        String hashAlgorithm,
        Integer schemaVersion,
        Instant occurredAt,
        Instant recordedAt,
        Instant createdAt
) {}
