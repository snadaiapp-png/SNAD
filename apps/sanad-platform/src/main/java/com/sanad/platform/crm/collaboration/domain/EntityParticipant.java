package com.sanad.platform.crm.collaboration.domain;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
public record EntityParticipant(UUID id, UUID tenantId, CollaborationEntityType entityType, UUID entityId,
        UUID userId, ParticipantRole role, UUID addedByUserId, Instant addedAt,
        UUID removedByUserId, Instant removedAt, long version) {
    public EntityParticipant {
        Objects.requireNonNull(id, "id"); Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(entityType, "entityType"); Objects.requireNonNull(entityId, "entityId");
        Objects.requireNonNull(userId, "userId"); Objects.requireNonNull(role, "role");
        Objects.requireNonNull(addedByUserId, "addedByUserId"); Objects.requireNonNull(addedAt, "addedAt");
        if ((removedByUserId == null) != (removedAt == null)) throw new IllegalArgumentException("removal actor and timestamp must be set together");
        if (version < 0) throw new IllegalArgumentException("version must be non-negative");
    }
    public static EntityParticipant active(UUID id, UUID tenantId, CollaborationEntityType entityType,
            UUID entityId, UUID userId, ParticipantRole role, UUID actorId, Instant addedAt) {
        return new EntityParticipant(id, tenantId, entityType, entityId, userId, role, actorId, addedAt, null, null, 0L);
    }
    public boolean isActive() { return removedAt == null; }
    public EntityParticipant remove(UUID actorId, Instant at) {
        Objects.requireNonNull(actorId, "actorId"); Objects.requireNonNull(at, "at");
        if (!isActive()) throw new IllegalStateException("participant already removed");
        return new EntityParticipant(id, tenantId, entityType, entityId, userId, role, addedByUserId, addedAt, actorId, at, version + 1);
    }
}
