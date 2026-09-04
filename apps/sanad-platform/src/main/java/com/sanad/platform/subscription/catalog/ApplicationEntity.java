package com.sanad.platform.subscription.catalog;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Domain entity for an application in the Subscription Control Plane catalog.
 *
 * <p>The catalog is the single source of truth for applications — the executive
 * console renders whatever the catalog returns; nothing is hardcoded. This is a
 * platform-scoped catalog (like {@code saas_plans}/{@code modules}), NOT
 * tenant-scoped. Persistence is JdbcTemplate (consistent with
 * {@code SaasAdministrationService} and {@code module.registry}), NOT JPA.
 */
public final class ApplicationEntity {
    private UUID id;
    private String code;
    private String name;
    private String localizedName;
    private String description;
    private String category;
    private String status;              // ACTIVE | INACTIVE | DEPRECATED
    private String version;
    private int displayOrder;
    private String iconKey;
    private String provisioningMode;    // IMMEDIATE | MANUAL | ASYNC
    private List<String> supportedCountries;
    private List<String> dependencies;
    private Instant createdAt;
    private Instant updatedAt;

    public ApplicationEntity() {
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getLocalizedName() { return localizedName; }
    public void setLocalizedName(String localizedName) { this.localizedName = localizedName; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }

    public int getDisplayOrder() { return displayOrder; }
    public void setDisplayOrder(int displayOrder) { this.displayOrder = displayOrder; }

    public String getIconKey() { return iconKey; }
    public void setIconKey(String iconKey) { this.iconKey = iconKey; }

    public String getProvisioningMode() { return provisioningMode; }
    public void setProvisioningMode(String provisioningMode) { this.provisioningMode = provisioningMode; }

    public List<String> getSupportedCountries() { return supportedCountries; }
    public void setSupportedCountries(List<String> supportedCountries) { this.supportedCountries = supportedCountries; }

    public List<String> getDependencies() { return dependencies; }
    public void setDependencies(List<String> dependencies) { this.dependencies = dependencies; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ApplicationEntity that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "ApplicationEntity{" +
                "id=" + id +
                ", code='" + code + '\'' +
                ", status='" + status + '\'' +
                '}';
    }
}
