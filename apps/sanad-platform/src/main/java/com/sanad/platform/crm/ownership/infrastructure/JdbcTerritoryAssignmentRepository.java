package com.sanad.platform.crm.ownership.infrastructure;

import com.sanad.platform.crm.ownership.domain.AssigneeType;
import com.sanad.platform.crm.ownership.domain.OwnershipDomainException;
import com.sanad.platform.crm.ownership.domain.TerritoryAssignment;
import com.sanad.platform.crm.ownership.domain.TerritoryAssignmentRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.sanad.platform.crm.ownership.infrastructure.OwnershipJdbcSupport.territoryAssignmentMapper;

@Repository
public class JdbcTerritoryAssignmentRepository implements TerritoryAssignmentRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public JdbcTerritoryAssignmentRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public TerritoryAssignment save(TerritoryAssignment assignment) {
        UUID id = assignment.id() != null ? assignment.id() : UUID.randomUUID();
        try {
            jdbc.update("""
                    INSERT INTO crm_territory_assignments
                      (id, tenant_id, territory_id, assignee_type, assignee_id,
                       role, priority, status, effective_from, effective_to,
                       created_at, updated_at, created_by, updated_by)
                    VALUES
                      (:id, :tenantId, :territoryId, :assigneeType, :assigneeId,
                       :role, :priority, :status, COALESCE(:effectiveFrom, CURRENT_TIMESTAMP),
                       :effectiveTo, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, :createdBy, :updatedBy)
                    """, new MapSqlParameterSource()
                    .addValue("id", id)
                    .addValue("tenantId", assignment.tenantId())
                    .addValue("territoryId", assignment.territoryId())
                    .addValue("assigneeType", assignment.assigneeType().name())
                    .addValue("assigneeId", assignment.assigneeId())
                    .addValue("role", assignment.role().name())
                    .addValue("priority", assignment.priority())
                    .addValue("status", assignment.status().name())
                    .addValue("effectiveFrom", assignment.effectiveFrom() != null
                            ? Timestamp.from(assignment.effectiveFrom()) : null)
                    .addValue("effectiveTo", assignment.effectiveTo() != null
                            ? Timestamp.from(assignment.effectiveTo()) : null)
                    .addValue("createdBy", assignment.createdBy())
                    .addValue("updatedBy", assignment.updatedBy()));
        } catch (DataIntegrityViolationException conflict) {
            throw new OwnershipDomainException(
                    "Active territory assignment conflicts with an existing assignment", conflict);
        }
        return findById(assignment.tenantId(), id).orElseThrow();
    }

    @Override
    public Optional<TerritoryAssignment> findById(UUID tenantId, UUID assignmentId) {
        try {
            return Optional.ofNullable(jdbc.queryForObject("""
                    SELECT * FROM crm_territory_assignments
                     WHERE tenant_id=:tenantId AND id=:id
                    """, new MapSqlParameterSource()
                    .addValue("tenantId", tenantId)
                    .addValue("id", assignmentId), territoryAssignmentMapper()));
        } catch (EmptyResultDataAccessException missing) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<TerritoryAssignment> findActivePrimary(UUID tenantId,
                                                            UUID territoryId,
                                                            AssigneeType assigneeType) {
        List<TerritoryAssignment> matches = jdbc.query("""
                SELECT * FROM crm_territory_assignments
                 WHERE tenant_id=:tenantId
                   AND territory_id=:territoryId
                   AND assignee_type=:assigneeType
                   AND status='ACTIVE'
                   AND role='PRIMARY'
                 ORDER BY priority DESC, effective_from ASC, id ASC
                 LIMIT 2
                """, new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("territoryId", territoryId)
                .addValue("assigneeType", assigneeType.name()), territoryAssignmentMapper());
        return matches.isEmpty() ? Optional.empty() : Optional.of(matches.get(0));
    }

    @Override
    public List<TerritoryAssignment> findActiveByTerritory(UUID tenantId, UUID territoryId) {
        return jdbc.query("""
                SELECT * FROM crm_territory_assignments
                 WHERE tenant_id=:tenantId
                   AND territory_id=:territoryId
                   AND status='ACTIVE'
                   AND effective_from<=CURRENT_TIMESTAMP
                   AND (effective_to IS NULL OR effective_to>CURRENT_TIMESTAMP)
                 ORDER BY priority DESC, effective_from ASC, id ASC
                """, new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("territoryId", territoryId), territoryAssignmentMapper());
    }

    @Override
    public List<TerritoryAssignment> findActiveByAssignee(UUID tenantId,
                                                           AssigneeType assigneeType,
                                                           UUID assigneeId) {
        return jdbc.query("""
                SELECT * FROM crm_territory_assignments
                 WHERE tenant_id=:tenantId
                   AND assignee_type=:assigneeType
                   AND assignee_id=:assigneeId
                   AND status='ACTIVE'
                   AND effective_from<=CURRENT_TIMESTAMP
                   AND (effective_to IS NULL OR effective_to>CURRENT_TIMESTAMP)
                 ORDER BY priority DESC, effective_from ASC, id ASC
                """, new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("assigneeType", assigneeType.name())
                .addValue("assigneeId", assigneeId), territoryAssignmentMapper());
    }

    @Override
    @Transactional
    public void deactivate(UUID tenantId, UUID assignmentId, UUID updatedBy) {
        int rows = jdbc.update("""
                UPDATE crm_territory_assignments
                   SET status='INACTIVE',
                       effective_to=COALESCE(effective_to, CURRENT_TIMESTAMP),
                       updated_at=CURRENT_TIMESTAMP,
                       updated_by=:updatedBy
                 WHERE tenant_id=:tenantId
                   AND id=:id
                   AND status='ACTIVE'
                """, new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("id", assignmentId)
                .addValue("updatedBy", updatedBy));
        if (rows != 1) {
            throw new OwnershipDomainException(
                    "Territory assignment not found or already inactive: " + assignmentId);
        }
    }
}
