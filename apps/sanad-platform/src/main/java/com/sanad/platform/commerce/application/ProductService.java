package com.sanad.platform.commerce.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanad.platform.admin.service.PlatformAuditService;
import com.sanad.platform.commerce.api.CommerceDtos.*;
import com.sanad.platform.commerce.domain.CommerceDomain;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Product application service (v20260816.5).
 *
 * <p>Tenant + store-scoped CRUD for {@code commerce_products}, plus
 * nested management of variants, collections, and prices.
 * Publish / unpublish lifecycle mirrors {@code WebsitePageService}.
 */
@Service
public class ProductService {

    private final JdbcTemplate jdbc;
    private final PlatformAuditService auditService;
    private final ObjectMapper objectMapper;

    public ProductService(JdbcTemplate jdbc, PlatformAuditService auditService, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    // ===== Products =====
    @Transactional
    public ProductResponse create(UUID tenantId, UUID storeId, CreateProductRequest request, Authentication auth) {
        ensureStore(tenantId, storeId);
        if (request == null || request.name() == null || request.name().isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "name is required");
        String slug = normalizeSlug(request.slug() != null ? request.slug() : request.name(), 200);
        CommerceDomain.ProductType type = request.productType() != null
                ? request.productType() : CommerceDomain.ProductType.PHYSICAL;
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        try {
            jdbc.update("INSERT INTO commerce_products (id, tenant_id, store_id, name, slug, sku, "
                            + "description, status, product_type, version, created_by, created_at, updated_at) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, 'DRAFT', ?, 0, ?, ?, ?)",
                    id, tenantId, storeId, request.name().trim(), slug, request.sku(),
                    request.description(), type.name(),
                    actorUserId(auth), Timestamp.from(now), Timestamp.from(now));
        } catch (DuplicateKeyException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "product slug already exists: " + slug);
        }
        audit(tenantId, auth, "PRODUCT.CREATED", id, "slug=" + slug);
        return getOrThrow(tenantId, storeId, id);
    }

    @Transactional
    public ProductResponse update(UUID tenantId, UUID storeId, UUID productId,
                                    UpdateProductRequest request, Authentication auth) {
        ProductResponse existing = getOrThrow(tenantId, storeId, productId);
        if (request.expectedVersion() != null && request.expectedVersion() != existing.version()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "version conflict: expected " + request.expectedVersion() + " but was " + existing.version());
        }
        Instant now = Instant.now();
        if (request.name() != null && !request.name().isBlank()) {
            jdbc.update("UPDATE commerce_products SET name = ?, updated_at = ?, version = version + 1 "
                            + "WHERE tenant_id = ? AND id = ?", request.name().trim(), Timestamp.from(now), tenantId, productId);
        }
        if (request.sku() != null) {
            jdbc.update("UPDATE commerce_products SET sku = ?, updated_at = ?, version = version + 1 "
                            + "WHERE tenant_id = ? AND id = ?", request.sku(), Timestamp.from(now), tenantId, productId);
        }
        if (request.description() != null) {
            jdbc.update("UPDATE commerce_products SET description = ?, updated_at = ?, version = version + 1 "
                            + "WHERE tenant_id = ? AND id = ?", request.description(), Timestamp.from(now), tenantId, productId);
        }
        if (request.productType() != null) {
            jdbc.update("UPDATE commerce_products SET product_type = ?, updated_at = ?, version = version + 1 "
                            + "WHERE tenant_id = ? AND id = ?", request.productType().name(), Timestamp.from(now), tenantId, productId);
        }
        audit(tenantId, auth, "PRODUCT.UPDATED", productId, "name=" + existing.name());
        return getOrThrow(tenantId, storeId, productId);
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> list(UUID tenantId, UUID storeId) {
        ensureStore(tenantId, storeId);
        return jdbc.query("SELECT * FROM commerce_products WHERE tenant_id = ? AND store_id = ? ORDER BY created_at",
                this::mapRow, tenantId, storeId);
    }

    @Transactional(readOnly = true)
    public ProductResponse get(UUID tenantId, UUID storeId, UUID productId) {
        return getOrThrow(tenantId, storeId, productId);
    }

    @Transactional
    public ProductResponse publish(UUID tenantId, UUID storeId, UUID productId, Authentication auth) {
        ProductResponse existing = getOrThrow(tenantId, storeId, productId);
        Instant now = Instant.now();
        jdbc.update("UPDATE commerce_products SET status = 'PUBLISHED', published_at = ?, "
                        + "updated_at = ?, version = version + 1 WHERE tenant_id = ? AND id = ?",
                Timestamp.from(now), Timestamp.from(now), tenantId, productId);
        audit(tenantId, auth, "PRODUCT.PUBLISHED", productId, "name=" + existing.name());
        return getOrThrow(tenantId, storeId, productId);
    }

