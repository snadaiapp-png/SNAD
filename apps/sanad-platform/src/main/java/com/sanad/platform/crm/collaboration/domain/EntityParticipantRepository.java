package com.sanad.platform.crm.collaboration.domain;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
public interface EntityParticipantRepository {
    EntityParticipant insert(EntityParticipant participant);
    Optional<EntityParticipant> findActive(UUID tenantId, CollaborationEntityType entityType, UUID entityId, UUID userId, ParticipantRole role);
    Optional<EntityParticipant> findById(UUID tenantId, UUID participantId);
    List<EntityParticipant> listActive(UUID tenantId, CollaborationEntityType entityType, UUID entityId);
    boolean markRemoved(UUID tenantId, UUID participantId, long expectedVersion, UUID removedByUserId, Instant removedAt);
}
