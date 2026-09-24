package com.sanad.platform.module.registry;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Domain entity representing a capability within a Module.
 *
 * <p>Capabilities are typed:
 * <ul>
 *   <li>{@code MODULE_ENABLED} — boolean: is the module enabled at all?</li>
 *   <li>{@code FEATURE_ENABLED} — boolean: is a specific feature enabled?</li>
 *   <li>{@code NUMERIC_LIMIT} — integer: a hard limit (e.g., max contacts)</li>
 *   <li>{@code QUOTA} — integer with period: a usage quota (e.g., monthly API calls)</li>
 *   <li>{@code BOOLEAN_CAPABILITY} — boolean: a generic boolean toggle</li>
 * </ul>
 *
 * <p>Capabilities are global (defined once per module). Per-plan overrides are
 * stored in {@code plan_module_entitlements} and resolved at runtime by
 * {@code EntitlementResolver}.
 */
public final class ModuleCapabilityEntity {
    private UUID id;
    private UUID moduleId;
    private String code;            // e.g., 'CRM.MAX_CONTACTS', 'AI.MONTHLY_OPS'
    private String name;
    private String description;
    private String capabilityType;  // MODULE_ENABLED | FEATURE_ENABLED | NUMERIC_LIMIT | QUOTA | BOOLEAN_CAPABILITY
    private String defaultValue;    // default value as string (parsed by resolver)
    private String status;          // ACTIVE | INACTIVE
    private Instant createdAt;
    private Instant updatedAt;

    public ModuleCapabilityEntity() {
    }

    public ModuleCapabilityEntity(UUID id, UUID moduleId, String code, String name,
                                  String description, String capabilityType,
                                  String defaultValue, String status,
                                  Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.moduleId = moduleId;
        this.code = code;
        this.name = name;
        this.description = description;
        this.capabilityType = capabilityType;
        this.defaultValue = defaultValue;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getModuleId() { return moduleId; }
    public void setModuleId(UUID moduleId) { this.moduleId = moduleId; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getCapabilityType() { return capabilityType; }
    public void setCapabilityType(String capabilityType) { this.capabilityType = capabilityType; }

    public String getDefaultValue() { return defaultValue; }
    public void setDefaultValue(String defaultValue) { this.defaultValue = defaultValue; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ModuleCapabilityEntity that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "ModuleCapabilityEntity{" +
                "code='" + code + '\'' +
                ", type='" + capabilityType + '\'' +
                ", moduleId=" + moduleId +
                '}';
    }
}
