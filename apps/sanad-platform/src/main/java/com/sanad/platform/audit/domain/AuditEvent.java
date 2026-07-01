package com.sanad.platform.audit.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Stage 05 §4 — Immutable audit event record.
 *
 * <p>Every auditable action in the platform writes exactly one
 * {@code AuditEvent} row. The table is append-only: UPDATE, DELETE,
 * and TRUNCATE are blocked by PostgreSQL triggers (V23 migration).
 * The runtime role can only INSERT and SELECT (tenant-scoped via RLS).</p>
 *
 * <p>Each event carries:</p>
 * <ul>
 *   <li>Tenant identity (tenantId) — enforced by RLS</li>
 *   <li>Actor identity (actorType, actorUserId, actorService, actorDisplayName)</li>
 *   <li>Session and request identity (sessionId, jwtId, requestId, correlationId, traceId)</li>
 *   <li>Action and resource identity (action, category, resourceType, resourceId, operation)</li>
 *   <li>Outcome (outcome, httpStatus, errorCode, failureReason)</li>
 *   <li>Source (sourceIp, userAgent, channel)</li>
 *   <li>State change (beforeState, afterState, changedFields) — redacted JSON</li>
 *   <li>Metadata (metadata) — redacted JSON</li>
 *   <li>Hash chain (previousHash, eventHash, hashAlgorithm, schemaVersion)</li>
 *   <li>Timestamps (occurredAt, recordedAt, createdAt)</li>
 * </ul>
 *
 * <p>Sensitive data is redacted BEFORE persistence by
 * {@link com.sanad.platform.audit.service.AuditRedactionService}.
 * The raw password, token, secret, or credential is NEVER stored.</p>
 */
