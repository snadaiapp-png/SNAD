package com.sanad.platform.management.domain;

import java.util.List;
import java.util.UUID;

/**
 * Repository for {@link ManagementAuditEntry} — append-only.
 * No update or delete methods. This is intentional: audit records are immutable.
 */
public interface ManagementAuditRepository {
    ManagementAuditEntry save(ManagementAuditEntry entry);
    List<ManagementAuditEntry> findByEntity(UUID tenantId, ManagementAuditEntry.EntityType entityType, UUID entityId);
    List<ManagementAuditEntry> findByTenant(UUID tenantId, int limit);
    List<ManagementAuditEntry> findByActor(UUID tenantId, UUID actorUserId, int limit);
}
