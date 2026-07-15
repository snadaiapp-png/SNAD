package com.sanad.platform.crm.activity.domain;

import java.util.List;
import java.util.UUID;

public interface ActivityRepository {
    ActivityRecord findById(UUID tenantId, UUID activityId);
    List<ActivityRecord> findAll(UUID tenantId, int limit, String relatedType, UUID relatedId, String status);
    ActivityRecord create(UUID tenantId, UUID actorId, CreateActivityCommand command);
    ActivityRecord update(UUID tenantId, UUID actorId, UUID activityId, UpdateActivityCommand command, long expectedVersion);
    ActivityRecord complete(UUID tenantId, UUID actorId, UUID activityId, String result, long expectedVersion);

    record ActivityRecord(UUID id, long version, String activityType, String subject, String body,
            String relatedType, UUID relatedId, UUID ownerUserId, String status, Integer priority,
            java.time.OffsetDateTime startAt, java.time.OffsetDateTime dueAt,
            java.time.OffsetDateTime completedAt, String result,
            java.time.Instant createdAt, java.time.Instant updatedAt) {}
    record CreateActivityCommand(String activityType, String subject, String body, String relatedType,
            UUID relatedId, UUID ownerUserId, Integer priority,
            java.time.OffsetDateTime startAt, java.time.OffsetDateTime dueAt) {}
    record UpdateActivityCommand(String subject, String body, Integer priority,
            java.time.OffsetDateTime startAt, java.time.OffsetDateTime dueAt) {}
}
