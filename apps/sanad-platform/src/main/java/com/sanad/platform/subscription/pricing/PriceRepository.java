package com.sanad.platform.subscription.pricing;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * JdbcTemplate repository for {@link PriceEntity}.
 */
@Repository
public class PriceRepository {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final TypeReference<List<PriceTier>> TIER_LIST = new TypeReference<>() {};

    static final RowMapper<PriceEntity> ROW_MAPPER = (rs, rowNum) -> {
        PriceEntity p = new PriceEntity();
        p.setId(rs.getObject("id", UUID.class));
        p.setPlanVersionId(rs.getObject("plan_version_id", UUID.class));
        p.setProductId(rs.getObject("product_id", UUID.class));
        p.setPriceModel(rs.getString("price_model"));
        p.setCountryCode(rs.getString("country_code"));
        p.setCurrencyCode(rs.getString("currency_code"));
        p.setBillingInterval(rs.getString("billing_interval"));
        p.setBaseAmountMinor(rs.getLong("base_amount_minor"));
        long unit = rs.getLong("unit_amount_minor");
        p.setUnitAmountMinor(rs.wasNull() ? null : unit);
        p.setTiersJson(rs.getString("tiers"));
        long min = rs.getLong("min_amount_minor");
        p.setMinAmountMinor(rs.wasNull() ? null : min);
        long max = rs.getLong("max_amount_minor");
        p.setMaxAmountMinor(rs.wasNull() ? null : max);
        Timestamp effectiveFrom = rs.getTimestamp("effective_from");
        Timestamp effectiveTo = rs.getTimestamp("effective_to");
        p.setEffectiveFrom(effectiveFrom != null ? effectiveFrom.toInstant() : null);
        p.setEffectiveTo(effectiveTo != null ? effectiveTo.toInstant() : null);
        Timestamp createdAt = rs.getTimestamp("created_at");
        Timestamp updatedAt = rs.getTimestamp("updated_at");
        p.setCreatedAt(createdAt != null ? createdAt.toInstant() : null);
        p.setUpdatedAt(updatedAt != null ? updatedAt.toInstant() : null);
        return p;
    };

    private final JdbcTemplate jdbc;

    public PriceRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * All prices for an owner (plan version OR product) + billing interval whose
     * effective window covers {@code at}, newest-effective first.
     */
    @Transactional(readOnly = true)
    public List<PriceEntity> findEffective(UUID planVersionId, UUID productId, String billingInterval, Instant at) {
        if (planVersionId != null) {
            return jdbc.query("""
                            SELECT * FROM prices
                            WHERE plan_version_id = ? AND billing_interval = ?
                              AND effective_from <= ? AND (effective_to IS NULL OR effective_to > ?)
                            ORDER BY effective_from DESC
                            """,
                    ROW_MAPPER, planVersionId, billingInterval, Timestamp.from(at), Timestamp.from(at));
        }
        return jdbc.query("""
                        SELECT * FROM prices
                        WHERE product_id = ? AND billing_interval = ?
                          AND effective_from <= ? AND (effective_to IS NULL OR effective_to > ?)
                        ORDER BY effective_from DESC
                        """,
                ROW_MAPPER, productId, billingInterval, Timestamp.from(at), Timestamp.from(at));
    }

    @Transactional(readOnly = true)
    public List<PriceEntity> findByPlanVersion(UUID planVersionId) {
        return jdbc.query(
                "SELECT * FROM prices WHERE plan_version_id = ? ORDER BY country_code, billing_interval",
                ROW_MAPPER, planVersionId);
    }

    @Transactional(readOnly = true)
    public List<PriceEntity> findByProduct(UUID productId) {
        return jdbc.query(
                "SELECT * FROM prices WHERE product_id = ? ORDER BY country_code, billing_interval",
                ROW_MAPPER, productId);
    }

    @Transactional
    public void insert(PriceEntity p) {
        jdbc.update("""
                        INSERT INTO prices (
                            id, plan_version_id, product_id, price_model, country_code,
                            currency_code, billing_interval, base_amount_minor, unit_amount_minor,
                            tiers, min_amount_minor, max_amount_minor,
                            effective_from, effective_to, created_at, updated_at
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?, ?)
                        """,
                p.getId(), p.getPlanVersionId(), p.getProductId(), p.getPriceModel(),
                p.getCountryCode(), p.getCurrencyCode(), p.getBillingInterval(),
                p.getBaseAmountMinor(), p.getUnitAmountMinor(), p.getTiersJson(),
                p.getMinAmountMinor(), p.getMaxAmountMinor(),
                p.getEffectiveFrom() == null ? Timestamp.from(Instant.now()) : Timestamp.from(p.getEffectiveFrom()),
                p.getEffectiveTo() == null ? null : Timestamp.from(p.getEffectiveTo()),
                Timestamp.from(Instant.now()), Timestamp.from(Instant.now()));
    }

    /** Parses the tiers JSONB column; empty list when absent. */
    public static List<PriceTier> parseTiers(String tiersJson) {
        if (tiersJson == null || tiersJson.isBlank()) {
            return List.of();
        }
        try {
            return JSON.readValue(tiersJson, TIER_LIST);
        } catch (Exception e) {
            return List.of();
        }
    }

    public static String tiersToJson(List<PriceTier> tiers) {
        if (tiers == null || tiers.isEmpty()) {
            return null;
        }
        try {
            return JSON.writeValueAsString(tiers);
        } catch (Exception e) {
            return null;
        }
    }
}
