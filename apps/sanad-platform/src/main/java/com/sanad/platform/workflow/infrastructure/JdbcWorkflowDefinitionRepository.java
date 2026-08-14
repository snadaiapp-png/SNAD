package com.sanad.platform.workflow.infrastructure;

import com.sanad.platform.workflow.domain.WorkflowDefinition;
import com.sanad.platform.workflow.domain.WorkflowDefinitionRepository;
import com.sanad.platform.workflow.domain.WorkflowStep;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * JdbcTemplate implementation of {@link WorkflowDefinitionRepository}.
 *
 * <p>Follows the SNAD platform pattern (NOT JPA): hand-written SQL, optimistic
 * locking via the {@code version_lock} column, JSONB columns written via
 * {@code CAST(? AS jsonb)}, and tenant-scoped queries on every read.
 *
 * <p>Definitions and their steps live in two separate tables
 * ({@code workflow_definitions} and {@code workflow_steps}) but share a single
 * repository because steps cannot exist without their parent definition.
 */
@Repository
public class JdbcWorkflowDefinitionRepository implements WorkflowDefinitionRepository {

    private final JdbcTemplate jdbc;

    public JdbcWorkflowDefinitionRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final RowMapper<WorkflowDefinition> DEF_MAPPER = (rs, rowNum) -> new WorkflowDefinition(
            rs.getObject("id", UUID.class),
            rs.getObject("tenant_id", UUID.class),
            rs.getString("code"),
            rs.getString("name"),
            rs.getString("description"),
            rs.getString("module"),
            rs.getInt("version"),
            WorkflowDefinition.Status.valueOf(rs.getString("status")),
            WorkflowDefinition.TriggerType.valueOf(rs.getString("trigger_type")),
            rs.getObject("created_by", UUID.class),
            rs.getLong("version_lock"),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("updated_at").toInstant()
    );

    private static final RowMapper<WorkflowStep> STEP_MAPPER = (rs, rowNum) -> new WorkflowStep(
            rs.getObject("id", UUID.class),
            rs.getObject("tenant_id", UUID.class),
            rs.getObject("workflow_definition_id", UUID.class),
            rs.getString("step_key"),
            rs.getString("name"),
            WorkflowStep.StepType.valueOf(rs.getString("step_type")),
            rs.getInt("sequence_order"),
            rs.getString("configuration"),
            rs.getObject("sla_hours", Integer.class),
            rs.getString("required_capability"),
            rs.getString("required_role"),
            rs.getLong("version"),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("updated_at").toInstant()
    );

    // ===== WorkflowDefinition =====

    @Override
    public WorkflowDefinition save(WorkflowDefinition def) {
        if (def.versionLock() == 0) {
            return insert(def);
        }
        return update(def);
    }

