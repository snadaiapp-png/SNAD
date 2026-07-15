package com.sanad.platform.crm.note.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Note repository port — bounded context for CRM notes.
 * <p>
 * Notes are append-only plain-text records attached to any CRM entity
 * (account/contact/lead/opportunity/activity/task). V1 supports create,
 * list-by-subject, archive (soft-delete), and read-by-id. Edit and hard
 * delete are intentionally NOT supported to preserve the audit trail.
 * <p>
 * Branch: feature/crm-notes
 */
public interface NoteRepository {
    NoteRecord findById(UUID tenantId, UUID noteId);
    List<NoteRecord> findAllBySubject(UUID tenantId, String subjectType, UUID subjectId, int limit, boolean includeArchived);
    NoteRecord create(UUID tenantId, UUID actorId, CreateNoteCommand command);
    NoteRecord archive(UUID tenantId, UUID actorId, UUID noteId, long expectedVersion);

    record NoteRecord(UUID id, long version, String subjectType, UUID subjectId,
            String body, UUID authorUserId, boolean archived,
            Instant createdAt, Instant updatedAt) {}

    record CreateNoteCommand(String subjectType, UUID subjectId, String body, UUID authorUserId) {}
}
