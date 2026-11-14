package com.sanad.platform.integration.zid.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * JDBC mirror for {@code zid_product_stocks}. PULL-ONLY: rows are
 * written exclusively from verified Zid product-payload
 * {@code stocks[]} arrays. No method here ever issues an outbound
 * write to Zid.
 */
@Repository
public class ZidStockMirrorRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public ZidStockMirrorRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Upserts one embedded stock entry; idempotent via the natural key. */
    public void upsert(UUID tenantId, UUID connectionId, String zidProductId, JsonNode stock) {
        String stockId = stock.path("id").asText(null);
        if (stockId == null || stockId.isBlank()) {
            return; // Cannot key deterministically — skipped (counted upstream).
        }
        jdbc.update("""
                INSERT INTO zid_product_stocks
                    (tenant_id, connection_id, zid_product_id, zid_stock_id,
                     location_name_ar, location_name_en, available_quantity, is_infinite,
                     synced_at, created_at, updated_at)
                VALUES
                    (:tenantId, :connectionId, :zidProductId, :zidStockId,
                     :locationAr, :locationEn, :availableQuantity, :isInfinite,
                     NOW(), NOW(), NOW())
                ON CONFLICT (tenant_id, connection_id, zid_product_id, zid_stock_id) DO UPDATE SET
                    location_name_ar = EXCLUDED.location_name_ar,
                    location_name_en = EXCLUDED.location_name_en,
                    available_quantity = EXCLUDED.available_quantity,
                    is_infinite = EXCLUDED.is_infinite,
                    synced_at = NOW(),
                    updated_at = NOW()
                """, new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("connectionId", connectionId)
                .addValue("zidProductId", zidProductId)
                .addValue("zidStockId", stockId)
                .addValue("locationAr", stock.path("location").path("name").path("ar").asText(null))
                .addValue("locationEn", stock.path("location").path("name").path("en").asText(null))
                .addValue("availableQuantity", stock.path("available_quantity").isNumber()
                        ? stock.path("available_quantity").asInt() : null)
                .addValue("isInfinite", stock.path("is_infinite").asBoolean(false)));
    }

    public int countByConnection(UUID connectionId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM zid_product_stocks WHERE connection_id = :connectionId",
                new MapSqlParameterSource("connectionId", connectionId), Integer.class);
        return count == null ? 0 : count;
    }
}
