package com.sanad.platform.subscription.item;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * JdbcTemplate repository for {@link SubscriptionItemEntity}.
 */
@Repository
public class SubscriptionItemRepository {

    static final RowMapper<SubscriptionItemEntity> ROW_MAPPER = (rs, rowNum) -> {
        SubscriptionItemEntity e = new SubscriptionItemEntity();
        e.setId(rs.getObject("id", UUID.class));
        e.setTenantId(rs.getObject("tenant_id", UUID.class));
        e.setSubscriptionId(rs.getObject("subscription_id", UUID.class));
        e.setItemType(rs.getString("item_type"));
        e.setApplicationId(rs.getObject("application_id", UUID.class));
        e.setProductId(rs.getObject("product_id", UUID.class));
        e.setPlanId(rs.getObject("plan_id", UUID.class));
        e.setPlanVersionId(rs.getObject("plan_version_id", UUID.class));
        e.setNameSnapshot(rs.getString("name_snapshot"));
        e.setQuantity(rs.getInt("quantity"));
        long unitAmount = rs.getLong("unit_amount_minor");
        e.setUnitAmountMinor(rs.wasNull() ? null : unitAmount);
        e.setCurrencyCode(rs.getString("currency_code"));
        e.setStatus(rs.getString("status"));
        Timestamp createdAt = rs.getTimestamp("created_at");
        Timestamp updatedAt = rs.getTimestamp("updated_at");
        e.setCreatedAt(createdAt != null ? createdAt.toInstant() : null);
        e.setUpdatedAt(updatedAt != null ? updatedAt.toInstant() : null);
        return e;
    };

    private final JdbcTemplate jdbc;

    public SubscriptionItemRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public List<SubscriptionItemEntity> findBySubscriptionId(UUID subscriptionId) {
        return jdbc.query(
                "SELECT * FROM subscription_items WHERE subscription_id = ? ORDER BY created_at, id",
                ROW_MAPPER, subscriptionId);
    }

    @Transactional(readOnly = true)
    public Optional<SubscriptionItemEntity> findById(UUID id) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                    "SELECT * FROM subscription_items WHERE id = ?", ROW_MAPPER, id));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Transactional(readOnly = true)
    public Optional<SubscriptionItemEntity> findActiveBySubscriptionIdAndType(UUID subscriptionId, String itemType) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                    "SELECT * FROM subscription_items WHERE subscription_id = ? AND item_type = ? AND status = 'ACTIVE'",
                    ROW_MAPPER, subscriptionId, itemType));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Transactional
    public void insert(SubscriptionItemEntity e) {
        jdbc.update("""
                        INSERT INTO subscription_items (
                            id, tenant_id, subscription_id, item_type, application_id, product_id,
                            plan_id, plan_version_id, name_snapshot, quantity, unit_amount_minor,
                            currency_code, status, created_at, updated_at
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                e.getId(), e.getTenantId(), e.getSubscriptionId(), e.getItemType(),
                e.getApplicationId(), e.getProductId(), e.getPlanId(), e.getPlanVersionId(),
                e.getNameSnapshot(), e.getQuantity(), e.getUnitAmountMinor(),
                e.getCurrencyCode(), e.getStatus(),
                Timestamp.from(e.getCreatedAt()), Timestamp.from(e.getUpdatedAt()));
    }

    @Transactional
    public void updateStatus(UUID id, String status) {
        jdbc.update("UPDATE subscription_items SET status = ?, updated_at = ? WHERE id = ?",
                status, Timestamp.from(java.time.Instant.now()), id);
    }

    @Transactional
    public void updateQuantityAndAmount(UUID id, int quantity, Long unitAmountMinor) {
        jdbc.update("UPDATE subscription_items SET quantity = ?, unit_amount_minor = ?, updated_at = ? WHERE id = ?",
                quantity, unitAmountMinor, Timestamp.from(java.time.Instant.now()), id);
    }
}
