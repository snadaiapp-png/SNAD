package com.sanad.platform.organization.dto;

import com.sanad.platform.organization.domain.OrganizationStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Outbound response payload representing a created (or fetched) Organization.
 *
 * <p>This DTO is the contract between the application service layer and
 * the future transport layer. Unlike {@link CreateOrganizationRequest},
 * it exposes the persisted state of the Organization aggregate including
 * its surrogate ID, audit timestamps, and lifecycle status.</p>
 *
 * <p>Note that {@code tenantId} is intentionally exposed as a UUID here
 * — this is acceptable on the response side because we are reading
 * state, not mutating it. The DDD aggregate-consistency rule (no bare
 * tenantId on the {@code Organization} entity) is about preventing
 * direct mutation of the tenant relationship; reading the tenant
 * identity in a serializable response is fine.</p>
 */
public class OrganizationResponse {

    /** Surrogate primary key of the Organization. */
    private UUID id;

    /** UUID of the parent Tenant. */
    private UUID tenantId;

    /** Human-readable organization name. */
    private String name;

    /** Optional longer description. */
    private String description;

    /** Current lifecycle status. */
    private OrganizationStatus status;

    /** When the Organization row was first persisted. */
    private Instant createdAt;

    /** When the Organization row was last modified. */
    private Instant updatedAt;

    // ------------------------------------------------------------
    // Constructors
    // ------------------------------------------------------------

    /** Default constructor for JSON serialization frameworks. */
    public OrganizationResponse() {
    }

    public OrganizationResponse(UUID id, UUID tenantId, String name, String description,
                                 OrganizationStatus status, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.name = name;
        this.description = description;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    // ------------------------------------------------------------
    // Getters / Setters (all fields)
    // ------------------------------------------------------------

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public OrganizationStatus getStatus() {
        return status;
    }

    public void setStatus(OrganizationStatus status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Override
    public String toString() {
        return "OrganizationResponse{" +
                "id=" + id +
                ", tenantId=" + tenantId +
                ", name='" + name + '\'' +
                ", status=" + status +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                '}';
    }
}
