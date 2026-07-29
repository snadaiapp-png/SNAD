package com.sanad.platform.crm.ownership.infrastructure;

import com.sanad.platform.crm.ownership.domain.workload.WorkloadAssignment;
import com.sanad.platform.crm.ownership.domain.workload.WorkloadRepository;
import com.sanad.platform.crm.ownership.domain.workload.WorkloadStatus;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.sanad.platform.crm.ownership.infrastructure.OwnershipJdbcSupport.workloadAssignmentMapper;

@Repository
public class JdbcWorkloadRepository implements WorkloadRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcWorkloadRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<WorkloadAssignment> findById(UUID tenantId, UUID id) {
        try {
            return Optional.ofNullable(jdbc.queryForObject("""
                    SELECT * FROM crm_workload_assignments
                     WHERE tenant_id=:tenantId AND id=:id
                    """, new MapSqlParameterSource()
                    .addValue("tenantId", tenantId)
                    .addValue("id", id), workloadAssignmentMapper()));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Override
    public List<WorkloadAssignment> findByStaffId(UUID tenantId, UUID staffId, WorkloadStatus status) {
        return jdbc.query("""
                SELECT * FROM crm_workload_assignments
                 WHERE tenant_id=:tenantId AND staff_id=:staffId AND status=:status
                 ORDER BY start_date, id
                """, new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("staffId", staffId)
                .addValue("status", status.name()), workloadAssignmentMapper());
    }

    @Override
    public List<WorkloadAssignment> findByServiceId(UUID tenantId, UUID serviceId) {
        return jdbc.query("""
                SELECT * FROM crm_workload_assignments
                 WHERE tenant_id=:tenantId AND service_id=:serviceId
                 ORDER BY start_date, id
                """, new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("serviceId", serviceId), workloadAssignmentMapper());
    }

    @Override
    @Transactional
    public WorkloadAssignment create(CreateWorkloadCommand cmd) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO crm_workload_assignments
                  (id, tenant_id, staff_id, service_id, job_id,
                   estimated_hours, actual_hours, status, start_date, end_date,
                   created_by, updated_by, created_at, updated_at, version)
                VALUES
                  (:id, :tenantId, :staffId, :serviceId, :jobId,
                   :estimatedHours, NULL, 'PLANNED', :startDate, :endDate,
                   :createdBy, :createdBy, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 1)
                """, new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("tenantId", cmd.tenantId())
                .addValue("staffId", cmd.staffId())
                .addValue("serviceId", cmd.serviceId())
                .addValue("jobId", cmd.jobId())
                .addValue("estimatedHours", cmd.estimatedHours())
                .addValue("startDate", cmd.startDate())
                .addValue("endDate", cmd.endDate())
                .addValue("createdBy", cmd.createdBy()));
        return findById(cmd.tenantId(), id).orElseThrow();
    }

    @Override
    @Transactional
    public Optional<WorkloadAssignment> update(UUID tenantId, UUID id, UpdateWorkloadCommand cmd) {
        int rows = jdbc.update("""
                UPDATE crm_workload_assignments
                   SET actual_hours=:actualHours, status=:status, end_date=:endDate,
                       updated_by=:updatedBy, updated_at=CURRENT_TIMESTAMP,
                       version=version+1
                 WHERE id=:id AND tenant_id=:tenantId AND version=:expectedVersion
                """, new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("tenantId", tenantId)
                .addValue("actualHours", cmd.actualHours())
                .addValue("status", cmd.status().name())
                .addValue("endDate", cmd.endDate())
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
                DELETE FROM crm_workload_assignments
                 WHERE tenant_id=:tenantId AND id=:id
                """, new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("id", id));
        return rows == 1;
    }

    @Override
    public int sumEstimatedHoursByStaff(UUID tenantId, UUID staffId, LocalDate from, LocalDate to) {
        Long sum = jdbc.queryForObject("""
                SELECT COALESCE(SUM(estimated_hours), 0) FROM crm_workload_assignments
                 WHERE tenant_id=:tenantId AND staff_id=:staffId
                   AND status IN ('PLANNED', 'IN_PROGRESS')
                   AND start_date >= :from AND start_date <= :to
                """, new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("staffId", staffId)
                .addValue("from", from)
                .addValue("to", to), Long.class);
        return sum != null ? sum.intValue() : 0;
    }

    @Override
    public int sumActualHoursByStaff(UUID tenantId, UUID staffId, LocalDate from, LocalDate to) {
        Long sum = jdbc.queryForObject("""
                SELECT COALESCE(SUM(actual_hours), 0) FROM crm_workload_assignments
                 WHERE tenant_id=:tenantId AND staff_id=:staffId
                   AND status IN ('IN_PROGRESS', 'COMPLETED')
                   AND start_date >= :from AND start_date <= :to
                """, new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("staffId", staffId)
                .addValue("from", from)
                .addValue("to", to), Long.class);
        return sum != null ? sum.intValue() : 0;
    }
}
