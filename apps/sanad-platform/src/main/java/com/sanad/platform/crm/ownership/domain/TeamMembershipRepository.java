package com.sanad.platform.crm.ownership.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Repository for team memberships (tenant-scoped). */
public interface TeamMembershipRepository {

    TeamMembership save(TeamMembership membership);

    Optional<TeamMembership> findById(UUID tenantId, UUID membershipId);

    Optional<TeamMembership> findActive(UUID tenantId, UUID teamId, UUID userId);

    List<TeamMembership> findActiveByTeam(UUID tenantId, UUID teamId);

    List<TeamMembership> findActiveByUser(UUID tenantId, UUID userId);

    Optional<TeamMembership> findPrimaryByUser(UUID tenantId, UUID userId);

    void endMembership(UUID tenantId, UUID membershipId, String leftReason, UUID updatedBy);

    long countActiveByTeam(UUID tenantId, UUID teamId);
}
