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
     * Both operations run in the same transaction.
     */
    Assignment supersedeAndInsert(UUID tenantId, AssignmentRecordType recordType, UUID recordId,
                                   Assignment newAssignment, UUID actorUserId, String reason);

    void endAssignment(UUID tenantId, UUID assignmentId, UUID updatedBy, String reason);

    long countActiveByOwner(UUID tenantId, OwnerType ownerType, UUID ownerId);

    long countActiveByUser(UUID tenantId, UUID userId);
}