    private WorkflowDefinition insert(WorkflowDefinition def) {
        jdbc.update("""
                INSERT INTO workflow_definitions
                    (id, tenant_id, code, name, description, module, version, status,
                     trigger_type, created_by, version_lock, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                def.id(), def.tenantId(), def.code(), def.name(), def.description(),
                def.module(), def.version(), def.status().name(),
                def.triggerType().name(), def.createdBy(), def.versionLock(),
                Timestamp.from(def.createdAt()), Timestamp.from(def.updatedAt())
        );
        return def;
    }

    private WorkflowDefinition update(WorkflowDefinition def) {
        int affected = jdbc.update("""
                UPDATE workflow_definitions SET
                    name = ?, description = ?, module = ?, status = ?, trigger_type = ?,
                    version_lock = ?, updated_at = ?
                WHERE id = ? AND tenant_id = ? AND version_lock = ?
                """,
                def.name(), def.description(), def.module(),
                def.status().name(), def.triggerType().name(),
                def.versionLock(), Timestamp.from(def.updatedAt()),
                def.id(), def.tenantId(), def.versionLock() - 1
        );
        if (affected == 0) {
            throw new org.springframework.dao.OptimisticLockingFailureException(
                    "WorkflowDefinition " + def.id() + " was modified by another transaction");
        }
        return def;
    }

    @Override
    public Optional<WorkflowDefinition> findById(UUID tenantId, UUID id) {
        return jdbc.query("""
                SELECT * FROM workflow_definitions WHERE tenant_id = ? AND id = ?
                """, DEF_MAPPER, tenantId, id).stream().findFirst();
    }

    @Override
    public Optional<WorkflowDefinition> findByCode(UUID tenantId, String code, int version) {
        return jdbc.query("""
                SELECT * FROM workflow_definitions
                WHERE tenant_id = ? AND code = ? AND version = ?
                """, DEF_MAPPER, tenantId, code, version).stream().findFirst();
    }

    @Override
    public Optional<WorkflowDefinition> findActiveByCode(UUID tenantId, String code) {
        return jdbc.query("""
                SELECT * FROM workflow_definitions
                WHERE tenant_id = ? AND code = ? AND status = 'ACTIVE'
                ORDER BY version DESC
                LIMIT 1
                """, DEF_MAPPER, tenantId, code).stream().findFirst();
    }

    @Override
    public List<WorkflowDefinition> findByTenant(UUID tenantId, int limit) {
        return jdbc.query("""
                SELECT * FROM workflow_definitions WHERE tenant_id = ?
                ORDER BY updated_at DESC LIMIT ?
                """, DEF_MAPPER, tenantId, limit);
    }

    @Override
    public List<WorkflowDefinition> findByTenantAndStatus(
            UUID tenantId, WorkflowDefinition.Status status, int limit) {
        return jdbc.query("""
                SELECT * FROM workflow_definitions
                WHERE tenant_id = ? AND status = ?
                ORDER BY updated_at DESC LIMIT ?
                """, DEF_MAPPER, tenantId, status.name(), limit);
    }

    // ===== WorkflowStep =====

    @Override
    public List<WorkflowStep> findSteps(UUID workflowDefinitionId) {
        return jdbc.query("""
                SELECT * FROM workflow_steps
                WHERE workflow_definition_id = ?
                ORDER BY sequence_order ASC
                """, STEP_MAPPER, workflowDefinitionId);
    }

    @Override
    public WorkflowStep saveStep(WorkflowStep step) {
        if (step.version() == 0) {
            return insertStep(step);
        }
        return updateStep(step);
    }

    private WorkflowStep insertStep(WorkflowStep step) {
        jdbc.update("""
                INSERT INTO workflow_steps
                    (id, tenant_id, workflow_definition_id, step_key, name, step_type,
                     sequence_order, configuration, sla_hours, required_capability,
                     required_role, version, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb), ?, ?, ?, ?, ?, ?)
                """,
                step.id(), step.tenantId(), step.workflowDefinitionId(),
                step.stepKey(), step.name(), step.stepType().name(),
                step.sequenceOrder(),
                step.configuration() != null ? step.configuration() : "{}",
                step.slaHours(),
                step.requiredCapability(),
                step.requiredRole(),
                step.version(),
                Timestamp.from(step.createdAt()), Timestamp.from(step.updatedAt())
        );
        return step;
    }

    private WorkflowStep updateStep(WorkflowStep step) {
        int affected = jdbc.update("""
                UPDATE workflow_steps SET
                    name = ?, step_type = ?, sequence_order = ?, configuration = CAST(? AS jsonb),
                    sla_hours = ?, required_capability = ?, required_role = ?,
                    version = ?, updated_at = ?
                WHERE id = ? AND workflow_definition_id = ? AND version = ?
                """,
                step.name(), step.stepType().name(), step.sequenceOrder(),
                step.configuration() != null ? step.configuration() : "{}",
                step.slaHours(),
                step.requiredCapability(),
                step.requiredRole(),
                step.version(), Timestamp.from(step.updatedAt()),
                step.id(), step.workflowDefinitionId(), step.version() - 1
        );
        if (affected == 0) {
            throw new org.springframework.dao.OptimisticLockingFailureException(
                    "WorkflowStep " + step.id() + " was modified by another transaction");
        }
        return step;
    }
}
