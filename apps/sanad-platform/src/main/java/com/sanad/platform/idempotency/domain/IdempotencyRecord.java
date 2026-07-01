package com.sanad.platform.idempotency.domain;

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
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Stage 05 §13 — Persistent idempotency record.
 *
 * <p>Stores the state of a sensitive command (POST/PUT/PATCH) so that
 * a retry with the same {@code Idempotency-Key} header replays the
 * original response instead of re-executing the business operation.</p>
 *
 * <p>The unique constraint on {@code (tenant_id, operation, route,
 * idempotency_key)} ensures that concurrent duplicate requests cannot
 * create multiple records — the database serializes them.</p>
 */
@Entity
@Table(name = "idempotency_records")
@EntityListeners(AuditingEntityListener.class)
public class IdempotencyRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false, columnDefinition = "uuid")
    private UUID id;

    @NotNull
    @Column(name = "tenant_id", nullable = false, columnDefinition = "uuid")
    private UUID tenantId;

    @NotBlank
    @Size(max = 255)
    @Column(name = "idempotency_key", nullable = false, length = 255)
    private String idempotencyKey;

    @NotBlank
    @Size(max = 100)
    @Column(name = "operation", nullable = false, length = 100)
    private String operation;

    @NotBlank
    @Size(max = 500)
    @Column(name = "route", nullable = false, length = 500)
    private String route;

    @Size(max = 100)
    @Column(name = "resource_type", length = 100)
    private String resourceType;

    @NotBlank
    @Size(max = 64)
    @Column(name = "request_fingerprint", nullable = false, length = 64)
    private String requestFingerprint;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private IdempotencyStatus status;

    @Column(name = "response_status")
    private Integer responseStatus;

    @Column(name = "response_headers", columnDefinition = "text")
    private String responseHeaders;

    @Column(name = "response_body", columnDefinition = "text")
    private String responseBody;

    @Column(name = "locked_at")
    private Instant lockedAt;

    @Column(name = "processing_started_at")
    private Instant processingStartedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @NotNull
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Size(max = 128)
    @Column(name = "owner_request_id", length = 128)
    private String ownerRequestId;

    @Size(max = 50)
    @Column(name = "error_code", length = 50)
    private String errorCode;

    @Size(max = 1000)
    @Column(name = "error_detail", length = 1000)
    private String errorDetail;

    // Stage 05A.2 §14 — Processing lease
    @Size(max = 128)
    @Column(name = "lease_owner_request_id", length = 128)
    private String leaseOwnerRequestId;

    @Column(name = "lease_expires_at")
    private Instant leaseExpiresAt;

    @Column(name = "attempt_count", nullable = false)
    private Integer attemptCount = 0;

    @Column(name = "last_attempt_at")
    private Instant lastAttemptAt;

    protected IdempotencyRecord() {
    }

    public IdempotencyRecord(UUID tenantId, String idempotencyKey, String operation,
                              String route, String requestFingerprint,
                              IdempotencyStatus status, Instant expiresAt) {
        this.tenantId = tenantId;
        this.idempotencyKey = idempotencyKey;
        this.operation = operation;
        this.route = route;
        this.requestFingerprint = requestFingerprint;
        this.status = status;
        this.expiresAt = expiresAt;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getOperation() { return operation; }
    public String getRoute() { return route; }
    public String getResourceType() { return resourceType; }
    public String getRequestFingerprint() { return requestFingerprint; }
    public IdempotencyStatus getStatus() { return status; }
    public Integer getResponseStatus() { return responseStatus; }
    public String getResponseHeaders() { return responseHeaders; }
    public String getResponseBody() { return responseBody; }
    public Instant getLockedAt() { return lockedAt; }
    public Instant getProcessingStartedAt() { return processingStartedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public String getOwnerRequestId() { return ownerRequestId; }
    public String getErrorCode() { return errorCode; }
    public String getErrorDetail() { return errorDetail; }
    public String getLeaseOwnerRequestId() { return leaseOwnerRequestId; }
    public Instant getLeaseExpiresAt() { return leaseExpiresAt; }
    public Integer getAttemptCount() { return attemptCount; }
    public Instant getLastAttemptAt() { return lastAttemptAt; }

    public void setResourceType(String resourceType) { this.resourceType = resourceType; }
    public void setStatus(IdempotencyStatus status) { this.status = status; }
    public void setResponseStatus(Integer responseStatus) { this.responseStatus = responseStatus; }
    public void setResponseHeaders(String responseHeaders) { this.responseHeaders = responseHeaders; }
    public void setResponseBody(String responseBody) { this.responseBody = responseBody; }
    public void setLockedAt(Instant lockedAt) { this.lockedAt = lockedAt; }
    public void setProcessingStartedAt(Instant processingStartedAt) { this.processingStartedAt = processingStartedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
    public void setOwnerRequestId(String ownerRequestId) { this.ownerRequestId = ownerRequestId; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }
    public void setErrorDetail(String errorDetail) { this.errorDetail = errorDetail; }
    public void setLeaseOwnerRequestId(String leaseOwnerRequestId) { this.leaseOwnerRequestId = leaseOwnerRequestId; }
    public void setLeaseExpiresAt(Instant leaseExpiresAt) { this.leaseExpiresAt = leaseExpiresAt; }
    public void setAttemptCount(Integer attemptCount) { this.attemptCount = attemptCount; }
    public void setLastAttemptAt(Instant lastAttemptAt) { this.lastAttemptAt = lastAttemptAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof IdempotencyRecord that)) return false;
        if (id != null && that.id != null) return Objects.equals(id, that.id);
        return Objects.equals(tenantId, that.tenantId)
                && Objects.equals(operation, that.operation)
                && Objects.equals(idempotencyKey, that.idempotencyKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tenantId, operation, idempotencyKey);
    }

    @Override
    public String toString() {
        return "IdempotencyRecord{id=" + id + ", tenantId=" + tenantId
                + ", key='" + idempotencyKey + "', operation='" + operation + "'"
                + ", status=" + status + "}";
    }
}
