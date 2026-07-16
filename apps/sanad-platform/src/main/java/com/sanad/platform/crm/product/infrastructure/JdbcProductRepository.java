package com.sanad.platform.crm.product.infrastructure;

import com.sanad.platform.crm.error.CrmContractException;
import com.sanad.platform.crm.error.CrmErrorCode;
import com.sanad.platform.crm.product.domain.ProductRepository;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
public class JdbcProductRepository implements ProductRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcProductRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public ProductRecord findById(UUID tenantId, UUID productId) {
        try {
            return mapRow(jdbc.queryForMap("SELECT * FROM crm_products WHERE tenant_id = :t AND id = :id",
                    new MapSqlParameterSource().addValue("t", tenantId).addValue("id", productId)));
        } catch (org.springframework.dao.EmptyResultDataAccessException e) {
            throw new CrmContractException(CrmErrorCode.CRM_PRODUCT_NOT_FOUND);
        }
    }

    @Override
    public List<ProductRecord> findAll(UUID tenantId, int limit, String search, Boolean activeOnly, String category) {
        StringBuilder sql = new StringBuilder("SELECT * FROM crm_products WHERE tenant_id = :t");
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("t", tenantId);
        if (search != null && !search.isBlank()) {
            sql.append(" AND (LOWER(name) LIKE :s OR LOWER(COALESCE(sku, '')) LIKE :s)");
            params.addValue("s", "%" + search.toLowerCase() + "%");
        }
        if (activeOnly != null && activeOnly) { sql.append(" AND active = TRUE"); }
        if (category != null && !category.isBlank()) { sql.append(" AND category = :cat"); params.addValue("cat", category); }
        sql.append(" ORDER BY name ASC, id ASC LIMIT :limit");
        params.addValue("limit", limit);
        return jdbc.queryForList(sql.toString(), params).stream().map(this::mapRow).toList();
    }

    @Override
    public ProductRecord create(UUID tenantId, UUID actorId, CreateProductCommand cmd) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        jdbc.update(
                "INSERT INTO crm_products (id, tenant_id, version, name, sku, description, product_type, category, " +
                "  unit_price, currency_code, tax_rate, unit, active, created_by, updated_by, created_at, updated_at) " +
                "VALUES (:id, :t, 0, :name, :sku, :desc, :type, :cat, :price, :cur, :tax, :unit, TRUE, :actor, :actor, :now, :now)",
                new MapSqlParameterSource()
                        .addValue("id", id).addValue("t", tenantId)
                        .addValue("name", cmd.name())
                        .addValue("sku", cmd.sku())
                        .addValue("desc", cmd.description())
                        .addValue("type", cmd.productType() == null ? "PRODUCT" : cmd.productType().toUpperCase())
                        .addValue("cat", cmd.category())
                        .addValue("price", cmd.unitPrice() == null ? BigDecimal.ZERO : cmd.unitPrice())
                        .addValue("cur", cmd.currencyCode() == null ? "USD" : cmd.currencyCode())
                        .addValue("tax", cmd.taxRate() == null ? BigDecimal.ZERO : cmd.taxRate())
                        .addValue("unit", cmd.unit() == null ? "EA" : cmd.unit())
                        .addValue("actor", actorId).addValue("now", Timestamp.from(now)));
        return findById(tenantId, id);
    }

    @Override
    public ProductRecord update(UUID tenantId, UUID actorId, UUID productId, UpdateProductCommand cmd, long expectedVersion) {
        StringBuilder sql = new StringBuilder("UPDATE crm_products SET version = version + 1, updated_by = :actor, updated_at = :now");
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("t", tenantId).addValue("id", productId)
                .addValue("expectedVersion", expectedVersion)
                .addValue("actor", actorId).addValue("now", Timestamp.from(Instant.now()));
        if (cmd.name() != null) { sql.append(", name = :name"); params.addValue("name", cmd.name()); }
        if (cmd.sku() != null) { sql.append(", sku = :sku"); params.addValue("sku", cmd.sku()); }
        if (cmd.description() != null) { sql.append(", description = :desc"); params.addValue("desc", cmd.description()); }
        if (cmd.productType() != null) { sql.append(", product_type = :type"); params.addValue("type", cmd.productType().toUpperCase()); }
        if (cmd.category() != null) { sql.append(", category = :cat"); params.addValue("cat", cmd.category()); }
        if (cmd.unitPrice() != null) { sql.append(", unit_price = :price"); params.addValue("price", cmd.unitPrice()); }
        if (cmd.currencyCode() != null) { sql.append(", currency_code = :cur"); params.addValue("cur", cmd.currencyCode()); }
        if (cmd.taxRate() != null) { sql.append(", tax_rate = :tax"); params.addValue("tax", cmd.taxRate()); }
        if (cmd.unit() != null) { sql.append(", unit = :unit"); params.addValue("unit", cmd.unit()); }
        if (cmd.active() != null) { sql.append(", active = :active"); params.addValue("active", cmd.active()); }
        sql.append(" WHERE tenant_id = :t AND id = :id AND version = :expectedVersion");
        int updated = jdbc.update(sql.toString(), params);
        if (updated == 0) throw new CrmContractException(CrmErrorCode.CRM_CONCURRENCY_CONFLICT);
        return findById(tenantId, productId);
    }

    @Override
    public void delete(UUID tenantId, UUID actorId, UUID productId) {
        int deleted = jdbc.update("DELETE FROM crm_products WHERE tenant_id = :t AND id = :id",
                new MapSqlParameterSource().addValue("t", tenantId).addValue("id", productId));
        if (deleted == 0) throw new CrmContractException(CrmErrorCode.CRM_PRODUCT_NOT_FOUND);
    }

    private ProductRecord mapRow(Map<String, Object> r) {
        return new ProductRecord(
                (UUID) r.get("id"), asLong(r.get("version")),
                (String) r.get("name"), (String) r.get("sku"), (String) r.get("description"),
                (String) r.get("product_type"), (String) r.get("category"),
                toBigDecimal(r.get("unit_price")), (String) r.get("currency_code"),
                toBigDecimal(r.get("tax_rate")), (String) r.get("unit"),
                r.get("active") != null && ((Boolean) r.get("active")),
                asInstant(r.get("created_at")), asInstant(r.get("updated_at")));
    }

    private static long asLong(Object v) { if (v == null) return 0L; if (v instanceof Number n) return n.longValue(); try { return Long.parseLong(String.valueOf(v)); } catch (Exception e) { return 0L; } }
    private static Instant asInstant(Object v) { if (v == null) return null; if (v instanceof Timestamp t) return t.toInstant(); if (v instanceof Instant i) return i; return null; }
    private static BigDecimal toBigDecimal(Object v) { if (v == null) return BigDecimal.ZERO; if (v instanceof BigDecimal bd) return bd; if (v instanceof Number n) return BigDecimal.valueOf(n.doubleValue()); try { return new BigDecimal(String.valueOf(v)); } catch (Exception e) { return BigDecimal.ZERO; } }
}
