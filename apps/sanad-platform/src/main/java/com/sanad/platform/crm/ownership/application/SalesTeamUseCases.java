package com.sanad.platform.crm.ownership.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanad.platform.crm.integration.domain.AuditPort;
import com.sanad.platform.crm.integration.domain.AuditPort.AuditChange;
import com.sanad.platform.crm.integration.domain.TimelineEventPort;
import com.sanad.platform.crm.ownership.domain.ActiveMembershipExistsException;
import com.sanad.platform.crm.ownership.domain.ActiveTeamManagerConflictException;
import com.sanad.platform.crm.ownership.domain.InvalidTeamManagerException;
import com.sanad.platform.crm.ownership.domain.MembershipRole;
import com.sanad.platform.crm.ownership.domain.MembershipStatus;
import com.sanad.platform.crm.ownership.domain.OwnershipDomainException;
import com.sanad.platform.crm.ownership.domain.OwnershipUserValidationPort;
import com.sanad.platform.crm.ownership.domain.PrimaryMembershipConflictException;
import com.sanad.platform.crm.ownership.domain.Queue;
import com.sanad.platform.crm.ownership.domain.QueueRepository;
import com.sanad.platform.crm.ownership.domain.QueueStatus;
import com.sanad.platform.crm.ownership.domain.SalesTeam;
import com.sanad.platform.crm.ownership.domain.SalesTeamRepository;
import com.sanad.platform.crm.ownership.domain.TeamArchivedException;
import com.sanad.platform.crm.ownership.domain.TeamMembership;
import com.sanad.platform.crm.ownership.domain.TeamMembershipNotFoundException;
import com.sanad.platform.crm.ownership.domain.TeamMembershipRepository;
import com.sanad.platform.crm.ownership.domain.TeamNotFoundException;
import com.sanad.platform.crm.ownership.domain.TeamStatus;
import com.sanad.platform.crm.ownership.domain.Territory;
import com.sanad.platform.crm.ownership.domain.TerritoryRepository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Application service for CRM-008B WP-03 Sales Teams. */
public class SalesTeamUseCases {

    private final SalesTeamRepository teams;
    private final TeamMembershipRepository memberships;
    private final OwnershipUserValidationPort users;
    private final QueueRepository queues;
    private final TerritoryRepository territories;
    private final AuditPort audit;
    private final TimelineEventPort timeline;
    private final ObjectMapper mapper;

    public SalesTeamUseCases(SalesTeamRepository teams,
                             TeamMembershipRepository memberships,
                             OwnershipUserValidationPort users,
                             QueueRepository queues,
                             TerritoryRepository territories,
                             AuditPort audit,
                             TimelineEventPort timeline,
                             ObjectMapper mapper) {
        this.teams = teams;
        this.memberships = memberships;
        this.users = users;
        this.queues = queues;
        this.territories = territories;
        this.audit = audit;
        this.timeline = timeline;
        this.mapper = mapper;
    }

    @Transactional
    public SalesTeam createTeam(UUID tenantId, UUID actorId, CreateTeamCommand command) {
        requireContext(tenantId, actorId);
        if (command == null) {
            throw new IllegalArgumentException("command required");
        }
        validateManager(tenantId, command.managerUserId(), null, TeamStatus.ACTIVE);
        validateDefaults(tenantId, command.defaultQueueId(), command.defaultTerritoryId());

        SalesTeam created = teams.save(new SalesTeam(
                null,
                tenantId,
                command.code(),
                command.displayName(),
                command.description(),
                TeamStatus.ACTIVE,
                command.managerUserId(),
                command.defaultQueueId(),
                command.defaultTerritoryId(),
                null,
                null,
                actorId,
                actorId));

        Instant now = Instant.now();
        audit.record(tenantId, actorId, "CREATE", "SALES_TEAM", created.id(),
                new AuditChange(null, serializeTeam(created)), now);
        timeline.record(tenantId, "SALES_TEAM", created.id(),
                "crm.sales_team.created", "Sales team created",
                "CRM_SALES_TEAM", created.id(), actorId, now);
        return created;
    }

    public SalesTeam getTeam(UUID tenantId, UUID teamId) {
        requireId(tenantId, "tenantId");
        requireId(teamId, "teamId");
        return teams.findById(tenantId, teamId)
                .orElseThrow(() -> new TeamNotFoundException(tenantId, teamId));
    }

