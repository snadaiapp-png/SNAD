package com.sanad.platform.audit.service;

import com.sanad.platform.audit.domain.AuditActorType;
import com.sanad.platform.audit.domain.AuditOutcome;

import java.time.Instant;
import java.util.UUID;

/**
 * Stage 05 §8 — Context record passed to {@link AuditService#record}.
 *
 * <p>Carries the action, resource, outcome, and optional state-change
 * payloads for an audited operation. Actor, tenant, and request
 * identity are NOT taken from this record — they are extracted from
 * verified sources (TenantContext, MDC, HTTP request) inside the
 * service.</p>
 *
 * <p>The only fields that callers MUST set are {@code action},
 * {@code resourceType}, and {@code operation}. All others are
 * optional.</p>
 */
public record AuditContext(
        UUID tenantId,
        AuditActorType actorType,
        UUID actorUserId,
        String actorService,
        String actorDisplayName,
        String correlationId,
        String traceId,
        String action,
        String category,
        String resourceType,
        String resourceId,
        String operation,
        AuditOutcome outcome,
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
        Instant occurredAt
) {
    /**
     * Returns a new builder for constructing an AuditContext.
     */
    public static Builder builder(String action, String resourceType, String operation) {
        return new Builder(action, resourceType, operation);
    }

    /**
     * Fluent builder for {@link AuditContext}.
     */
    public static class Builder {
        private UUID tenantId;
        private AuditActorType actorType;
        private UUID actorUserId;
        private String actorService;
        private String actorDisplayName;
        private String correlationId;
        private String traceId;
        private final String action;
        private String category;
        private final String resourceType;
        private String resourceId;
        private final String operation;
        private AuditOutcome outcome;
        private Integer httpStatus;
        private String errorCode;
        private String failureReason;
        private String sourceIp;
        private String userAgent;
        private String channel;
        private String beforeState;
        private String afterState;
        private String changedFields;
        private String metadata;
        private Instant occurredAt;

        public Builder(String action, String resourceType, String operation) {
            this.action = action;
            this.resourceType = resourceType;
            this.operation = operation;
        }

        public Builder tenantId(UUID v) { this.tenantId = v; return this; }
        public Builder actorType(AuditActorType v) { this.actorType = v; return this; }
        public Builder actorUserId(UUID v) { this.actorUserId = v; return this; }
        public Builder actorService(String v) { this.actorService = v; return this; }
        public Builder actorDisplayName(String v) { this.actorDisplayName = v; return this; }
        public Builder correlationId(String v) { this.correlationId = v; return this; }
        public Builder traceId(String v) { this.traceId = v; return this; }
        public Builder category(String v) { this.category = v; return this; }
        public Builder resourceId(String v) { this.resourceId = v; return this; }
        public Builder outcome(AuditOutcome v) { this.outcome = v; return this; }
        public Builder httpStatus(Integer v) { this.httpStatus = v; return this; }
        public Builder errorCode(String v) { this.errorCode = v; return this; }
        public Builder failureReason(String v) { this.failureReason = v; return this; }
        public Builder sourceIp(String v) { this.sourceIp = v; return this; }
        public Builder userAgent(String v) { this.userAgent = v; return this; }
        public Builder channel(String v) { this.channel = v; return this; }
        public Builder beforeState(String v) { this.beforeState = v; return this; }
        public Builder afterState(String v) { this.afterState = v; return this; }
        public Builder changedFields(String v) { this.changedFields = v; return this; }
        public Builder metadata(String v) { this.metadata = v; return this; }
        public Builder occurredAt(Instant v) { this.occurredAt = v; return this; }

        public AuditContext build() {
            return new AuditContext(tenantId, actorType, actorUserId, actorService,
                    actorDisplayName, correlationId, traceId, action, category,
                    resourceType, resourceId, operation, outcome, httpStatus,
                    errorCode, failureReason, sourceIp, userAgent, channel,
                    beforeState, afterState, changedFields, metadata, occurredAt);
        }
    }
}
