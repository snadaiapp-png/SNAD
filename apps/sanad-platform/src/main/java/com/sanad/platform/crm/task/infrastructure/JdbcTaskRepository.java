package com.sanad.platform.crm.task.infrastructure;

import com.sanad.platform.crm.error.CrmContractException;
import com.sanad.platform.crm.error.CrmErrorCode;
import com.sanad.platform.crm.task.domain.TaskRepository;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * JDBC implementation of {@link TaskRepository}.
 * <p>
 * Tenant isolation is enforced at the SQL level — every query filters
 * on {@code tenant_id}. Optimistic concurrency is enforced via
 * {@code WHERE version = :expectedVersion} on mutations; a zero-rows
 * result raises {@link CrmErrorCode#CRM_CONCURRENCY_CONFLICT}.
 * <p>
 * Branch: feature/crm-tasks
 */
@Repository
public class JdbcTaskRepository implements TaskRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcTaskRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public TaskRecord findById(UUID tenantId, UUID taskId) {
        try {
            return mapRow(jdbc.queryForMap(
                    "SELECT * FROM crm_tasks WHERE tenant_id = :t AND id = :id",
                    new MapSqlParameterSource()
                            .addValue("t", tenantId)
                            .addValue("id", taskId)));
        } catch (org.springframework.dao.EmptyResultDataAccessException e) {
            throw new CrmContractException(CrmErrorCode.CRM_TASK_NOT_FOUND);
        }
    }

    @Override
    public List<TaskRecord> findAll(UUID tenantId, int limit, String status, UUID assigneeId, UUID relatedId) {
        StringBuilder sql = new StringBuilder("SELECT * FROM crm_tasks WHERE tenant_id = :t");
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("t", tenantId);
        if (status != null && !status.isBlank()) {
            sql.append(" AND status = :status");
            params.addValue("status", status.toUpperCase());
        }
        if (assigneeId != null) {
            sql.append(" AND assignee_user_id = :assigneeId");
            params.addValue("assigneeId", assigneeId);
        }
        if (relatedId != null) {
            sql.append(" AND related_id = :relatedId");
            params.addValue("relatedId", relatedId);
        }
        sql.append(" ORDER BY due_at ASC NULLS LAST, updated_at DESC, id DESC LIMIT :limit");
        params.addValue("limit", limit);
        return jdbc.queryForList(sql.toString(), params).stream().map(this::mapRow).toList();
    }

    @Override
    public TaskRecord create(UUID tenantId, UUID actorId, CreateTaskCommand cmd) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        Integer priority = cmd.priority() == null ? 50 : cmd.priority();
        UUID ownerUserId = cmd.ownerUserId() == null ? actorId : cmd.ownerUserId();

        jdbc.update(
                "INSERT INTO crm_tasks (" +
                "  id, tenant_id, version, title, description, " +
                "  related_type, related_id, assignee_user_id, owner_user_id, " +
                "  status, priority, start_at, due_at, " +
                "  created_by, updated_by, created_at, updated_at" +
                ") VALUES (" +
                "  :id, :t, 0, :title, :description, " +
                "  :relatedType, :relatedId, :assigneeUserId, :ownerUserId, " +
                "  'OPEN', :priority, :startAt, :dueAt, " +
                "  :actorId, :actorId, :now, :now" +
                ")",
                new MapSqlParameterSource()
                        .addValue("id", id)
                        .addValue("t", tenantId)
                        .addValue("title", cmd.title())
                        .addValue("description", cmd.description())
                        .addValue("relatedType", cmd.relatedType() == null ? null : cmd.relatedType().toUpperCase())
                        .addValue("relatedId", cmd.relatedId())
                        .addValue("assigneeUserId", cmd.assigneeUserId())
                        .addValue("ownerUserId", ownerUserId)
                        .addValue("priority", priority)
                        .addValue("startAt", cmd.startAt())
                        .addValue("dueAt", cmd.dueAt())
                        .addValue("actorId", actorId)
                        .addValue("now", Timestamp.from(now)));
        return findById(tenantId, id);
    }

    @Override
    public TaskRecord update(UUID tenantId, UUID actorId, UUID taskId, UpdateTaskCommand cmd, long expectedVersion) {
        StringBuilder sql = new StringBuilder(
                "UPDATE crm_tasks SET version = version + 1, updated_by = :actorId, updated_at = :now");
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("t", tenantId)
                .addValue("id", taskId)
                .addValue("expectedVersion", expectedVersion)
                .addValue("actorId", actorId)
                .addValue("now", Timestamp.from(Instant.now()));

        if (cmd.title() != null) {
            sql.append(", title = :title");
            params.addValue("title", cmd.title());
        }
        if (cmd.description() != null) {
            sql.append(", description = :description");
            params.addValue("description", cmd.description());
        }
        if (cmd.assigneeUserId() != null) {
            sql.append(", assignee_user_id = :assigneeUserId");
            params.addValue("assigneeUserId", cmd.assigneeUserId());
        }
        if (cmd.priority() != null) {
            sql.append(", priority = :priority");
            params.addValue("priority", cmd.priority());
        }
        if (cmd.startAt() != null) {
            sql.append(", start_at = :startAt");
            params.addValue("startAt", cmd.startAt());
        }
        if (cmd.dueAt() != null) {
            sql.append(", due_at = :dueAt");
            params.addValue("dueAt", cmd.dueAt());
        }
        sql.append(" WHERE tenant_id = :t AND id = :id AND version = :expectedVersion");

        int updated = jdbc.update(sql.toString(), params);
        if (updated == 0) {
            throw new CrmContractException(CrmErrorCode.CRM_CONCURRENCY_CONFLICT);
        }
        return findById(tenantId, taskId);
    }

    @Override
    public TaskRecord start(UUID tenantId, UUID actorId, UUID taskId, long expectedVersion) {
        int updated = jdbc.update(
                "UPDATE crm_tasks SET status = 'IN_PROGRESS', version = version + 1, " +
                "  updated_by = :actorId, updated_at = :now, " +
                "  start_at = COALESCE(start_at, :now) " +
                "WHERE tenant_id = :t AND id = :id AND version = :expectedVersion AND status = 'OPEN'",
                new MapSqlParameterSource()
                        .addValue("t", tenantId)
                        .addValue("id", taskId)
                        .addValue("expectedVersion", expectedVersion)
                        .addValue("actorId", actorId)
                        .addValue("now", Timestamp.from(Instant.now())));
        if (updated == 0) {
            // Either version mismatch or invalid state transition
            TaskRecord current = findById(tenantId, taskId);
            if (!"OPEN".equals(current.status())) {
                throw new CrmContractException(CrmErrorCode.CRM_INVALID_TASK_TRANSITION);
            }
            throw new CrmContractException(CrmErrorCode.CRM_CONCURRENCY_CONFLICT);
        }
        return findById(tenantId, taskId);
    }

    @Override
    public TaskRecord complete(UUID tenantId, UUID actorId, UUID taskId, String result, long expectedVersion) {
        int updated = jdbc.update(
                "UPDATE crm_tasks SET status = 'COMPLETED', result = :result, completed_at = :now, " +
                "  version = version + 1, updated_by = :actorId, updated_at = :now " +
                "WHERE tenant_id = :t AND id = :id AND version = :expectedVersion " +
                "  AND status IN ('OPEN', 'IN_PROGRESS')",
                new MapSqlParameterSource()
                        .addValue("t", tenantId)
                        .addValue("id", taskId)
                        .addValue("expectedVersion", expectedVersion)
                        .addValue("result", result)
                        .addValue("actorId", actorId)
                        .addValue("now", Timestamp.from(Instant.now())));
        if (updated == 0) {
            TaskRecord current = findById(tenantId, taskId);
            if ("COMPLETED".equals(current.status()) || "CANCELLED".equals(current.status())) {
                throw new CrmContractException(CrmErrorCode.CRM_INVALID_TASK_TRANSITION);
            }
            throw new CrmContractException(CrmErrorCode.CRM_CONCURRENCY_CONFLICT);
        }
        return findById(tenantId, taskId);
    }

    @Override
    public TaskRecord cancel(UUID tenantId, UUID actorId, UUID taskId, String reason, long expectedVersion) {
        int updated = jdbc.update(
                "UPDATE crm_tasks SET status = 'CANCELLED', result = :reason, " +
                "  version = version + 1, updated_by = :actorId, updated_at = :now " +
                "WHERE tenant_id = :t AND id = :id AND version = :expectedVersion " +
                "  AND status IN ('OPEN', 'IN_PROGRESS')",
                new MapSqlParameterSource()
                        .addValue("t", tenantId)
                        .addValue("id", taskId)
                        .addValue("expectedVersion", expectedVersion)
                        .addValue("reason", reason)
                        .addValue("actorId", actorId)
                        .addValue("now", Timestamp.from(Instant.now())));
        if (updated == 0) {
            TaskRecord current = findById(tenantId, taskId);
            if ("COMPLETED".equals(current.status()) || "CANCELLED".equals(current.status())) {
                throw new CrmContractException(CrmErrorCode.CRM_INVALID_TASK_TRANSITION);
            }
            throw new CrmContractException(CrmErrorCode.CRM_CONCURRENCY_CONFLICT);
        }
        return findById(tenantId, taskId);
    }

    private TaskRecord mapRow(Map<String, Object> r) {
        return new TaskRecord(
                (UUID) r.get("id"),
                asLong(r.get("version")),
                (String) r.get("title"),
                (String) r.get("description"),
                (String) r.get("related_type"),
                (UUID) r.get("related_id"),
                (UUID) r.get("assignee_user_id"),
                (UUID) r.get("owner_user_id"),
                (String) r.get("status"),
                r.get("priority") == null ? null : ((Number) r.get("priority")).intValue(),
                (java.time.OffsetDateTime) r.get("start_at"),
                (java.time.OffsetDateTime) r.get("due_at"),
                (java.time.OffsetDateTime) r.get("completed_at"),
                (String) r.get("result"),
                asInstant(r.get("created_at")),
                asInstant(r.get("updated_at")));
    }

    private static long asLong(Object v) {
        if (v == null) return 0L;
        if (v instanceof Number n) return n.longValue();
        try { return Long.parseLong(String.valueOf(v)); } catch (Exception e) { return 0L; }
    }

    private static Instant asInstant(Object v) {
        if (v == null) return null;
        if (v instanceof Timestamp t) return t.toInstant();
        if (v instanceof Instant i) return i;
        return null;
    }
}
