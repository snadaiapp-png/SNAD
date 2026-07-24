package com.sanad.platform.crm.integration.application;

import java.util.UUID;

/**
 * Port for loading a live CRM entity snapshot to validate
 * that the entity version hasn't changed since the AI recommendation was generated.
 */
public interface CrmEntitySnapshotPort {

    /**
     * Load a tenant-scoped entity snapshot.
     * Returns null if entity not found or doesn't belong to tenant.
     */
    CrmEntitySnapshot load(UUID tenantId, String entityType, UUID entityId);

    record CrmEntitySnapshot(
            UUID tenantId,
            String entityType,
            UUID entityId,
            long currentVersion,
            String currentState,
            boolean active
    ) {}
}
