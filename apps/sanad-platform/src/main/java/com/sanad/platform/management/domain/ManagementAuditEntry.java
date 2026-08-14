package com.sanad.platform.management.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Management Audit Entry — an immutable record of a management operation.
 *
 * <p>Append-only: records are never updated or deleted. Every management
 * operation (decision state change, risk reassessment, issue resolution, etc.)
 * creates an audit entry here.
 *
 * <p>This provides the governance/provenance layer required for:
 * <ul>
 *   <li>Regulatory compliance</li>
 *   <li>Segregation of duties verification</li>
 *   <li>Executive accountability</li>
 *   <li>Post-incident review</li>
 * </ul>
 */
public record ManagementAuditEntry(
        UUID id,
        UUID tenantId,
        UUID actorUserId,
        EntityType entityType,
        UUID entityId,
        Action action,
        String fromState,
        String toState,
        String changes,  // JSON diff (before/after)
        UUID correlationId,
        Instant createdAt
) {
    public enum EntityType {
        DECISION, RISK, ISSUE, ESCALATION, OBJECTIVE, KPI, INITIATIVE
    }

    public enum Action {
        CREATE, UPDATE, STATE_CHANGE, APPROVE, REJECT, DELETE, ASSIGN, MEASURE
    }

    public static ManagementAuditEntry create(
            UUID tenantId, UUID actorUserId,
            EntityType entityType, UUID entityId,
            Action action, String fromState, String toState,
            String changes, UUID correlationId) {
        return new ManagementAuditEntry(
                UUID.randomUUID(), tenantId, actorUserId,
                entityType, entityId, action, fromState, toState,
                changes, correlationId, Instant.now()
        );
    }
}
