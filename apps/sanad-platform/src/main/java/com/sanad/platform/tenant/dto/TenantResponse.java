package com.sanad.platform.tenant.dto;

import com.sanad.platform.tenant.domain.TenantStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Outbound response payload representing a Tenant.
 */
public class TenantResponse {

    private UUID id;
    private String name;
    private String subdomain;
    private TenantStatus status;
    private Instant createdAt;
    private Instant updatedAt;

    public TenantResponse() {
    }

    public TenantResponse(UUID id, String name, String subdomain, TenantStatus status,
                          Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.name = name;
        this.subdomain = subdomain;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getSubdomain() { return subdomain; }
    public void setSubdomain(String subdomain) { this.subdomain = subdomain; }
    public TenantStatus getStatus() { return status; }
    public void setStatus(TenantStatus status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    @Override
    public String toString() {
        return "TenantResponse{id=" + id + ", name='" + name + "', subdomain='" + subdomain
                + "', status=" + status + '}';
    }
}
