package com.sanad.platform.integration.zid.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.sanad.platform.integration.zid.config.ZidIntegrationProperties;
import com.sanad.platform.integration.zid.domain.ZidConnectionRecord;
import com.sanad.platform.integration.zid.domain.ZidIntegrationException;
import com.sanad.platform.integration.zid.infrastructure.ZidConnector;
import com.sanad.platform.integration.zid.infrastructure.ZidConnectionRepository;
import com.sanad.platform.integration.zid.infrastructure.ZidStockMirrorRepository;
import com.sanad.platform.security.rls.TenantRlsTransactionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Pulls the product catalog from the VERIFIED products endpoint
 * ({@code GET /v1/products/}, scope products.read) and upserts the
 * SANAD mirror idempotently — repeated runs never create duplicates
 * (UNIQUE (tenant_id, connection_id, zid_product_id) + ON CONFLICT).
 *
 * <p>Per-product {@code stocks[]} arrays embedded in the verified
 * products payload are upserted into {@code zid_product_stocks} by
 * {@link ZidInventorySyncService}. Deletion semantics: Zid exposes
 * {@code is_draft} and {@code is_published}; the mirror records these
 * flags verbatim. Physical rows are never deleted on pull — the
 * reconciliation service flags vanished products as drift.</p>
 */
@Service
public class ZidProductSyncService {

    private static final Logger log = LoggerFactory.getLogger(ZidProductSyncService.class);

    private final ZidConnector connector;
    private final ZidConnectionRepository connections;
    private final ZidAuthService authService;
    private final NamedParameterJdbcTemplate jdbc;
    private final ZidIntegrationProperties properties;
    private final ZidSyncAuditService auditService;
    private final ZidStockMirrorRepository stockMirror;
    private final TenantRlsTransactionContext tenantRlsContext;

    public ZidProductSyncService(ZidConnector connector,
                                 ZidConnectionRepository connections,
                                 ZidAuthService authService,
                                 NamedParameterJdbcTemplate jdbc,
                                 ZidIntegrationProperties properties,
                                 ZidSyncAuditService auditService,
                                 ZidStockMirrorRepository stockMirror,
                                 TenantRlsTransactionContext tenantRlsContext) {
        this.connector = connector;
        this.connections = connections;
        this.authService = authService;
        this.jdbc = jdbc;
        this.properties = properties;
        this.auditService = auditService;
        this.stockMirror = stockMirror;
        this.tenantRlsContext = tenantRlsContext;
    }

    public record SyncResult(int processed, int failed, Instant completedAt) {
    }

    /**
     * Background/manual entry point — establishes the trusted tenant
     * scope explicitly (worker context) and runs the full pull.
     */
    @Transactional
    public SyncResult syncConnection(UUID connectionId) {
        ZidConnectionRecord connection = connections.findById(connectionId)
                .orElseThrow(() -> new ZidIntegrationException(
                        ZidIntegrationException.Reason.TRUSTED_BINDING_NOT_FOUND,
                        "Connection not found"));
        tenantRlsContext.applyForCurrentTransaction(connection.tenantId());
        return sync(connection);
    }

    /** Request-scoped entry (GUC already applied by the DataSource proxy). */
    public SyncResult sync(ZidConnectionRecord connection) {
        Instant startedAt = Instant.now();
        int processed = 0;
        int failed = 0;
        String failureSummary = null;

        ZidAuthService.ZidCredentials credentials = authService.decryptCredentials(connection);
        Map<String, String> query = new LinkedHashMap<>();
        query.put("page", "1");
        query.put("page_size", String.valueOf(properties.getPageSize()));

        int page = 1;
        boolean more = true;
        while (more && page <= properties.getMaxPagesPerSync()) {
            query.put("page", String.valueOf(page));
            ZidConnector.ZidResponse response = connector.get(
                    credentials.toConnectionCredentials(), "products/", query);
            if (!response.isSuccess() || response.body() == null) {
                failed++;
                failureSummary = "products pull failed on page " + page
                        + " (status " + response.statusCode() + ")";
                break;
            }
            JsonNode results = response.body().path("results");
            if (!results.isArray() || results.isEmpty()) {
                break;
            }
            for (JsonNode product : results) {
                try {
                    upsertProduct(connection, product);
                    processed++;
                } catch (RuntimeException e) {
                    failed++;
                    log.warn("Zid product upsert failed for store {}: skipping item",
                            connection.zidStoreId());
                }
            }
            // The verified payload carries a "next" URL; when absent or null the collection ended.
            String next = response.body().path("next").asText(null);
            more = next != null && !next.isBlank();
            page++;
        }

        connections.markSynced(connection.id(), Instant.now());
        auditService.record(connection.tenantId(), connection.id(),
                ZidSyncAuditService.SyncType.PRODUCTS, "products",
                ZidSyncAuditService.Direction.PULL,
                failed == 0 ? ZidSyncAuditService.Status.SUCCESS
                        : (processed > 0 ? ZidSyncAuditService.Status.PARTIAL : ZidSyncAuditService.Status.FAILED),
                processed, failed, failureSummary, null);
        return new SyncResult(processed, failed, Instant.now());
    }

