package com.sanad.platform.crm.task.domain;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Task repository port — bounded context for first-class CRM tasks.
 * <p>
 * Tasks are standalone work items with assignee, due date, priority,
 * and a simple OPEN → IN_PROGRESS → COMPLETED|CANCELLED lifecycle.
 * They may optionally link to any CRM entity via (relatedType, relatedId).
 * <p>
 * Branch: feature/crm-tasks
 */
public interface TaskRepository {
    TaskRecord findById(UUID tenantId, UUID taskId);
    List<TaskRecord> findAll(UUID tenantId, int limit, String status, UUID assigneeId, UUID relatedId);
    TaskRecord create(UUID tenantId, UUID actorId, CreateTaskCommand command);
    TaskRecord update(UUID tenantId, UUID actorId, UUID taskId, UpdateTaskCommand command, long expectedVersion);
    TaskRecord complete(UUID tenantId, UUID actorId, UUID taskId, String result, long expectedVersion);
    TaskRecord cancel(UUID tenantId, UUID actorId, UUID taskId, String reason, long expectedVersion);
    TaskRecord start(UUID tenantId, UUID actorId, UUID taskId, long expectedVersion);

    record TaskRecord(UUID id, long version, String title, String description,
            String relatedType, UUID relatedId,
            UUID assigneeUserId, UUID ownerUserId,
            String status, Integer priority,
            OffsetDateTime startAt, OffsetDateTime dueAt, OffsetDateTime completedAt,
            String result,
            Instant createdAt, Instant updatedAt) {}

    record CreateTaskCommand(String title, String description,
            String relatedType, UUID relatedId,
            UUID assigneeUserId, UUID ownerUserId,
            Integer priority,
            OffsetDateTime startAt, OffsetDateTime dueAt) {}

    record UpdateTaskCommand(String title, String description,
            UUID assigneeUserId, Integer priority,
            OffsetDateTime startAt, OffsetDateTime dueAt) {}
}