    @Transactional
    public ProductResponse unpublish(UUID tenantId, UUID storeId, UUID productId, Authentication auth) {
        ProductResponse existing = getOrThrow(tenantId, storeId, productId);
        Instant now = Instant.now();
        jdbc.update("UPDATE commerce_products SET status = 'UNPUBLISHED', updated_at = ?, version = version + 1 "
                        + "WHERE tenant_id = ? AND id = ?", Timestamp.from(now), tenantId, productId);
        audit(tenantId, auth, "PRODUCT.UNPUBLISHED", productId, "name=" + existing.name());
        return getOrThrow(tenantId, storeId, productId);
    }

    @Transactional
    public ProductResponse archive(UUID tenantId, UUID storeId, UUID productId, Authentication auth) {
        ProductResponse existing = getOrThrow(tenantId, storeId, productId);
        Instant now = Instant.now();
        jdbc.update("UPDATE commerce_products SET status = 'ARCHIVED', updated_at = ?, version = version + 1 "
                        + "WHERE tenant_id = ? AND id = ?", Timestamp.from(now), tenantId, productId);
        audit(tenantId, auth, "PRODUCT.ARCHIVED", productId, "name=" + existing.name());
        return getOrThrow(tenantId, storeId, productId);
    }

    // ===== Variants =====
    @Transactional
    public VariantResponse createVariant(UUID tenantId, UUID storeId, UUID productId,
                                          CreateVariantRequest request, Authentication auth) {
        ensureProduct(tenantId, storeId, productId);
        if (request == null || request.name() == null || request.name().isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "variant name is required");
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        jdbc.update("INSERT INTO commerce_product_variants (id, tenant_id, product_id, sku, name, "
                        + "options, status, version, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?::jsonb, 'DRAFT', 0, ?, ?)",
                id, tenantId, productId, request.sku(), request.name().trim(),
                toJson(request.options()), Timestamp.from(now), Timestamp.from(now));
        audit(tenantId, auth, "PRODUCT.VARIANT_CREATED", id, "product=" + productId);
        return getVariantOrThrow(tenantId, productId, id);
    }

    @Transactional(readOnly = true)
    public List<VariantResponse> listVariants(UUID tenantId, UUID storeId, UUID productId) {
        ensureProduct(tenantId, storeId, productId);
        return jdbc.query("SELECT * FROM commerce_product_variants WHERE tenant_id = ? AND product_id = ? ORDER BY created_at",
                this::mapVariantRow, tenantId, productId);
    }

