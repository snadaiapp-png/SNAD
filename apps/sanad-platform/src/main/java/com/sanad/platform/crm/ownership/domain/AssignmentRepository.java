package com.sanad.platform.crm.ownership.domain;

import java.util.Optional;
import java.util.UUID;

/** Repository for CRM assignments (tenant-scoped, single-active invariant). */
public interface AssignmentRepository {

    Assignment save(Assignment assignment);

    Optional<Assignment> findById(UUID tenantId, UUID assignmentId);

    Optional<Assignment> findActive(UUID tenantId, AssignmentRecordType recordType, UUID recordId);

    /**
     * Atomically supersede the current active assignment and insert a new one.
     * Both operations and the immutable history append run in the same transaction.
     */
    default Assignment supersedeAndInsert(UUID tenantId,
                                          AssignmentRecordType recordType,
                                          UUID recordId,
                                          Assignment newAssignment,
                                          UUID actorUserId,
                                          String reason) {
        return supersedeAndInsert(
                tenantId, recordType, recordId, newAssignment, actorUserId, reason,
                ChangeType.REASSIGN, TriggerSource.MANUAL, null);
    }

    /** Atomic ownership transition with an explicit, auditable classification. */
    Assignment supersedeAndInsert(UUID tenantId,
                                  AssignmentRecordType recordType,
                                  UUID recordId,
                                  Assignment newAssignment,
                                  UUID actorUserId,
                                  String reason,
                                  ChangeType changeType,
                                  TriggerSource triggerSource,
                                  UUID triggerReferenceId);

    void endAssignment(UUID tenantId, UUID assignmentId, UUID updatedBy, String reason);

    long countActiveByOwner(UUID tenantId, OwnerType ownerType, UUID ownerId);

    long countActiveByUser(UUID tenantId, UUID userId);
}
