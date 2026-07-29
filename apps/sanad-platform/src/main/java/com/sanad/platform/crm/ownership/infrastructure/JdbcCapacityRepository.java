package com.sanad.platform.crm.ownership.infrastructure;

import com.sanad.platform.crm.ownership.domain.capacity.CapacityPlan;
import com.sanad.platform.crm.ownership.domain.capacity.CapacityRepository;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.sanad.platform.crm.ownership.infrastructure.OwnershipJdbcSupport.capacityPlanMapper;

@Repository
public class JdbcCapacityRepository implements CapacityRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcCapacityRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<CapacityPlan> findById(UUID tenantId, UUID id) {
        try {
            return Optional.ofNullable(jdbc.queryForObject("""
                    SELECT * FROM crm_capacity_plans
                     WHERE tenant_id=:tenantId AND id=:id
                    """, new MapSqlParameterSource()
                    .addValue("tenantId", tenantId)
                    .addValue("id", id), capacityPlanMapper()));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Override
    public List<CapacityPlan> findByTeamId(UUID tenantId, UUID teamId) {
        return jdbc.query("""
                SELECT * FROM crm_capacity_plans
                 WHERE tenant_id=:tenantId AND team_id=:teamId
                 ORDER BY period_start DESC, id
                """, new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("teamId", teamId), capacityPlanMapper());
    }

    @Override
    public Optional<CapacityPlan> findActiveByTeamAndPeriod(UUID tenantId, UUID teamId, LocalDate date) {
        try {
            return Optional.ofNullable(jdbc.queryForObject("""
                    SELECT * FROM crm_capacity_plans
                     WHERE tenant_id=:tenantId AND team_id=:teamId
                       AND status='ACTIVE'
                       AND period_start <= :date AND period_end >= :date
                     ORDER BY period_start DESC
                     LIMIT 1
                    """, new MapSqlParameterSource()
                    .addValue("tenantId", tenantId)
                    .addValue("teamId", teamId)
                    .addValue("date", date), capacityPlanMapper()));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Override
    @Transactional
    public CapacityPlan create(CreateCapacityPlanCommand cmd) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO crm_capacity_plans
                  (id, tenant_id, team_id, period_start, period_end,
                   max_capacity, allocated_capacity, status,
                   created_by, updated_by, created_at, updated_at, version)
                VALUES
                  (:id, :tenantId, :teamId, :periodStart, :periodEnd,
                   :maxCapacity, 0, 'DRAFT',
                   :createdBy, :createdBy, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 1)
                """, new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("tenantId", cmd.tenantId())
                .addValue("teamId", cmd.teamId())
                .addValue("periodStart", cmd.periodStart())
                .addValue("periodEnd", cmd.periodEnd())
                .addValue("maxCapacity", cmd.maxCapacity())
                .addValue("createdBy", cmd.createdBy()));
        return findById(cmd.tenantId(), id).orElseThrow();
    }

    @Override
    @Transactional
    public Optional<CapacityPlan> update(UUID tenantId, UUID id, UpdateCapacityPlanCommand cmd) {
        int rows = jdbc.update("""
                UPDATE crm_capacity_plans
                   SET max_capacity=:maxCapacity, allocated_capacity=:allocatedCapacity,
                       status=:status,
                       updated_by=:updatedBy, updated_at=CURRENT_TIMESTAMP,
                       version=version+1
                 WHERE id=:id AND tenant_id=:tenantId AND version=:expectedVersion
                """, new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("tenantId", tenantId)
                .addValue("maxCapacity", cmd.maxCapacity())
                .addValue("allocatedCapacity", cmd.allocatedCapacity())
                .addValue("status", cmd.status().name())
                .addValue("updatedBy", cmd.updatedBy())
                .addValue("expectedVersion", cmd.expectedVersion()));
        if (rows != 1) {
            return Optional.empty();
        }
        return findById(tenantId, id);
    }
}
