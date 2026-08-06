package com.sanad.platform.crm.ownership.infrastructure;

import com.sanad.platform.crm.ownership.domain.scheduling.ShiftTemplate;
import com.sanad.platform.crm.ownership.domain.scheduling.ShiftTemplateRepository;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.sanad.platform.crm.ownership.infrastructure.OwnershipJdbcSupport.*;

@Repository
public class JdbcShiftTemplateRepository implements ShiftTemplateRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcShiftTemplateRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<ShiftTemplate> findById(UUID tenantId, UUID id) {
        try {
            return Optional.ofNullable(jdbc.queryForObject("""
                    SELECT * FROM crm_shift_templates
                     WHERE tenant_id=:tenantId AND id=:id
                    """, new MapSqlParameterSource()
                    .addValue("tenantId", tenantId)
                    .addValue("id", id), shiftTemplateMapper()));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Override
    public List<ShiftTemplate> findAll(UUID tenantId, int limit, int offset) {
        return jdbc.query("""
                SELECT * FROM crm_shift_templates
                 WHERE tenant_id=:tenantId
                 ORDER BY name, id
                 LIMIT :limit OFFSET :offset
                """, new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("limit", limit)
                .addValue("offset", offset), shiftTemplateMapper());
    }

    @Override
    @Transactional
    public ShiftTemplate create(CreateShiftTemplateCommand cmd) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO crm_shift_templates
                  (id, tenant_id, name, start_time, end_time, days_of_week, status,
                   created_by, updated_by, created_at, updated_at, version)
                VALUES
                  (:id, :tenantId, :name, :startTime, :endTime, :daysOfWeek, 'ACTIVE',
                   :createdBy, :createdBy, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 1)
                """, new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("tenantId", cmd.tenantId())
                .addValue("name", cmd.name())
                .addValue("startTime", cmd.startTime())
                .addValue("endTime", cmd.endTime())
                .addValue("daysOfWeek", toDayOfWeekCsv(cmd.daysOfWeek()))
                .addValue("createdBy", cmd.createdBy()));
        return findById(cmd.tenantId(), id).orElseThrow();
    }

    @Override
    @Transactional
    public Optional<ShiftTemplate> update(UUID tenantId, UUID id, UpdateShiftTemplateCommand cmd) {
        int rows = jdbc.update("""
                UPDATE crm_shift_templates
                   SET name=:name, start_time=:startTime, end_time=:endTime,
                       days_of_week=:daysOfWeek, status=:status,
                       updated_by=:updatedBy, updated_at=CURRENT_TIMESTAMP,
                       version=version+1
                 WHERE id=:id AND tenant_id=:tenantId AND version=:expectedVersion
                """, new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("tenantId", tenantId)
                .addValue("name", cmd.name())
                .addValue("startTime", cmd.startTime())
                .addValue("endTime", cmd.endTime())
                .addValue("daysOfWeek", toDayOfWeekCsv(cmd.daysOfWeek()))
                .addValue("status", cmd.status().name())
                .addValue("updatedBy", cmd.updatedBy())
                .addValue("expectedVersion", cmd.expectedVersion()));
        if (rows != 1) {
            return Optional.empty();
        }
        return findById(tenantId, id);
    }

    @Override
    public boolean existsByName(UUID tenantId, String name, UUID excludeId) {
        String sql = excludeId != null
                ? "SELECT COUNT(*) FROM crm_shift_templates WHERE tenant_id=:tenantId AND name=:name AND id<>:excludeId"
                : "SELECT COUNT(*) FROM crm_shift_templates WHERE tenant_id=:tenantId AND name=:name";
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("name", name);
        if (excludeId != null) params.addValue("excludeId", excludeId);
        Long count = jdbc.queryForObject(sql, params, Long.class);
        return count != null && count > 0;
    }
}
