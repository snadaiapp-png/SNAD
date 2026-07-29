package com.sanad.platform.crm.ownership.infrastructure;

import com.sanad.platform.crm.ownership.domain.service.ServiceAssignment;
import com.sanad.platform.crm.ownership.domain.service.ServiceAssignmentRepository;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.sanad.platform.crm.ownership.infrastructure.OwnershipJdbcSupport.serviceAssignmentMapper;

@Repository
public class JdbcServiceAssignmentRepository implements ServiceAssignmentRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcServiceAssignmentRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<ServiceAssignment> findById(UUID tenantId, UUID id) {
        try {
            return Optional.ofNullable(jdbc.queryForObject("""
                    SELECT * FROM crm_service_assignments
                     WHERE tenant_id=:tenantId AND id=:id
                    """, new MapSqlParameterSource()
                    .addValue("tenantId", tenantId)
                    .addValue("id", id), serviceAssignmentMapper()));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Override
    public List<ServiceAssignment> findByTeamId(UUID tenantId, UUID teamId) {
        return jdbc.query("""
                SELECT * FROM crm_service_assignments
                 WHERE tenant_id=:tenantId AND team_id=:teamId
                 ORDER BY id
                """, new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("teamId", teamId), serviceAssignmentMapper());
    }

    @Override
    public List<ServiceAssignment> findByServiceId(UUID tenantId, UUID serviceId) {
        return jdbc.query("""
                SELECT * FROM crm_service_assignments
                 WHERE tenant_id=:tenantId AND service_id=:serviceId
                 ORDER BY id
                """, new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("serviceId", serviceId), serviceAssignmentMapper());
    }

    @Override
    @Transactional
    public ServiceAssignment create(CreateServiceAssignmentCommand cmd) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO crm_service_assignments
                  (id, tenant_id, team_id, service_id, status,
                   created_by, updated_by, created_at, updated_at, version)
                VALUES
                  (:id, :tenantId, :teamId, :serviceId, 'ACTIVE',
                   :createdBy, :createdBy, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 1)
                """, new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("tenantId", cmd.tenantId())
                .addValue("teamId", cmd.teamId())
                .addValue("serviceId", cmd.serviceId())
                .addValue("createdBy", cmd.createdBy()));
        return findById(cmd.tenantId(), id).orElseThrow();
    }

    @Override
    @Transactional
    public Optional<ServiceAssignment> update(UUID tenantId, UUID id, UpdateServiceAssignmentCommand cmd) {
        int rows = jdbc.update("""
                UPDATE crm_service_assignments
                   SET status=:status,
                       updated_by=:updatedBy, updated_at=CURRENT_TIMESTAMP,
                       version=version+1
                 WHERE id=:id AND tenant_id=:tenantId AND version=:expectedVersion
                """, new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("tenantId", tenantId)
                .addValue("status", cmd.status().name())
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
                DELETE FROM crm_service_assignments
                 WHERE tenant_id=:tenantId AND id=:id
                """, new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("id", id));
        return rows == 1;
    }

    @Override
    public boolean existsByTeamAndService(UUID tenantId, UUID teamId, UUID serviceId, UUID excludeId) {
        String sql = excludeId != null
                ? "SELECT COUNT(*) FROM crm_service_assignments WHERE tenant_id=:tenantId AND team_id=:teamId AND service_id=:serviceId AND id<>:excludeId"
                : "SELECT COUNT(*) FROM crm_service_assignments WHERE tenant_id=:tenantId AND team_id=:teamId AND service_id=:serviceId";
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("teamId", teamId)
                .addValue("serviceId", serviceId);
        if (excludeId != null) params.addValue("excludeId", excludeId);
        Long count = jdbc.queryForObject(sql, params, Long.class);
        return count != null && count > 0;
    }
}