    // ===== Collections =====
    @Transactional
    public CollectionResponse createCollection(UUID tenantId, UUID storeId,
                                                 CreateCollectionRequest request, Authentication auth) {
        ensureStore(tenantId, storeId);
        if (request == null || request.name() == null || request.name().isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "collection name is required");
        String slug = normalizeSlug(request.slug() != null ? request.slug() : request.name(), 200);
        int sortOrder = request.sortOrder() != null ? request.sortOrder() : 0;
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        try {
            jdbc.update("INSERT INTO commerce_collections (id, tenant_id, store_id, name, slug, "
                            + "description, status, sort_order, version, created_at, updated_at) "
                            + "VALUES (?, ?, ?, ?, ?, ?, 'DRAFT', ?, 0, ?, ?)",
                    id, tenantId, storeId, request.name().trim(), slug, request.description(),
                    sortOrder, Timestamp.from(now), Timestamp.from(now));
        } catch (DuplicateKeyException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "collection slug already exists: " + slug);
        }
        // Attach products (if any)
        if (request.productIds() != null) {
            int idx = 0;
            for (UUID productId : request.productIds()) {
                attachProductToCollection(tenantId, id, productId, idx++);
            }
        }
        audit(tenantId, auth, "COLLECTION.CREATED", id, "slug=" + slug);
        return getCollectionOrThrow(tenantId, storeId, id);
    }

    @Transactional(readOnly = true)
    public List<CollectionResponse> listCollections(UUID tenantId, UUID storeId) {
        ensureStore(tenantId, storeId);
        return jdbc.query("SELECT * FROM commerce_collections WHERE tenant_id = ? AND store_id = ? ORDER BY sort_order, created_at",
                this::mapCollectionRow, tenantId, storeId);
    }

    private void attachProductToCollection(UUID tenantId, UUID collectionId, UUID productId, int sortOrder) {
        try {
            jdbc.update("INSERT INTO commerce_collection_products (id, tenant_id, collection_id, product_id, sort_order, created_at) "
                            + "VALUES (?, ?, ?, ?, ?, ?)",
                    UUID.randomUUID(), tenantId, collectionId, productId, sortOrder, Timestamp.from(Instant.now()));
        } catch (DuplicateKeyException ignored) {
            // already attached — no-op
        }
    }

    // ===== Prices =====
    @Transactional
    public PriceResponse createPrice(UUID tenantId, UUID storeId, UUID productId,
                                      CreatePriceRequest request, Authentication auth) {
        ensureProduct(tenantId, storeId, productId);
        if (request == null || request.amount() == null || request.amount().signum() < 0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "amount must be >= 0");
        String currency = request.currency() != null && !request.currency().isBlank()
                ? request.currency() : "SAR";
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        jdbc.update("INSERT INTO commerce_prices (id, tenant_id, store_id, product_id, variant_id, "
                        + "currency, amount, compare_at_amount, valid_from, valid_to, status, version, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'ACTIVE', 0, ?, ?)",
                id, tenantId, storeId, productId, request.variantId(), currency,
                request.amount(), request.compareAtAmount(),
                request.validFrom() != null ? Timestamp.from(request.validFrom()) : null,
                request.validTo() != null ? Timestamp.from(request.validTo()) : null,
                Timestamp.from(now), Timestamp.from(now));
        audit(tenantId, auth, "PRICE.CREATED", id, "product=" + productId + ",amount=" + request.amount());
        return getPriceOrThrow(tenantId, id);
    }

    @Transactional(readOnly = true)
    public List<PriceResponse> listPrices(UUID tenantId, UUID storeId, UUID productId) {
        ensureProduct(tenantId, storeId, productId);
        return jdbc.query("SELECT * FROM commerce_prices WHERE tenant_id = ? AND product_id = ? ORDER BY created_at",
                this::mapPriceRow, tenantId, productId);
    }

    // ===== Helpers =====
    private void ensureStore(UUID tenantId, UUID storeId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM commerce_stores WHERE tenant_id = ? AND id = ?",
                Integer.class, tenantId, storeId);
        if (count == null || count == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "store not found");
    }

    private void ensureProduct(UUID tenantId, UUID storeId, UUID productId) {
        try {
            jdbc.queryForObject("SELECT id FROM commerce_products WHERE tenant_id = ? AND store_id = ? AND id = ?",
                    (rs, rowNum) -> rs.getObject("id", UUID.class), tenantId, storeId, productId);
        } catch (EmptyResultDataAccessException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "product not found: " + productId);
        }
    }

    private ProductResponse getOrThrow(UUID tenantId, UUID storeId, UUID productId) {
        try {
            return jdbc.queryForObject(
                    "SELECT * FROM commerce_products WHERE tenant_id = ? AND store_id = ? AND id = ?",
                    this::mapRow, tenantId, storeId, productId);
        } catch (EmptyResultDataAccessException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "product not found: " + productId);
        }
    }

    private VariantResponse getVariantOrThrow(UUID tenantId, UUID productId, UUID variantId) {
        try {
            return jdbc.queryForObject(
                    "SELECT * FROM commerce_product_variants WHERE tenant_id = ? AND product_id = ? AND id = ?",
                    this::mapVariantRow, tenantId, productId, variantId);
        } catch (EmptyResultDataAccessException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "variant not found: " + variantId);
        }
    }

    private CollectionResponse getCollectionOrThrow(UUID tenantId, UUID storeId, UUID collectionId) {
        try {
            CollectionResponse col = jdbc.queryForObject(
                    "SELECT * FROM commerce_collections WHERE tenant_id = ? AND store_id = ? AND id = ?",
                    this::mapCollectionRow, tenantId, storeId, collectionId);
            if (col != null) {
                List<UUID> productIds = jdbc.queryForList(
                        "SELECT product_id FROM commerce_collection_products WHERE collection_id = ? ORDER BY sort_order",
                        UUID.class, collectionId);
                return new CollectionResponse(col.id(), col.tenantId(), col.storeId(), col.name(), col.slug(),
                        col.description(), col.status(), col.sortOrder(), productIds, col.version(),
                        col.createdAt(), col.updatedAt());
            }
            return col;
        } catch (EmptyResultDataAccessException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "collection not found: " + collectionId);
        }
    }

    private PriceResponse getPriceOrThrow(UUID tenantId, UUID priceId) {
        try {
            return jdbc.queryForObject(
                    "SELECT * FROM commerce_prices WHERE tenant_id = ? AND id = ?",
                    this::mapPriceRow, tenantId, priceId);
        } catch (EmptyResultDataAccessException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "price not found: " + priceId);
        }
    }

    private String normalizeSlug(String raw, int maxLen) {
        if (raw == null) return "item";
        String s = raw.trim().toLowerCase().replaceAll("[^a-z0-9-]", "-").replaceAll("-+", "-");
        if (s.startsWith("-")) s = s.substring(1);
        if (s.endsWith("-")) s = s.substring(0, s.length() - 1);
        if (s.isEmpty()) s = "item";
        return s.length() > maxLen ? s.substring(0, maxLen) : s;
    }

    private UUID actorUserId(Authentication auth) {
        if (auth == null || auth.getName() == null) return null;
        try { return UUID.fromString(auth.getName()); } catch (Exception e) { return null; }
    }

    private void audit(UUID tenantId, Authentication auth, String action, UUID resourceId, String reason) {
        try { auditService.success(auth, tenantId, action, "COMMERCE", resourceId == null ? null : resourceId.toString(), reason, null, null); }
        catch (Exception ignored) {}
    }

    private String toJson(Map<String, Object> map) {
        try { return objectMapper.writeValueAsString(map); }
        catch (Exception e) { return "{}"; }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> fromJson(String json) {
        if (json == null || json.isBlank()) return null;
        try { return objectMapper.readValue(json, Map.class); }
        catch (Exception e) { return null; }
    }

    private ProductResponse mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new ProductResponse(
                rs.getObject("id", UUID.class), rs.getObject("tenant_id", UUID.class),
                rs.getObject("store_id", UUID.class), rs.getString("name"), rs.getString("slug"),
                rs.getString("sku"), rs.getString("description"),
                CommerceDomain.ProductStatus.valueOf(rs.getString("status")),
                CommerceDomain.ProductType.valueOf(rs.getString("product_type")),
                rs.getObject("published_at", Timestamp.class) == null ? null
                        : rs.getObject("published_at", Timestamp.class).toInstant(),
                rs.getLong("version"),
                rs.getObject("created_at", Timestamp.class).toInstant(),
                rs.getObject("updated_at", Timestamp.class).toInstant());
    }

    private VariantResponse mapVariantRow(ResultSet rs, int rowNum) throws SQLException {
        return new VariantResponse(
                rs.getObject("id", UUID.class), rs.getObject("tenant_id", UUID.class),
                rs.getObject("product_id", UUID.class), rs.getString("sku"), rs.getString("name"),
                fromJson(rs.getString("options")),
                CommerceDomain.VariantStatus.valueOf(rs.getString("status")),
                rs.getLong("version"),
                rs.getObject("created_at", Timestamp.class).toInstant(),
                rs.getObject("updated_at", Timestamp.class).toInstant());
    }

    private CollectionResponse mapCollectionRow(ResultSet rs, int rowNum) throws SQLException {
        return new CollectionResponse(
                rs.getObject("id", UUID.class), rs.getObject("tenant_id", UUID.class),
                rs.getObject("store_id", UUID.class), rs.getString("name"), rs.getString("slug"),
                rs.getString("description"),
                CommerceDomain.CollectionStatus.valueOf(rs.getString("status")),
                rs.getInt("sort_order"),
                List.of(),
                rs.getLong("version"),
                rs.getObject("created_at", Timestamp.class).toInstant(),
                rs.getObject("updated_at", Timestamp.class).toInstant());
    }

    private PriceResponse mapPriceRow(ResultSet rs, int rowNum) throws SQLException {
        return new PriceResponse(
                rs.getObject("id", UUID.class), rs.getObject("tenant_id", UUID.class),
                rs.getObject("store_id", UUID.class), rs.getObject("product_id", UUID.class),
                rs.getObject("variant_id", UUID.class),
                rs.getString("currency"), rs.getBigDecimal("amount"),
                rs.getBigDecimal("compare_at_amount"),
                rs.getObject("valid_from", Timestamp.class) == null ? null
                        : rs.getObject("valid_from", Timestamp.class).toInstant(),
                rs.getObject("valid_to", Timestamp.class) == null ? null
                        : rs.getObject("valid_to", Timestamp.class).toInstant(),
                CommerceDomain.PriceStatus.valueOf(rs.getString("status")),
                rs.getLong("version"),
                rs.getObject("created_at", Timestamp.class).toInstant(),
                rs.getObject("updated_at", Timestamp.class).toInstant());
    }
}
