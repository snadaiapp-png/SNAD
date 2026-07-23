package com.sanad.platform.crm.ownership.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Repository for territories with closure table (tenant-scoped). */
public interface TerritoryRepository {

    Territory save(Territory territory);

    Optional<Territory> findById(UUID tenantId, UUID territoryId);

    Optional<Territory> findByCode(UUID tenantId, String code);

    List<Territory> findByTenant(UUID tenantId, TerritoryStatus status);

    List<Territory> findChildren(UUID tenantId, UUID parentId);

    List<Territory> findAncestors(UUID tenantId, UUID territoryId);

    List<Territory> findDescendants(UUID tenantId, UUID territoryId);

    void updateParent(UUID tenantId, UUID territoryId, UUID newParentId, UUID updatedBy);

    void rebuildClosure(UUID tenantId);

    void archive(UUID tenantId, UUID territoryId, UUID updatedBy);

    boolean wouldCreateCycle(UUID tenantId, UUID territoryId, UUID proposedParentId);
}