    void upsertProduct(ZidConnectionRecord connection, JsonNode product) {
        String zidProductId = product.path("id").asText(null);
        if (zidProductId == null || zidProductId.isBlank()) {
            throw new ZidIntegrationException(ZidIntegrationException.Reason.TRANSIENT_FAILURE,
                    "Product payload missing id");
        }
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("tenantId", connection.tenantId())
                .addValue("connectionId", connection.id())
                .addValue("zidProductId", zidProductId)
                .addValue("sku", textOrNull(product, "sku"))
                .addValue("nameAr", product.path("name").path("ar").asText(null))
                .addValue("nameEn", product.path("name").path("en").asText(null))
                .addValue("price", decimalOrNull(product, "price"))
                .addValue("salePrice", decimalOrNull(product, "sale_price"))
                .addValue("currency", textOrNull(product, "currency"))
                .addValue("isDraft", product.path("is_draft").asBoolean(false))
                .addValue("isPublished", product.path("is_published").asBoolean(false))
                .addValue("isInfinite", product.path("is_infinite").asBoolean(false))
                .addValue("quantity", product.path("quantity").isNumber()
                        ? product.path("quantity").asInt() : null)
                .addValue("zidUpdatedAt", timestampOrNull(product, "updated_at"))
                .addValue("rawPayload", product.toString());

        jdbc.update("""
                INSERT INTO zid_products
                    (tenant_id, connection_id, zid_product_id, sku, name_ar, name_en,
                     price, sale_price, currency, is_draft, is_published, is_infinite,
                     quantity, zid_updated_at, raw_payload, created_at, updated_at)
                VALUES
                    (:tenantId, :connectionId, :zidProductId, :sku, :nameAr, :nameEn,
                     :price, :salePrice, :currency, :isDraft, :isPublished, :isInfinite,
                     :quantity, :zidUpdatedAt, :rawPayload::jsonb, NOW(), NOW())
                ON CONFLICT (tenant_id, connection_id, zid_product_id) DO UPDATE SET
                    sku = EXCLUDED.sku,
                    name_ar = EXCLUDED.name_ar,
                    name_en = EXCLUDED.name_en,
                    price = EXCLUDED.price,
                    sale_price = EXCLUDED.sale_price,
                    currency = EXCLUDED.currency,
                    is_draft = EXCLUDED.is_draft,
                    is_published = EXCLUDED.is_published,
                    is_infinite = EXCLUDED.is_infinite,
                    quantity = EXCLUDED.quantity,
                    zid_updated_at = EXCLUDED.zid_updated_at,
                    raw_payload = EXCLUDED.raw_payload,
                    updated_at = NOW()
                """, params);

        // The verified products payload embeds per-product stocks[] — mirror them (pull-only).
        JsonNode stocks = product.path("stocks");
        if (stocks.isArray()) {
            for (JsonNode stock : stocks) {
                stockMirror.upsert(connection.tenantId(), connection.id(), zidProductId, stock);
            }
        }
    }

    static String textOrNull(JsonNode node, String field) {
        String value = node.path(field).asText(null);
        return value == null || value.isBlank() ? null : value;
    }

    static java.math.BigDecimal decimalOrNull(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isNumber() ? value.decimalValue() : null;
    }

    static Timestamp timestampOrNull(JsonNode node, String field) {
        String value = node.path(field).asText(null);
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Timestamp.from(Instant.parse(value));
        } catch (RuntimeException e) {
            return null;
        }
    }
}
