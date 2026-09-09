package com.sanad.platform.subscription.catalog;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Product catalog management — create/update/list. R0C-11 closes the product
 * catalog runtime on the existing {@code products} storage (no migration, no
 * schema change).
 *
 * <p>Frozen semantics:
 * <ul>
 *   <li>the product {@code code} is the stable identity — normalized
 *       (trim + uppercase) at creation, never mutated afterwards;</li>
 *   <li>uniqueness is fail-closed (deterministic domain error with the
 *       {@code uk_products_code} index as the concurrent-race backstop);</li>
 *   <li>product types and statuses mirror the existing DB CHECK constraints;</li>
 *   <li>{@code application_id} is nullable; a non-null value must reference an
 *       existing application (deterministic domain error otherwise);</li>
 *   <li>NO physical delete exists — retirement is INACTIVE/ARCHIVED and
 *       historical references (items, prices, entitlements) remain valid.</li>
 * </ul>
 */
@Service
public class ProductCatalogService {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_INACTIVE = "INACTIVE";
    public static final String STATUS_ARCHIVED = "ARCHIVED";

    public static final String TYPE_APPLICATION = "APPLICATION";
    public static final String TYPE_ADD_ON = "ADD_ON";
    public static final String TYPE_METERED = "METERED";
    public static final String TYPE_OTHER = "OTHER";

    private static final Set<String> PRODUCT_TYPES =
            Set.of(TYPE_APPLICATION, TYPE_ADD_ON, TYPE_METERED, TYPE_OTHER);
    private static final Set<String> PRODUCT_STATUSES =
            Set.of(STATUS_ACTIVE, STATUS_INACTIVE, STATUS_ARCHIVED);
    private static final Pattern CODE_PATTERN = Pattern.compile("^[A-Z0-9_-]{1,50}$");

    private final ProductRepository repository;

    public ProductCatalogService(ProductRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public ProductEntity create(ProductEntity request) {
        String code = normalizeCode(request.getCode());
        if (!CODE_PATTERN.matcher(code).matches()) {
            throw new IllegalArgumentException(
                    "Product code must match [A-Za-z0-9_-]{1,50} after normalization: " + code);
        }
        requireName(request.getName());
        if (repository.existsByCode(code)) {
            throw new IllegalStateException("Product code already exists: " + code);
        }
        validateType(request.getProductType());
        validateStatus(request.getStatus());
        validateApplication(request.getApplicationId());
        request.setCode(code);
        if (request.getStatus() == null || request.getStatus().isBlank()) {
            request.setStatus(STATUS_ACTIVE);
        }
        request.setId(UUID.randomUUID());
        Instant now = Instant.now();
        request.setCreatedAt(now);
        request.setUpdatedAt(now);
        insertFailClosed(request);
        return request;
    }

    @Transactional
    public ProductEntity update(UUID id, ProductEntity changes) {
        ProductEntity existing = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Unknown product: " + id));
        // R0C-11: code is the stable identity — never mutated on update.
        requireName(changes.getName());
        validateType(changes.getProductType());
        validateStatus(changes.getStatus());
        validateApplication(changes.getApplicationId());
        existing.setName(changes.getName());
        existing.setDescription(changes.getDescription());
        existing.setApplicationId(changes.getApplicationId());
        existing.setProductType(changes.getProductType());
        existing.setStatus(changes.getStatus() == null ? existing.getStatus() : changes.getStatus());
        existing.setUpdatedAt(Instant.now());
        repository.update(existing);
        return existing;
    }

    @Transactional(readOnly = true)
    public ProductEntity findById(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Unknown product: " + id));
    }

    @Transactional(readOnly = true)
    public Optional<ProductEntity> findByCode(String code) {
        return repository.findByCode(normalizeCode(code));
    }

    @Transactional(readOnly = true)
    public List<ProductEntity> findAll() {
        return repository.findAll();
    }

    @Transactional(readOnly = true)
    public List<ProductEntity> findAvailable() {
        return repository.findAvailable();
    }

    // ---------------------------------------------------------------
    // helpers
    // ---------------------------------------------------------------

    private String normalizeCode(String raw) {
        String code = raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
        if (code.isBlank()) {
            throw new IllegalArgumentException("Product code is required");
        }
        return code;
    }

    private void requireName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Product name is required");
        }
    }

    private void validateType(String type) {
        if (type == null || !PRODUCT_TYPES.contains(type)) {
            throw new IllegalArgumentException(
                    "Invalid product type: " + type
                            + " (allowed: APPLICATION, ADD_ON, METERED, OTHER)");
        }
    }

    private void validateStatus(String status) {
        if (status != null && !status.isBlank() && !PRODUCT_STATUSES.contains(status)) {
            throw new IllegalArgumentException(
                    "Invalid product status: " + status
                            + " (allowed: ACTIVE, INACTIVE, ARCHIVED)");
        }
    }

    private void validateApplication(UUID applicationId) {
        if (applicationId == null) {
            return;
        }
        if (!repository.applicationExists(applicationId)) {
            throw new IllegalArgumentException("Unknown application: " + applicationId);
        }
    }

    /**
     * The repository translates {@code uk_products_code} violations into
     * {@link DuplicateKeyException}; keep the deterministic fail-closed domain
     * error for the concurrent-race path (two simultaneous inserts of the same
     * code after both pre-checks pass).
     */
    private void insertFailClosed(ProductEntity product) {
        try {
            repository.insert(product);
        } catch (DuplicateKeyException e) {
            throw new IllegalStateException("Product code already exists: " + product.getCode());
        }
    }
}
