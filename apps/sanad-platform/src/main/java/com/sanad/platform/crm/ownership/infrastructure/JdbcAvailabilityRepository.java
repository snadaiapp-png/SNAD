package com.sanad.platform.crm.ownership.infrastructure;

import com.sanad.platform.crm.ownership.domain.availability.AvailabilityRepository;
import com.sanad.platform.crm.ownership.domain.availability.StaffAvailability;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.sanad.platform.crm.ownership.infrastructure.OwnershipJdbcSupport.staffAvailabilityMapper;

@Repository
public class JdbcAvailabilityRepository implements AvailabilityRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcAvailabilityRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<StaffAvailability> findById(UUID tenantId, UUID id) {
        try {
            return Optional.ofNullable(jdbc.queryForObject("""
                    SELECT * FROM crm_staff_availability
                     WHERE tenant_id=:tenantId AND id=:id
                    """, new MapSqlParameterSource()
                    .addValue("tenantId", tenantId)
                    .addValue("id", id), staffAvailabilityMapper()));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Override
    public List<StaffAvailability> findByStaffId(UUID tenantId, UUID staffId, LocalDate from, LocalDate to) {
        return jdbc.query("""
                SELECT * FROM crm_staff_availability
                 WHERE tenant_id=:tenantId AND staff_id=:staffId
                   AND start_date >= :from AND start_date <= :to
                 ORDER BY start_date, id
                """, new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("staffId", staffId)
                .addValue("from", from)
                .addValue("to", to), staffAvailabilityMapper());
    }

    @Override
    @Transactional
    public StaffAvailability create(CreateAvailabilityCommand cmd) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO crm_staff_availability
                  (id, tenant_id, staff_id, type, start_date, end_date,
                   start_time, end_time, reason,
                   created_by, updated_by, created_at, updated_at, version)
                VALUES
                  (:id, :tenantId, :staffId, :type, :startDate, :endDate,
                   :startTime, :endTime, :reason,
                   :createdBy, :createdBy, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 1)
                """, new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("tenantId", cmd.tenantId())
                .addValue("staffId", cmd.staffId())
                .addValue("type", cmd.type().name())
                .addValue("startDate", cmd.startDate())
                .addValue("endDate", cmd.endDate())
                .addValue("startTime", cmd.startTime())
                .addValue("endTime", cmd.endTime())
                .addValue("reason", cmd.reason())
                .addValue("createdBy", cmd.createdBy()));
        return findById(cmd.tenantId(), id).orElseThrow();
    }

    @Override
    @Transactional
    public Optional<StaffAvailability> update(UUID tenantId, UUID id, UpdateAvailabilityCommand cmd) {
        int rows = jdbc.update("""
                UPDATE crm_staff_availability
                   SET type=:type, start_date=:startDate, end_date=:endDate,
                       start_time=:startTime, end_time=:endTime, reason=:reason,
                       updated_by=:updatedBy, updated_at=CURRENT_TIMESTAMP,
                       version=version+1
                 WHERE id=:id AND tenant_id=:tenantId AND version=:expectedVersion
                """, new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("tenantId", tenantId)
                .addValue("type", cmd.type().name())
                .addValue("startDate", cmd.startDate())
                .addValue("endDate", cmd.endDate())
                .addValue("startTime", cmd.startTime())
                .addValue("endTime", cmd.endTime())
                .addValue("reason", cmd.reason())
                .addValue("updatedBy", cmd.updatedBy())
                .addValue("expectedVersion", cmd.expectedVersion()));
        if (rows != 1) {
            return Optional.empty();
        }
        return findById(tenantId, id);
    }

    @Override
    @Transactional
    public boolean delete(UUID tenantId, UUID id) {
        int rows = jdbc.update("""
                DELETE FROM crm_staff_availability
                 WHERE tenant_id=:tenantId AND id=:id
                """, new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("id", id));
        return rows == 1;
    }
}