    public List<SalesTeam> listTeams(UUID tenantId, TeamStatus status) {
        requireId(tenantId, "tenantId");
        if (status == null) {
            throw new IllegalArgumentException("status required");
        }
        return List.copyOf(teams.findByTenant(tenantId, status));
    }

    @Transactional
    public SalesTeam updateTeam(UUID tenantId,
                                UUID actorId,
                                UUID teamId,
                                UpdateTeamCommand command) {
        requireContext(tenantId, actorId);
        requireId(teamId, "teamId");
        if (command == null) {
            throw new IllegalArgumentException("command required");
        }
        if (command.status() == null) {
            throw new IllegalArgumentException("status required");
        }
        if (command.status() == TeamStatus.ARCHIVED) {
            throw new OwnershipDomainException("Use archiveTeam for ARCHIVED status transitions");
        }

        SalesTeam current = getTeam(tenantId, teamId);
        assertMutable(current);
        validateManager(tenantId, command.managerUserId(), teamId, command.status());
        validateDefaults(tenantId, command.defaultQueueId(), command.defaultTerritoryId());

        SalesTeam updated = teams.save(new SalesTeam(
                current.id(),
                tenantId,
                current.code(),
                command.displayName(),
                command.description(),
                command.status(),
                command.managerUserId(),
                command.defaultQueueId(),
                command.defaultTerritoryId(),
                current.createdAt(),
                current.updatedAt(),
                current.createdBy(),
                actorId));

        Instant now = Instant.now();
        audit.record(tenantId, actorId, "UPDATE", "SALES_TEAM", teamId,
                new AuditChange(serializeTeam(current), serializeTeam(updated)), now);
        timeline.record(tenantId, "SALES_TEAM", teamId,
                "crm.sales_team.updated", "Sales team updated",
                "CRM_SALES_TEAM", teamId, actorId, now);
        return updated;
    }

    @Transactional
    public SalesTeam archiveTeam(UUID tenantId, UUID actorId, UUID teamId) {
        requireContext(tenantId, actorId);
        requireId(teamId, "teamId");
        SalesTeam current = getTeam(tenantId, teamId);
        assertMutable(current);

        teams.archive(tenantId, teamId, actorId);
        SalesTeam archived = getTeam(tenantId, teamId);

        Instant now = Instant.now();
        audit.record(tenantId, actorId, "ARCHIVE", "SALES_TEAM", teamId,
                new AuditChange(serializeTeam(current), serializeTeam(archived)), now);
        timeline.record(tenantId, "SALES_TEAM", teamId,
                "crm.sales_team.archived", "Sales team archived",
                "CRM_SALES_TEAM", teamId, actorId, now);
        return archived;
    }

    public List<TeamMembership> listActiveMemberships(UUID tenantId, UUID teamId) {
        getTeam(tenantId, teamId);
        return List.copyOf(memberships.findActiveByTeam(tenantId, teamId));
    }

    @Transactional
    public TeamMembership addMembership(UUID tenantId,
                                        UUID actorId,
                                        UUID teamId,
                                        AddMembershipCommand command) {
        requireContext(tenantId, actorId);
        requireId(teamId, "teamId");
        if (command == null) {
            throw new IllegalArgumentException("command required");
        }
        SalesTeam team = getTeam(tenantId, teamId);
        assertMutable(team);
        validateActiveMemberUser(tenantId, command.userId());
        validateMembershipFields(command.role(), command.capacityMax(), command.metadata());

        if (memberships.findActive(tenantId, teamId, command.userId()).isPresent()) {
            throw new ActiveMembershipExistsException(tenantId, teamId, command.userId());
        }
        if (command.primary()) {
            memberships.findPrimaryByUser(tenantId, command.userId()).ifPresent(existing -> {
                throw new PrimaryMembershipConflictException(tenantId, command.userId());
            });
        }

        TeamMembership created = memberships.save(new TeamMembership(
                null,
                tenantId,
                teamId,
                command.userId(),
                command.role(),
                command.primary(),
                MembershipStatus.ACTIVE,
                null,
                null,
                null,
                command.capacityMax(),
                normalizedMetadata(command.metadata()),
                null,
                null,
                actorId,
                actorId));

        Instant now = Instant.now();
        audit.record(tenantId, actorId, "CREATE", "TEAM_MEMBERSHIP", created.id(),
                new AuditChange(null, serializeMembership(created)), now);
        timeline.record(tenantId, "SALES_TEAM", teamId,
                "crm.team_membership.started", "Team membership started",
                "CRM_TEAM_MEMBERSHIP", created.id(), actorId, now);
        return created;
    }

