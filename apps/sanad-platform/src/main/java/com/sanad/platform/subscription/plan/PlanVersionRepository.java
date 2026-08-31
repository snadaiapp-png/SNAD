package com.sanad.platform.subscription.plan;

import org.springframework.dao.EmptyResultDataAccessException;
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
 * JdbcTemplate repository for {@link PlanVersionEntity}.
 */
@Repository
public class PlanVersionRepository {

    static final RowMapper<PlanVersionEntity> ROW_MAPPER = (rs, rowNum) -> {
        PlanVersionEntity v = new PlanVersionEntity();
        v.setId(rs.getObject("id", UUID.class));
        v.setPlanId(rs.getObject("plan_id", UUID.class));
        v.setVersionNumber(rs.getInt("version_number"));
        v.setStatus(rs.getString("status"));
        Timestamp effectiveFrom = rs.getTimestamp("effective_from");
        Timestamp effectiveTo = rs.getTimestamp("effective_to");
        v.setEffectiveFrom(effectiveFrom != null ? effectiveFrom.toInstant() : null);
        v.setEffectiveTo(effectiveTo != null ? effectiveTo.toInstant() : null);
        v.setCurrencyCode(rs.getString("currency_code"));
        v.setMonthlyPriceMinor(rs.getLong("monthly_price_minor"));
        v.setAnnualPriceMinor(rs.getLong("annual_price_minor"));
        v.setTrialDays(rs.getInt("trial_days"));
        v.setMaxUsers(rs.getInt("max_users"));
        v.setMaxOrganizations(rs.getInt("max_organizations"));
        v.setStorageMb(rs.getLong("storage_mb"));
        Timestamp createdAt = rs.getTimestamp("created_at");
        Timestamp updatedAt = rs.getTimestamp("updated_at");
        v.setCreatedAt(createdAt != null ? createdAt.toInstant() : null);
        v.setUpdatedAt(updatedAt != null ? updatedAt.toInstant() : null);
        return v;
    };

    private final JdbcTemplate jdbc;

    public PlanVersionRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public List<PlanVersionEntity> findByPlanIdOrderByVersionNumberDesc(UUID planId) {
        return jdbc.query(
                "SELECT * FROM plan_versions WHERE plan_id = ? ORDER BY version_number DESC",
                ROW_MAPPER, planId);
    }

    @Transactional(readOnly = true)
    public Optional<PlanVersionEntity> findById(UUID id) {
        try {
            return Optional.ofNullable(
                    jdbc.queryForObject("SELECT * FROM plan_versions WHERE id = ?", ROW_MAPPER, id));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Transactional(readOnly = true)
    public Optional<PlanVersionEntity> findActiveByPlanId(UUID planId) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                    "SELECT * FROM plan_versions WHERE plan_id = ? AND status = 'ACTIVE'",
                    ROW_MAPPER, planId));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Transactional
    public void insert(PlanVersionEntity v) {
        jdbc.update("""
                        INSERT INTO plan_versions (
                            id, plan_id, version_number, status, effective_from, effective_to,
                            currency_code, monthly_price_minor, annual_price_minor, trial_days,
                            max_users, max_organizations, storage_mb, created_at, updated_at
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                v.getId(), v.getPlanId(), v.getVersionNumber(), v.getStatus(),
                v.getEffectiveFrom() == null ? null : Timestamp.from(v.getEffectiveFrom()),
                v.getEffectiveTo() == null ? null : Timestamp.from(v.getEffectiveTo()),
                v.getCurrencyCode(), v.getMonthlyPriceMinor(), v.getAnnualPriceMinor(),
                v.getTrialDays(), v.getMaxUsers(), v.getMaxOrganizations(), v.getStorageMb(),
                Timestamp.from(v.getCreatedAt()), Timestamp.from(v.getUpdatedAt()));
    }

    @Transactional
    public void updateStatusAndWindow(UUID id, String status, Instant effectiveFrom, Instant effectiveTo) {
        jdbc.update("""
                        UPDATE plan_versions SET
                            status = ?,
                            effective_from = COALESCE(?, effective_from),
                            effective_to = ?,
                            updated_at = ?
                        WHERE id = ?
                        """,
                status,
                effectiveFrom == null ? null : Timestamp.from(effectiveFrom),
                effectiveTo == null ? null : Timestamp.from(effectiveTo),
                Timestamp.from(Instant.now()),
                id);
    }
}
