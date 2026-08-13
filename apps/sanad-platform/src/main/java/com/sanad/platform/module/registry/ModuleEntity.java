package com.sanad.platform.module.registry;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Domain entity representing a SNAD Module (global catalog).
 *
 * <p>Modules are the top-level organizational unit in SNAD's SaaS architecture.
 * Each module (CRM, AI, Workflow, ERP, Finance, Analytics, HRM, POS,
 * Ecommerce/CX, Industry Solutions) is registered here as a global catalog entry.
 *
 * <p>This entity is NOT tenant-scoped — it is a global catalog. Tenant-specific
 * entitlements are linked via {@code plan_module_entitlements} and resolved at
 * runtime by {@code EntitlementResolver}.
 *
 * <p>Persistence is via JdbcTemplate (consistent with {@code SaasAdministrationService}),
 * NOT JPA — to avoid the hybrid JdbcTemplate+JPA anti-pattern.
 */
public final class ModuleEntity {
    private UUID id;
    private String code;
    private String name;
    private String description;
    private String status;          // ACTIVE | INACTIVE | DEPRECATED
    private int displayOrder;
    private String version;
    private boolean enabled;
    private Instant createdAt;
    private Instant updatedAt;

    public ModuleEntity() {
    }

    public ModuleEntity(UUID id, String code, String name, String description,
                        String status, int displayOrder, String version,
                        boolean enabled, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.code = code;
        this.name = name;
        this.description = description;
        this.status = status;
        this.displayOrder = displayOrder;
        this.version = version;
        this.enabled = enabled;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public int getDisplayOrder() { return displayOrder; }
    public void setDisplayOrder(int displayOrder) { this.displayOrder = displayOrder; }

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ModuleEntity that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "ModuleEntity{" +
                "id=" + id +
                ", code='" + code + '\'' +
                ", name='" + name + '\'' +
                ", status='" + status + '\'' +
                ", enabled=" + enabled +
                '}';
    }
}