    @Transactional
    public TeamMembership updateMembership(UUID tenantId,
                                           UUID actorId,
                                           UUID teamId,
                                           UUID membershipId,
                                           UpdateMembershipCommand command) {
        requireContext(tenantId, actorId);
        requireId(teamId, "teamId");
        requireId(membershipId, "membershipId");
        if (command == null) {
            throw new IllegalArgumentException("command required");
        }
        SalesTeam team = getTeam(tenantId, teamId);
        assertMutable(team);
        TeamMembership current = activeMembershipForTeam(tenantId, teamId, membershipId);
        validateActiveMemberUser(tenantId, current.userId());
        validateMembershipFields(command.role(), command.capacityMax(), command.metadata());

        if (command.primary()) {
            memberships.findPrimaryByUser(tenantId, current.userId())
                    .filter(existing -> !existing.id().equals(membershipId))
                    .ifPresent(existing -> {
                        throw new PrimaryMembershipConflictException(tenantId, current.userId());
                    });
        }

        TeamMembership updated = memberships.updateActive(
                tenantId,
                membershipId,
                command.role(),
                command.primary(),
                command.capacityMax(),
                normalizedMetadata(command.metadata()),
                actorId);

        Instant now = Instant.now();
        audit.record(tenantId, actorId, "UPDATE", "TEAM_MEMBERSHIP", membershipId,
                new AuditChange(serializeMembership(current), serializeMembership(updated)), now);
        timeline.record(tenantId, "SALES_TEAM", teamId,
                "crm.team_membership.updated", "Team membership updated",
                "CRM_TEAM_MEMBERSHIP", membershipId, actorId, now);
        return updated;
    }

    @Transactional
    public TeamMembership endMembership(UUID tenantId,
                                        UUID actorId,
                                        UUID teamId,
                                        UUID membershipId,
                                        String reason) {
        requireContext(tenantId, actorId);
        requireId(teamId, "teamId");
        requireId(membershipId, "membershipId");
        getTeam(tenantId, teamId);
        TeamMembership current = activeMembershipForTeam(tenantId, teamId, membershipId);
        String normalizedReason = normalizeReason(reason);

        memberships.endMembership(tenantId, membershipId, normalizedReason, actorId);
        TeamMembership ended = memberships.findById(tenantId, membershipId)
                .orElseThrow(() -> new TeamMembershipNotFoundException(tenantId, membershipId));

        Instant now = Instant.now();
        audit.record(tenantId, actorId, "END", "TEAM_MEMBERSHIP", membershipId,
                new AuditChange(serializeMembership(current), serializeMembership(ended)), now);
        timeline.record(tenantId, "SALES_TEAM", teamId,
                "crm.team_membership.ended", "Team membership ended",
                "CRM_TEAM_MEMBERSHIP", membershipId, actorId, now);
        return ended;
    }

    private void validateManager(UUID tenantId,
                                 UUID managerUserId,
                                 UUID currentTeamId,
                                 TeamStatus targetStatus) {
        if (managerUserId == null) {
            return;
        }
        if (!users.lockActiveUser(tenantId, managerUserId)) {
            throw new InvalidTeamManagerException(tenantId, managerUserId);
        }
        if (targetStatus == TeamStatus.ACTIVE) {
            boolean conflict = teams.findByManager(tenantId, managerUserId).stream()
                    .anyMatch(team -> currentTeamId == null || !team.id().equals(currentTeamId));
            if (conflict) {
                throw new ActiveTeamManagerConflictException(tenantId, managerUserId);
            }
        }
    }

    private void validateDefaults(UUID tenantId, UUID queueId, UUID territoryId) {
        if (queueId != null) {
            Queue queue = queues.findById(tenantId, queueId)
                    .orElseThrow(() -> new OwnershipDomainException(
                            "Default queue not found in tenant: " + queueId));
            if (queue.status() == QueueStatus.ARCHIVED) {
                throw new OwnershipDomainException("Default queue is archived: " + queueId);
            }
        }
        if (territoryId != null) {
            Territory territory = territories.findById(tenantId, territoryId)
                    .orElseThrow(() -> new OwnershipDomainException(
                            "Default territory not found in tenant: " + territoryId));
            if (!territory.isActive()) {
                throw new OwnershipDomainException("Default territory is not ACTIVE: " + territoryId);
            }
        }
    }

