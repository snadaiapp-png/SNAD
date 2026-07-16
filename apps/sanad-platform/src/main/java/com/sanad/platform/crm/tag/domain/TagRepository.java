package com.sanad.platform.crm.tag.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Tag repository port — bounded context for CRM tags/labels.
 * <p>
 * Tags are reusable labels (name + optional color) that can be assigned to
 * any CRM entity via {@code crm_tag_assignments}. Assignments are
 * idempotent — assigning the same tag twice is a no-op.
 * <p>
 * Branch: feature/crm-tags
 */
public interface TagRepository {
    TagRecord findById(UUID tenantId, UUID tagId);
    List<TagRecord> findAll(UUID tenantId, int limit, String search);
    TagRecord create(UUID tenantId, UUID actorId, CreateTagCommand command);
    TagRecord update(UUID tenantId, UUID actorId, UUID tagId, UpdateTagCommand command, long expectedVersion);
    void delete(UUID tenantId, UUID actorId, UUID tagId);

    List<TagAssignmentRecord> findAssignmentsBySubject(UUID tenantId, String subjectType, UUID subjectId);
    List<TagAssignmentRecord> findAssignmentsByTag(UUID tenantId, UUID tagId, int limit);
    TagAssignmentRecord assign(UUID tenantId, UUID actorId, UUID tagId, String subjectType, UUID subjectId);
    void unassign(UUID tenantId, UUID actorId, UUID tagId, String subjectType, UUID subjectId);

    record TagRecord(UUID id, long version, String name, String color,
            Instant createdAt, Instant updatedAt) {}

    record CreateTagCommand(String name, String color) {}
    record UpdateTagCommand(String name, String color) {}

    record TagAssignmentRecord(UUID id, UUID tagId, String subjectType, UUID subjectId,
            UUID assignedBy, Instant assignedAt) {}
}
