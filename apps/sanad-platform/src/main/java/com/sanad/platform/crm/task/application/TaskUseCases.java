package com.sanad.platform.crm.task.application;

import com.sanad.platform.crm.task.domain.TaskRepository;
import com.sanad.platform.crm.task.domain.TaskRepository.CreateTaskCommand;
import com.sanad.platform.crm.task.domain.TaskRepository.TaskRecord;
import com.sanad.platform.crm.task.domain.TaskRepository.UpdateTaskCommand;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Use-case façade for the Task bounded context.
 * <p>
 * Thin orchestration layer — applies {@code @Transactional} boundaries
 * and delegates to {@link TaskRepository}. Domain policies (state machine
 * enforcement) live in the repository layer for now; this façade may
 * evolve to host cross-cutting concerns (timeline events, audit).
 * <p>
 * Branch: feature/crm-tasks
 */
public class TaskUseCases {
    private final TaskRepository repo;

    public TaskUseCases(TaskRepository repo) {
        this.repo = repo;
    }

    @Transactional
    public TaskRecord create(UUID tenantId, UUID actorId, CreateTaskCommand cmd) {
        return repo.create(tenantId, actorId, cmd);
    }

    public TaskRecord getById(UUID tenantId, UUID id) {
        return repo.findById(tenantId, id);
    }

    public List<TaskRecord> list(UUID tenantId, int limit, String status, UUID assigneeId, UUID relatedId) {
        return repo.findAll(tenantId, limit, status, assigneeId, relatedId);
    }

    @Transactional
    public TaskRecord update(UUID tenantId, UUID actorId, UUID id, UpdateTaskCommand cmd, long expectedVersion) {
        return repo.update(tenantId, actorId, id, cmd, expectedVersion);
    }

    @Transactional
    public TaskRecord start(UUID tenantId, UUID actorId, UUID id, long expectedVersion) {
        return repo.start(tenantId, actorId, id, expectedVersion);
    }

    @Transactional
    public TaskRecord complete(UUID tenantId, UUID actorId, UUID id, String result, long expectedVersion) {
        return repo.complete(tenantId, actorId, id, result, expectedVersion);
    }

    @Transactional
    public TaskRecord cancel(UUID tenantId, UUID actorId, UUID id, String reason, long expectedVersion) {
        return repo.cancel(tenantId, actorId, id, reason, expectedVersion);
    }
}
