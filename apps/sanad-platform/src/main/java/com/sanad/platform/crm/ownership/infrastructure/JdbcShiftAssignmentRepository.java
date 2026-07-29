package com.sanad.platform.crm.ownership.infrastructure;

import com.sanad.platform.crm.ownership.domain.scheduling.ShiftAssignment;
import com.sanad.platform.crm.ownership.domain.scheduling.ShiftAssignmentRepository;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.sanad.platform.crm.ownership.infrastructure.OwnershipJdbcSupport.shiftAssignmentMapper;

@Repository
public class JdbcShiftAssignmentRepository implements ShiftAssignmentRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcShiftAssignmentRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<ShiftAssignment> findById(UUID tenantId, UUID id) {
        try {
            return Optional.ofNullable(jdbc.queryForObject("""
                    SELECT * FROM crm_shift_assignments
                     WHERE tenant_id=:tenantId AND id=:id
                    """, new MapSqlParameterSource()
                    .addValue("tenantId", tenantId)
                    .addValue("id", id), shiftAssignmentMapper()));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Override
    public List<ShiftAssignment> findByTeamId(UUID tenantId, UUID teamId, int limit, int offset) {
        return jdbc.query("""
                SELECT * FROM crm_shift_assignments
                 WHERE tenant_id=:tenantId AND team_id=:teamId
                 ORDER BY start_date, id
                 LIMIT :limit OFFSET :offset
                """, new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("teamId", teamId)
                .addValue("limit", limit)
                .addValue("offset", offset), shiftAssignmentMapper());
    }

    @Override
    public List<ShiftAssignment> findByStaffId(UUID tenantId, UUID staffId, LocalDate from, LocalDate to) {
        return jdbc.query("""
                SELECT * FROM crm_shift_assignments
                 WHERE tenant_id=:tenantId AND staff_id=:staffId
                   AND start_date >= :from AND start_date <= :to
                 ORDER BY start_date, id
                """, new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("staffId", staffId)
                .addValue("from", from)
                .addValue("to", to), shiftAssignmentMapper());
    }

    @Override
    @Transactional
    public ShiftAssignment create(CreateShiftAssignmentCommand cmd) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO crm_shift_assignments
                  (id, tenant_id, team_id, staff_id, shift_template_id,
                   start_date, end_date, status,
                   created_by, updated_by, created_at, updated_at, version)
                VALUES
                  (:id, :tenantId, :teamId, :staffId, :shiftTemplateId,
                   :startDate, :endDate, 'SCHEDULED',
                   :createdBy, :createdBy, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 1)
                """, new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("tenantId", cmd.tenantId())
                .addValue("teamId", cmd.teamId())
                .addValue("staffId", cmd.staffId())
                .addValue("shiftTemplateId", cmd.shiftTemplateId())
                .addValue("startDate", cmd.startDate())
                .addValue("endDate", cmd.endDate())
                .addValue("createdBy", cmd.createdBy()));
        return findById(cmd.tenantId(), id).orElseThrow();
    }

    @Override
    @Transactional
    public Optional<ShiftAssignment> update(UUID tenantId, UUID id, UpdateShiftAssignmentCommand cmd) {
        int rows = jdbc.update("""
                UPDATE crm_shift_assignments
                   SET shift_template_id=:shiftTemplateId,
                       start_date=:startDate, end_date=:endDate, status=:status,
                       updated_by=:updatedBy, updated_at=CURRENT_TIMESTAMP,
                       version=version+1
                 WHERE id=:id AND tenant_id=:tenantId AND version=:expectedVersion
                """, new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("tenantId", tenantId)
                .addValue("shiftTemplateId", cmd.shiftTemplateId())
                .addValue("startDate", cmd.startDate())
                .addValue("endDate", cmd.endDate())
                .addValue("status", cmd.status().name())
                .addValue("updatedBy", cmd.updatedBy())
                .addValue("expectedVersion", cmd.expectedVersion()));
        if (rows != 1) {
            return Optional.empty();
        }
        return findById(tenantId, id);
    }

    @Override
    public boolean hasOverlap(UUID tenantId, UUID staffId, LocalDate startDate,
                              LocalDate endDate, UUID excludeId) {
        String sql = excludeId != null
                ? """
                  SELECT COUNT(*) FROM crm_shift_assignments
                   WHERE tenant_id=:tenantId AND staff_id=:staffId
                     AND status<>'CANCELLED' AND id<>:excludeId
                     AND start_date <= :endDate AND end_date >= :startDate
                  """
                : """
                  SELECT COUNT(*) FROM crm_shift_assignments
                   WHERE tenant_id=:tenantId AND staff_id=:staffId
                     AND status<>'CANCELLED'
                     AND start_date <= :endDate AND end_date >= :startDate
                  """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("staffId", staffId)
                .addValue("startDate", startDate)
                .addValue("endDate", endDate);
        if (excludeId != null) params.addValue("excludeId", excludeId);
        Long count = jdbc.queryForObject(sql, params, Long.class);
        return count != null && count > 0;
    }
}
