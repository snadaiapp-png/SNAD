package com.sanad.platform.crm.ownership.domain;

import java.util.Optional;
import java.util.UUID;

public interface AssignmentRepository {
    Assignment save(Assignment assignment);
    Optional<Assignment> findById(UUID tenantId, UUID assignmentId);
    Optional<Assignment> findActive(UUID tenantId, AssignmentRecordType recordType, UUID recordId);

    default Assignment supersedeAndInsert(UUID tenantId, AssignmentRecordType recordType,
            UUID recordId, Assignment newAssignment, UUID actorUserId, String reason) {
        return supersedeAndInsert(tenantId, recordType, recordId, newAssignment,
                actorUserId, reason, ChangeType.REASSIGN, TriggerSource.MANUAL,
                null, null, null);
    }

    default Assignment supersedeAndInsert(UUID tenantId, AssignmentRecordType recordType,
            UUID recordId, Assignment newAssignment, UUID actorUserId, String reason,
            ChangeType changeType, TriggerSource triggerSource, UUID triggerReferenceId) {
        return supersedeAndInsert(tenantId, recordType, recordId, newAssignment,
                actorUserId, reason, changeType, triggerSource, triggerReferenceId,
                null, null);
    }

    Assignment supersedeAndInsert(UUID tenantId, AssignmentRecordType recordType,
            UUID recordId, Assignment newAssignment, UUID actorUserId, String reason,
            ChangeType changeType, TriggerSource triggerSource, UUID triggerReferenceId,
            OwnerType expectedOwnerType, UUID expectedOwnerId);

    default Assignment supersedeAndInsertExpected(UUID tenantId,
            AssignmentRecordType recordType, UUID recordId, Assignment newAssignment,
            UUID actorUserId, String reason, ChangeType changeType,
            TriggerSource triggerSource, UUID triggerReferenceId,
            UUID expectedAssignmentId, OwnerType expectedOwnerType, UUID expectedOwnerId) {
        return supersedeAndInsert(tenantId, recordType, recordId, newAssignment,
                actorUserId, reason, changeType, triggerSource, triggerReferenceId,
                expectedOwnerType, expectedOwnerId);
    }

    void endAssignment(UUID tenantId, UUID assignmentId, UUID updatedBy, String reason);
    long countActiveByOwner(UUID tenantId, OwnerType ownerType, UUID ownerId);
    long countActiveByUser(UUID tenantId, UUID userId);
    long countActiveQueueClaims(UUID tenantId, UUID queueId, UUID userId);
}
