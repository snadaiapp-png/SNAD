package com.sanad.platform.module.registry;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * JdbcTemplate-based repository for {@link PlanModuleEntitlementEntity}.
 */
@Repository
public class PlanModuleEntitlementRepository {

    private static final RowMapper<PlanModuleEntitlementEntity> ROW_MAPPER = (rs, rowNum) -> {
        PlanModuleEntitlementEntity e = new PlanModuleEntitlementEntity();
        e.setId(rs.getObject("id", UUID.class));
        e.setPlanId(rs.getObject("plan_id", UUID.class));
        e.setModuleId(rs.getObject("module_id", UUID.class));
        e.setModuleEnabled(rs.getBoolean("module_enabled"));
        e.setCapabilityCode(rs.getString("capability_code"));
        e.setCapabilityValue(rs.getString("capability_value"));
        long limitVal = rs.getLong("limit_value");
        e.setLimitValue(rs.wasNull() ? null : limitVal);
        long quotaVal = rs.getLong("quota_value");
        e.setQuotaValue(rs.wasNull() ? null : quotaVal);
        e.setQuotaPeriod(rs.getString("quota_period"));
        Timestamp effectiveAt = rs.getTimestamp("effective_at");
        Timestamp createdAt = rs.getTimestamp("created_at");
        Timestamp updatedAt = rs.getTimestamp("updated_at");
        e.setEffectiveAt(effectiveAt != null ? effectiveAt.toInstant() : null);
        e.setCreatedAt(createdAt != null ? createdAt.toInstant() : null);
        e.setUpdatedAt(updatedAt != null ? updatedAt.toInstant() : null);
        return e;
    };

    private final JdbcTemplate jdbc;

    public PlanModuleEntitlementRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public List<PlanModuleEntitlementEntity> findByPlanId(UUID planId) {
        return jdbc.query(
                "SELECT * FROM plan_module_entitlements WHERE plan_id = ? ORDER BY module_id, capability_code",
                ROW_MAPPER, planId);
    }

    @Transactional(readOnly = true)
    public List<PlanModuleEntitlementEntity> findByPlanIdAndModuleId(UUID planId, UUID moduleId) {
        return jdbc.query(
                "SELECT * FROM plan_module_entitlements WHERE plan_id = ? AND module_id = ? ORDER BY capability_code",
                ROW_MAPPER, planId, moduleId);
    }

    @Transactional(readOnly = true)
    public Optional<PlanModuleEntitlementEntity> findByPlanModuleCapability(UUID planId, UUID moduleId, String capabilityCode) {
        return jdbc.query(
                "SELECT * FROM plan_module_entitlements WHERE plan_id = ? AND module_id = ? AND capability_code = ?",
                ROW_MAPPER, planId, moduleId, capabilityCode)
                .stream().findFirst();
    }

    @Transactional
    public int deleteByPlanIdAndModuleId(UUID planId, UUID moduleId) {
        return jdbc.update(
                "DELETE FROM plan_module_entitlements WHERE plan_id = ? AND module_id = ?",
                planId, moduleId);
    }

    @Transactional
    public int insert(PlanModuleEntitlementEntity e) {
        if (e.getId() == null) {
            e.setId(UUID.randomUUID());
        }
        Instant now = Instant.now();
        if (e.getCreatedAt() == null) e.setCreatedAt(now);
        if (e.getUpdatedAt() == null) e.setUpdatedAt(now);
        if (e.getEffectiveAt() == null) e.setEffectiveAt(now);
        return jdbc.update(
                "INSERT INTO plan_module_entitlements " +
                        "(id, plan_id, module_id, module_enabled, capability_code, capability_value, " +
                        "limit_value, quota_value, quota_period, effective_at, created_at, updated_at) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                e.getId(), e.getPlanId(), e.getModuleId(), e.isModuleEnabled(),
                e.getCapabilityCode(), e.getCapabilityValue(),
                e.getLimitValue(), e.getQuotaValue(), e.getQuotaPeriod(),
                Timestamp.from(e.getEffectiveAt()),
                Timestamp.from(e.getCreatedAt()), Timestamp.from(e.getUpdatedAt()));
    }

    @Transactional
    public int update(PlanModuleEntitlementEntity e) {
        e.setUpdatedAt(Instant.now());
        return jdbc.update(
                "UPDATE plan_module_entitlements SET " +
                        "module_enabled = ?, capability_value = ?, limit_value = ?, quota_value = ?, " +
                        "quota_period = ?, effective_at = ?, updated_at = ? " +
                        "WHERE id = ?",
                e.isModuleEnabled(), e.getCapabilityValue(),
                e.getLimitValue(), e.getQuotaValue(), e.getQuotaPeriod(),
                Timestamp.from(e.getEffectiveAt() != null ? e.getEffectiveAt() : Instant.now()),
                Timestamp.from(e.getUpdatedAt()),
                e.getId());
    }
}