    private void validateActiveMemberUser(UUID tenantId, UUID userId) {
        requireId(userId, "userId");
        if (!users.isActiveUser(tenantId, userId)) {
            throw new OwnershipDomainException(
                    "Team member must be an ACTIVE user in the same tenant: " + userId);
        }
    }

    private void validateMembershipFields(MembershipRole role, int capacityMax, String metadata) {
        if (role == null) {
            throw new IllegalArgumentException("role required");
        }
        if (capacityMax < 0 || capacityMax > 1000) {
            throw new OwnershipDomainException("capacityMax must be between 0 and 1000");
        }
        JsonNode parsed = parseMetadata(metadata);
        if (!parsed.isObject()) {
            throw new OwnershipDomainException("metadata must be a JSON object");
        }
    }

    private TeamMembership activeMembershipForTeam(UUID tenantId,
                                                    UUID teamId,
                                                    UUID membershipId) {
        TeamMembership membership = memberships.findById(tenantId, membershipId)
                .orElseThrow(() -> new TeamMembershipNotFoundException(tenantId, membershipId));
        if (!membership.teamId().equals(teamId) || !membership.isActive()) {
            throw new TeamMembershipNotFoundException(tenantId, membershipId);
        }
        return membership;
    }

    private static void assertMutable(SalesTeam team) {
        if (team.isArchived()) {
            throw new TeamArchivedException(team.tenantId(), team.id());
        }
    }

    private static void requireContext(UUID tenantId, UUID actorId) {
        requireId(tenantId, "tenantId");
        requireId(actorId, "actorId");
    }

    private static void requireId(UUID value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " required");
        }
    }

    private static String normalizeReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("reason required");
        }
        String normalized = reason.trim();
        if (normalized.length() > 100) {
            throw new OwnershipDomainException("reason must not exceed 100 characters");
        }
        return normalized;
    }

    private String normalizedMetadata(String metadata) {
        return parseMetadata(metadata).toString();
    }

    private JsonNode parseMetadata(String metadata) {
        String value = metadata == null || metadata.isBlank() ? "{}" : metadata;
        try {
            return mapper.readTree(value);
        } catch (JsonProcessingException invalid) {
            throw new OwnershipDomainException("metadata must be valid JSON", invalid);
        }
    }

    private JsonNode serializeTeam(SalesTeam team) {
        if (team == null) {
            return null;
        }
        var node = mapper.createObjectNode();
        node.put("id", team.id().toString());
        node.put("tenantId", team.tenantId().toString());
        node.put("code", team.code());
        node.put("displayName", team.displayName());
        node.put("description", team.description());
        node.put("status", team.status().name());
        putUuid(node, "managerUserId", team.managerUserId());
        putUuid(node, "defaultQueueId", team.defaultQueueId());
        putUuid(node, "defaultTerritoryId", team.defaultTerritoryId());
        return node;
    }

    private JsonNode serializeMembership(TeamMembership membership) {
        if (membership == null) {
            return null;
        }
        var node = mapper.createObjectNode();
        node.put("id", membership.id().toString());
        node.put("tenantId", membership.tenantId().toString());
        node.put("teamId", membership.teamId().toString());
        node.put("userId", membership.userId().toString());
        node.put("role", membership.role().name());
        node.put("primary", membership.isPrimary());
        node.put("status", membership.status().name());
        node.put("capacityMax", membership.capacityMax());
        node.set("metadata", parseMetadata(membership.metadata()));
        if (membership.leftReason() != null) {
            node.put("leftReason", membership.leftReason());
        }
        return node;
    }

    private static void putUuid(com.fasterxml.jackson.databind.node.ObjectNode node,
                                String field,
                                UUID value) {
        if (value == null) {
            node.putNull(field);
        } else {
            node.put(field, value.toString());
        }
    }

    public record CreateTeamCommand(String code,
                                    String displayName,
                                    String description,
                                    UUID managerUserId,
                                    UUID defaultQueueId,
                                    UUID defaultTerritoryId) {
    }

    /** Full mutable state replacement; team code and identity remain immutable. */
    public record UpdateTeamCommand(String displayName,
                                    String description,
                                    TeamStatus status,
                                    UUID managerUserId,
                                    UUID defaultQueueId,
                                    UUID defaultTerritoryId) {
    }

    public record AddMembershipCommand(UUID userId,
                                       MembershipRole role,
                                       boolean primary,
                                       int capacityMax,
                                       String metadata) {
    }

    public record UpdateMembershipCommand(MembershipRole role,
                                          boolean primary,
                                          int capacityMax,
                                          String metadata) {
    }
}