@Entity
@Table(name = "audit_events")
@EntityListeners(AuditingEntityListener.class)
public class AuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false, columnDefinition = "uuid")
    private UUID id;

    @NotNull
    @Column(name = "tenant_id", nullable = false, updatable = false, columnDefinition = "uuid")
    private UUID tenantId;

    // === Actor attribution ===

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "actor_type", nullable = false, updatable = false, length = 30)
    private AuditActorType actorType;

    @Column(name = "actor_user_id", updatable = false, columnDefinition = "uuid")
    private UUID actorUserId;

    @Size(max = 200)
    @Column(name = "actor_service", updatable = false, length = 200)
    private String actorService;

    @Size(max = 200)
    @Column(name = "actor_display_name", updatable = false, length = 200)
    private String actorDisplayName;

    // === Session and request identity ===

    @Size(max = 128)
    @Column(name = "session_id", updatable = false, length = 128)
    private String sessionId;

    @Size(max = 128)
    @Column(name = "jwt_id", updatable = false, length = 128)
    private String jwtId;

    @Size(max = 128)
    @Column(name = "request_id", updatable = false, length = 128)
    private String requestId;

    @Size(max = 128)
    @Column(name = "correlation_id", updatable = false, length = 128)
    private String correlationId;

    @Size(max = 128)
    @Column(name = "trace_id", updatable = false, length = 128)
    private String traceId;

    // === Action and resource ===

    @NotBlank
    @Size(max = 100)
    @Column(name = "action", nullable = false, updatable = false, length = 100)
    private String action;

    @Size(max = 50)
    @Column(name = "category", updatable = false, length = 50)
    private String category;

    @NotBlank
    @Size(max = 100)
    @Column(name = "resource_type", nullable = false, updatable = false, length = 100)
    private String resourceType;

    @Size(max = 128)
    @Column(name = "resource_id", updatable = false, length = 128)
    private String resourceId;

    @NotBlank
    @Size(max = 50)
    @Column(name = "operation", nullable = false, updatable = false, length = 50)
    private String operation;

    // === Outcome ===

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "outcome", nullable = false, updatable = false, length = 20)
    private AuditOutcome outcome;

    @Column(name = "http_status", updatable = false)
    private Integer httpStatus;

    @Size(max = 50)
    @Column(name = "error_code", updatable = false, length = 50)
    private String errorCode;

    @Size(max = 1000)
    @Column(name = "failure_reason", updatable = false, length = 1000)
    private String failureReason;

    // === Source ===

    @Size(max = 45)
    @Column(name = "source_ip", updatable = false, length = 45)
    private String sourceIp;

    @Size(max = 500)
    @Column(name = "user_agent", updatable = false, length = 500)
    private String userAgent;

    @Size(max = 50)
    @Column(name = "channel", updatable = false, length = 50)
    private String channel;

    // === State change (redacted JSON) ===

    @Column(name = "before_state", updatable = false, columnDefinition = "text")
    private String beforeState;

    @Column(name = "after_state", updatable = false, columnDefinition = "text")
    private String afterState;

    @Column(name = "changed_fields", updatable = false, columnDefinition = "text")
    private String changedFields;

    // === Metadata (redacted JSON) ===

    @Column(name = "metadata", updatable = false, columnDefinition = "text")
    private String metadata;

    // === Hash chain ===

    @Size(max = 64)
    @Column(name = "previous_hash", updatable = false, length = 64)
    private String previousHash;

    @NotBlank
    @Size(max = 64)
    @Column(name = "event_hash", nullable = false, updatable = false, length = 64)
    private String eventHash;

    @NotBlank
    @Size(max = 20)
    @Column(name = "hash_algorithm", nullable = false, updatable = false, length = 20)
    private String hashAlgorithm = "SHA-256";

    @Column(name = "schema_version", nullable = false, updatable = false)
    private Integer schemaVersion = 1;

    // === Timestamps ===

    @NotNull
    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @NotNull
    @Column(name = "recorded_at", nullable = false, updatable = false)
    private Instant recordedAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AuditEvent() {
    }

    public AuditEvent(UUID tenantId, AuditActorType actorType, String action,
                       String resourceType, String operation, AuditOutcome outcome,
                       Instant occurredAt, Instant recordedAt, String eventHash) {
        this.tenantId = tenantId;
        this.actorType = actorType;
        this.action = action;
        this.resourceType = resourceType;
        this.operation = operation;
        this.outcome = outcome;
        this.occurredAt = occurredAt;
        this.recordedAt = recordedAt;
        this.eventHash = eventHash;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public AuditActorType getActorType() { return actorType; }
    public UUID getActorUserId() { return actorUserId; }
    public String getActorService() { return actorService; }
    public String getActorDisplayName() { return actorDisplayName; }
    public String getSessionId() { return sessionId; }
    public String getJwtId() { return jwtId; }
    public String getRequestId() { return requestId; }
    public String getCorrelationId() { return correlationId; }
    public String getTraceId() { return traceId; }
    public String getAction() { return action; }
    public String getCategory() { return category; }
    public String getResourceType() { return resourceType; }
    public String getResourceId() { return resourceId; }
    public String getOperation() { return operation; }
    public AuditOutcome getOutcome() { return outcome; }
    public Integer getHttpStatus() { return httpStatus; }
    public String getErrorCode() { return errorCode; }
    public String getFailureReason() { return failureReason; }
    public String getSourceIp() { return sourceIp; }
    public String getUserAgent() { return userAgent; }
    public String getChannel() { return channel; }
    public String getBeforeState() { return beforeState; }
    public String getAfterState() { return afterState; }
    public String getChangedFields() { return changedFields; }
    public String getMetadata() { return metadata; }
    public String getPreviousHash() { return previousHash; }
    public String getEventHash() { return eventHash; }
    public String getHashAlgorithm() { return hashAlgorithm; }
    public Integer getSchemaVersion() { return schemaVersion; }
    public Instant getOccurredAt() { return occurredAt; }
    public Instant getRecordedAt() { return recordedAt; }
    public Instant getCreatedAt() { return createdAt; }

    // Setters are public for JPA and the AuditService builder. The entity
    // is append-only at the DB level (triggers block UPDATE/DELETE), so
    // calling these setters on a detached entity with an existing ID has
    // no effect — the DB rejects the UPDATE.
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public void setActorType(AuditActorType actorType) { this.actorType = actorType; }
    public void setActorUserId(UUID actorUserId) { this.actorUserId = actorUserId; }
    public void setActorService(String actorService) { this.actorService = actorService; }
    public void setActorDisplayName(String actorDisplayName) { this.actorDisplayName = actorDisplayName; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public void setJwtId(String jwtId) { this.jwtId = jwtId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }
    public void setTraceId(String traceId) { this.traceId = traceId; }
    public void setAction(String action) { this.action = action; }
    public void setCategory(String category) { this.category = category; }
    public void setResourceType(String resourceType) { this.resourceType = resourceType; }
    public void setResourceId(String resourceId) { this.resourceId = resourceId; }
    public void setOperation(String operation) { this.operation = operation; }
    public void setOutcome(AuditOutcome outcome) { this.outcome = outcome; }
    public void setHttpStatus(Integer httpStatus) { this.httpStatus = httpStatus; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }
    public void setSourceIp(String sourceIp) { this.sourceIp = sourceIp; }
    public void setUserAgent(String userAgent) { this.userAgent = userAgent; }
    public void setChannel(String channel) { this.channel = channel; }
    public void setBeforeState(String beforeState) { this.beforeState = beforeState; }
    public void setAfterState(String afterState) { this.afterState = afterState; }
    public void setChangedFields(String changedFields) { this.changedFields = changedFields; }
    public void setMetadata(String metadata) { this.metadata = metadata; }
    public void setPreviousHash(String previousHash) { this.previousHash = previousHash; }
    public void setEventHash(String eventHash) { this.eventHash = eventHash; }
    public void setHashAlgorithm(String hashAlgorithm) { this.hashAlgorithm = hashAlgorithm; }
    public void setSchemaVersion(Integer schemaVersion) { this.schemaVersion = schemaVersion; }
    public void setOccurredAt(Instant occurredAt) { this.occurredAt = occurredAt; }
    public void setRecordedAt(Instant recordedAt) { this.recordedAt = recordedAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AuditEvent that)) return false;
        if (id != null && that.id != null) return Objects.equals(id, that.id);
        return Objects.equals(tenantId, that.tenantId)
                && Objects.equals(occurredAt, that.occurredAt)
                && Objects.equals(action, that.action);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tenantId, occurredAt, action);
    }

    @Override
    public String toString() {
        return "AuditEvent{id=" + id + ", tenantId=" + tenantId
                + ", action='" + action + "', resourceType='" + resourceType + "'"
                + ", outcome=" + outcome + ", occurredAt=" + occurredAt + "}";
    }
}
