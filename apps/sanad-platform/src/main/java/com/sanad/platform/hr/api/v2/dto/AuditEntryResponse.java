package com.sanad.platform.hr.api.v2.dto;

import com.sanad.platform.hr.audit.HrAuditReadService;

import java.time.Instant;
import java.util.UUID;

/**
 * HRM-G0 / WS5 Task 5 — audit ledger projection. State snapshots are
 * deliberately excluded: identifiers/classification/metadata only.
 */
public record AuditEntryResponse(
        UUID auditId,
        String action,
        String resourceType,
        UUID resourceId,
        String dataClassification,
        String reason,
        String result,
        UUID actorUserId,
        Instant occurredAt
) {

    public static AuditEntryResponse from(HrAuditReadService.HrAuditEntry entry) {
        return new AuditEntryResponse(entry.auditId(), entry.action(), entry.resourceType(), entry.resourceId(),
                entry.dataClassification(), entry.reason(), entry.result(), entry.actorUserId(), entry.occurredAt());
    }
}
