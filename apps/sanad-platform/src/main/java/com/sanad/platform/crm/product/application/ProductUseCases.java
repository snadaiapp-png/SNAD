package com.sanad.platform.crm.product.application;

import com.sanad.platform.crm.product.domain.ProductRepository;
import com.sanad.platform.crm.product.domain.ProductRepository.*;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Use-case façade for the Product bounded context.
 * <p>
 * Branch: feature/crm-products
 */
public class ProductUseCases {
    private final ProductRepository repo;

    public ProductUseCases(ProductRepository repo) { this.repo = repo; }

    @Transactional
    public ProductRecord create(UUID tenantId, UUID actorId, CreateProductCommand cmd) { return repo.create(tenantId, actorId, cmd); }
    public ProductRecord getById(UUID tenantId, UUID id) { return repo.findById(tenantId, id); }
    public List<ProductRecord> list(UUID tenantId, int limit, String search, Boolean activeOnly, String category) { return repo.findAll(tenantId, limit, search, activeOnly, category); }
    @Transactional
    public ProductRecord update(UUID tenantId, UUID actorId, UUID id, UpdateProductCommand cmd, long expectedVersion) { return repo.update(tenantId, actorId, id, cmd, expectedVersion); }
    @Transactional
    public void delete(UUID tenantId, UUID actorId, UUID id) { repo.delete(tenantId, actorId, id); }
}
