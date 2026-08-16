package com.sanad.platform.commerce.api;

import com.sanad.platform.commerce.api.CommerceDtos.*;
import com.sanad.platform.commerce.application.StoreDomainService;
import com.sanad.platform.commerce.domain.CommerceDomain;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Public Stores API (v20260816.5).
 *
 * <p>Hostname-driven public resolution — NO {@code @RequireCapability}.
 * Resolves by {@code Host} header → active {@code commerce_store_domains}
 * row → ACTIVE {@code commerce_stores} → PUBLISHED products / collections.
 *
 * <p>Only PUBLISHED content is exposed publicly.
 */
@RestController
@RequestMapping("/api/v1/public/stores")
public class PublicStoreController {

    private final StoreDomainService domainService;
    private final JdbcTemplate jdbc;

    public PublicStoreController(StoreDomainService domainService, JdbcTemplate jdbc) {
        this.domainService = domainService;
        this.jdbc = jdbc;
    }

    @GetMapping("/resolve")
    public ResponseEntity<PublicStoreResponse> resolveStore(
            @RequestHeader(value = "Host", required = false) String host) {
        if (host == null || host.isBlank()) return ResponseEntity.badRequest().build();
        var domain = domainService.findByHostname(host);
        if (domain == null) return ResponseEntity.notFound().build();
        try {
            Map<String, Object> store = jdbc.queryForMap(
                    "SELECT id, tenant_id, name, slug, default_locale, default_currency "
                            + "FROM commerce_stores WHERE id = ? AND tenant_id = ? AND status = 'ACTIVE'",
                    domain.storeId(), domain.tenantId());
            List<PublicCollectionResponse> collections = listPublicCollections(domain.tenantId(), domain.storeId());
            return ResponseEntity.ok(new PublicStoreResponse(
                    domain.storeId(), domain.tenantId(),
                    (String) store.get("name"), (String) store.get("slug"),
                    (String) store.get("default_locale"), (String) store.get("default_currency"),
                    collections));
        } catch (EmptyResultDataAccessException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/resolve/products")
    public ResponseEntity<List<PublicProductResponse>> listProducts(
            @RequestHeader(value = "Host", required = false) String host) {
        var resolved = resolveTenantAndStore(host);
        if (resolved == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(listPublicProducts(resolved[0], resolved[1], null));
    }

    @GetMapping("/resolve/products/{slug}")
    public ResponseEntity<PublicProductResponse> getProduct(
            @RequestHeader(value = "Host", required = false) String host,
            @PathVariable String slug) {
        var resolved = resolveTenantAndStore(host);
        if (resolved == null) return ResponseEntity.notFound().build();
        List<PublicProductResponse> products = listPublicProducts(resolved[0], resolved[1], slug);
        if (products.isEmpty()) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(products.get(0));
    }

    @GetMapping("/resolve/collections")
    public ResponseEntity<List<PublicCollectionResponse>> listCollections(
            @RequestHeader(value = "Host", required = false) String host) {
        var resolved = resolveTenantAndStore(host);
        if (resolved == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(listPublicCollections(resolved[0], resolved[1]));
    }

    // ===== Helpers =====
    private UUID[] resolveTenantAndStore(String host) {
        if (host == null || host.isBlank()) return null;
        var domain = domainService.findByHostname(host);
        if (domain == null) return null;
        return new UUID[] { domain.tenantId(), domain.storeId() };
    }

    private List<PublicProductResponse> listPublicProducts(UUID tenantId, UUID storeId, String slugFilter) {
        String sql = "SELECT p.id, p.name, p.slug, p.sku, p.description, p.product_type, "
                + "pr.amount AS price, pr.compare_at_amount AS compare_at_price, pr.currency "
                + "FROM commerce_products p "
                + "LEFT JOIN LATERAL (SELECT amount, compare_at_amount, currency FROM commerce_prices "
                + "    WHERE tenant_id = p.tenant_id AND product_id = p.id AND status = 'ACTIVE' "
                + "    ORDER BY created_at DESC LIMIT 1) pr ON TRUE "
                + "WHERE p.tenant_id = ? AND p.store_id = ? AND p.status = 'PUBLISHED'"
                + (slugFilter != null ? " AND p.slug = ?" : "")
                + " ORDER BY p.published_at DESC NULLS LAST, p.created_at DESC";
        if (slugFilter != null) {
            return jdbc.query(sql, (rs, rowNum) -> new PublicProductResponse(
                    rs.getObject("id", UUID.class), rs.getString("name"), rs.getString("slug"),
                    rs.getString("sku"), rs.getString("description"),
                    CommerceDomain.ProductType.valueOf(rs.getString("product_type")),
                    rs.getString("currency"), rs.getBigDecimal("price"),
                    rs.getBigDecimal("compare_at_price")),
                    tenantId, storeId, slugFilter);
        }
        return jdbc.query(sql, (rs, rowNum) -> new PublicProductResponse(
                rs.getObject("id", UUID.class), rs.getString("name"), rs.getString("slug"),
                rs.getString("sku"), rs.getString("description"),
                CommerceDomain.ProductType.valueOf(rs.getString("product_type")),
                rs.getString("currency"), rs.getBigDecimal("price"),
                rs.getBigDecimal("compare_at_price")),
                tenantId, storeId);
    }

    private List<PublicCollectionResponse> listPublicCollections(UUID tenantId, UUID storeId) {
        return jdbc.query(
                "SELECT id, name, slug, description, sort_order "
                        + "FROM commerce_collections WHERE tenant_id = ? AND store_id = ? AND status = 'PUBLISHED' "
                        + "ORDER BY sort_order, created_at",
                (rs, rowNum) -> {
                    UUID collectionId = rs.getObject("id", UUID.class);
                    List<PublicProductResponse> products = listProductsForCollection(tenantId, collectionId);
                    return new PublicCollectionResponse(
                            collectionId, rs.getString("name"), rs.getString("slug"),
                            rs.getString("description"), rs.getInt("sort_order"), products);
                },
                tenantId, storeId);
    }

    private List<PublicProductResponse> listProductsForCollection(UUID tenantId, UUID collectionId) {
        return jdbc.query(
                "SELECT p.id, p.name, p.slug, p.sku, p.description, p.product_type, "
                        + "pr.amount AS price, pr.compare_at_amount AS compare_at_price, pr.currency "
                        + "FROM commerce_collection_products cp "
                        + "JOIN commerce_products p ON p.id = cp.product_id AND p.tenant_id = cp.tenant_id "
                        + "LEFT JOIN LATERAL (SELECT amount, compare_at_amount, currency FROM commerce_prices "
                        + "    WHERE tenant_id = p.tenant_id AND product_id = p.id AND status = 'ACTIVE' "
                        + "    ORDER BY created_at DESC LIMIT 1) pr ON TRUE "
                        + "WHERE cp.tenant_id = ? AND cp.collection_id = ? AND p.status = 'PUBLISHED' "
                        + "ORDER BY cp.sort_order",
                (rs, rowNum) -> new PublicProductResponse(
                        rs.getObject("id", UUID.class), rs.getString("name"), rs.getString("slug"),
                        rs.getString("sku"), rs.getString("description"),
                        CommerceDomain.ProductType.valueOf(rs.getString("product_type")),
                        rs.getString("currency"), rs.getBigDecimal("price"),
                        rs.getBigDecimal("compare_at_price")),
                tenantId, collectionId);
    }
}
