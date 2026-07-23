package com.sanad.platform.crm.ownership.infrastructure;

import com.sanad.platform.crm.ownership.domain.*;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import static com.sanad.platform.crm.ownership.infrastructure.OwnershipJdbcSupport.*;

@Repository
public class JdbcTerritoryAssignmentRepository implements TerritoryAssignmentRepository {
    private final NamedParameterJdbcTemplate jdbc;
    public JdbcTerritoryAssignmentRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override @Transactional
    public TerritoryAssignment save(TerritoryAssignment a) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO crm_territory_assignments
              (id, tenant_id, territory_id, assignee_type, assignee_id, role, priority, status,
               effective_from, effective_to, created_at, updated_at, created_by, updated_by)
            VALUES
              (:id, :tenantId, :territoryId, :assigneeType, :assigneeId, :role, :priority, :status,
               CURRENT_TIMESTAMP, :effectiveTo, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, :createdBy, :updatedBy)
            """, new MapSqlParameterSource()
                .addValue("id", id).addValue("tenantId", a.tenantId()).addValue("territoryId", a.territoryId())
                .addValue("assigneeType", a.assigneeType().name()).addValue("assigneeId", a.assigneeId())
                .addValue("role", a.role().name()).addValue("priority", a.priority()).addValue("status", a.status().name())
                .addValue("effectiveTo", a.effectiveTo()).addValue("createdBy", a.createdBy()).addValue("updatedBy", a.updatedBy()));
        return findById(a.tenantId(), id).orElseThrow();
    }

    @Override
    public Optional<TerritoryAssignment> findById(UUID tenantId, UUID assignmentId) {
        try {
            return Optional.ofNullable(jdbc.queryForObject("SELECT * FROM crm_territory_assignments WHERE tenant_id=:tenantId AND id=:id",
                new MapSqlParameterSource().addValue("tenantId", tenantId).addValue("id", assignmentId), territoryAssignmentMapper()));
        } catch (EmptyResultDataAccessException e) { return Optional.empty(); }
    }

    @Override
    public Optional<TerritoryAssignment> findActivePrimary(UUID tenantId, UUID territoryId, AssigneeType assigneeType) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                "SELECT * FROM crm_territory_assignments WHERE tenant_id=:tenantId AND territory_id=:territoryId AND assignee_type=:assigneeType AND status='ACTIVE' AND role='PRIMARY'",
                new MapSqlParameterSource().addValue("tenantId", tenantId).addValue("territoryId", territoryId).addValue("assigneeType", assigneeType.name()),
                territoryAssignmentMapper()));
        } catch (EmptyResultDataAccessException e) { return Optional.empty(); }
    }

    @Override
    public List<TerritoryAssignment> findActiveByTerritory(UUID tenantId, UUID territoryId) {
        return jdbc.query("SELECT * FROM crm_territory_assignments WHERE tenant_id=:tenantId AND territory_id=:territoryId AND status='ACTIVE' ORDER BY priority",
            new MapSqlParameterSource().addValue("tenantId", tenantId).addValue("territoryId", territoryId), territoryAssignmentMapper());
    }

    @Override
    public List<TerritoryAssignment> findActiveByAssignee(UUID tenantId, AssigneeType assigneeType, UUID assigneeId) {
        return jdbc.query("SELECT * FROM crm_territory_assignments WHERE tenant_id=:tenantId AND assignee_type=:assigneeType AND assignee_id=:assigneeId AND status='ACTIVE' ORDER BY priority",
            new MapSqlParameterSource().addValue("tenantId", tenantId).addValue("assigneeType", assigneeType.name()).addValue("assigneeId", assigneeId), territoryAssignmentMapper());
    }

    @Override @Transactional
    public void deactivate(UUID tenantId, UUID assignmentId, UUID updatedBy) {
        int rows = jdbc.update("UPDATE crm_territory_assignments SET status='INACTIVE', updated_at=CURRENT_TIMESTAMP, updated_by=:updatedBy WHERE tenant_id=:tenantId AND id=:id AND status='ACTIVE'",
            new MapSqlParameterSource().addValue("tenantId", tenantId).addValue("id", assignmentId).addValue("updatedBy", updatedBy));
        if (rows == 0) throw new OwnershipDomainException("Territory assignment not found or already inactive: " + assignmentId);
    }
}
