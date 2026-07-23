package com.sanad.platform.crm.ownership.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Repository for sales teams (tenant-scoped). */
public interface SalesTeamRepository {

    SalesTeam save(SalesTeam team);

    Optional<SalesTeam> findById(UUID tenantId, UUID teamId);

    Optional<SalesTeam> findByCode(UUID tenantId, String code);

    List<SalesTeam> findByTenant(UUID tenantId, TeamStatus status);

    List<SalesTeam> findByManager(UUID tenantId, UUID managerUserId);

    void archive(UUID tenantId, UUID teamId, UUID updatedBy);

    long countActiveMemberships(UUID tenantId, UUID teamId);
}
