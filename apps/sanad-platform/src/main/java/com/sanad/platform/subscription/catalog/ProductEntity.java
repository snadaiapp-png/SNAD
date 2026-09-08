package com.sanad.platform.subscription.catalog;

import java.time.Instant;
import java.util.UUID;

/**
 * Product catalog entry — platform catalog data (like {@link ApplicationEntity},
 * not tenant data). Backed by the {@code products} table
 * ({@code V20260829_2}): product types APPLICATION | ADD_ON | METERED | OTHER,
 * statuses ACTIVE | INACTIVE | ARCHIVED.
 *
 * <p>R0C-11: the {@code code} is the stable identity (never renamed), there is
 * no physical delete — retirement is expressed through INACTIVE/ARCHIVED — and
 * historical references (subscription items, prices, product entitlements)
 * remain valid after any status change.</p>
 */
public class ProductEntity {

    private UUID id;
    private String code;
    private String name;
    private String description;
    private UUID applicationId;
    private String productType;   // APPLICATION | ADD_ON | METERED | OTHER
    private String status;        // ACTIVE | INACTIVE | ARCHIVED
    private Instant createdAt;
    private Instant updatedAt;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
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

    public UUID getApplicationId() {
        return applicationId;
    }

    public void setApplicationId(UUID applicationId) {
        this.applicationId = applicationId;
    }

    public String getProductType() {
        return productType;
    }

    public void setProductType(String productType) {
        this.productType = productType;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
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
}
