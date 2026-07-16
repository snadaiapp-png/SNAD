package com.sanad.platform.crm.product.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Product repository port — bounded context for product/service catalog.
 * <p>
 * Products have name, SKU, type (PRODUCT|SERVICE), pricing, tax, and
 * category. They can be linked to opportunities for quotation generation.
 * <p>
 * Branch: feature/crm-products
 */
public interface ProductRepository {
    ProductRecord findById(UUID tenantId, UUID productId);
    List<ProductRecord> findAll(UUID tenantId, int limit, String search, Boolean activeOnly, String category);
    ProductRecord create(UUID tenantId, UUID actorId, CreateProductCommand command);
    ProductRecord update(UUID tenantId, UUID actorId, UUID productId, UpdateProductCommand command, long expectedVersion);
    void delete(UUID tenantId, UUID actorId, UUID productId);

    record ProductRecord(UUID id, long version, String name, String sku, String description,
            String productType, String category,
            BigDecimal unitPrice, String currencyCode, BigDecimal taxRate,
            String unit, boolean active,
            Instant createdAt, Instant updatedAt) {}

    record CreateProductCommand(String name, String sku, String description,
            String productType, String category,
            BigDecimal unitPrice, String currencyCode, BigDecimal taxRate, String unit) {}

    record UpdateProductCommand(String name, String sku, String description,
            String productType, String category,
            BigDecimal unitPrice, String currencyCode, BigDecimal taxRate,
            String unit, Boolean active) {}
}
