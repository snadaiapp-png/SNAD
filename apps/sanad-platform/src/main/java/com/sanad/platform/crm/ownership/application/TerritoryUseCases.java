package com.sanad.platform.crm.ownership.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sanad.platform.crm.integration.domain.AuditPort;
import com.sanad.platform.crm.integration.domain.AuditPort.AuditChange;
import com.sanad.platform.crm.integration.domain.TimelineEventPort;
import com.sanad.platform.crm.ownership.domain.*;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Application service for CRM-008B WP-05 territories and assignments. */
public class TerritoryUseCases {

    private final TerritoryRepository territories;
    private final TerritoryAssignmentRepository assignments;
    private final SalesTeamRepository teams;
    private final OwnershipUserValidationPort users;
    private final AuditPort audit;
    private final TimelineEventPort timeline;
    private final ObjectMapper mapper;

    public TerritoryUseCases(TerritoryRepository territories,
                             TerritoryAssignmentRepository assignments,
                             SalesTeamRepository teams,
                             OwnershipUserValidationPort users,
                             AuditPort audit,
                             TimelineEventPort timeline,
                             ObjectMapper mapper) {
        this.territories = territories;
        this.assignments = assignments;
        this.teams = teams;
        this.users = users;
        this.audit = audit;
        this.timeline = timeline;
        this.mapper = mapper;
    }

    @Transactional
    public Territory create(UUID tenantId, UUID actorId, CreateCommand command) {
        requireTenantActor(tenantId, actorId);
        if (command == null || command.ruleType() == null) {
            throw new OwnershipDomainException("Territory command and ruleType required");
        }
        validateRule(command.ruleDefinition());
        if (command.parentId() != null) requireActive(tenantId, command.parentId());
        Territory created = territories.save(new Territory(
                null, tenantId, code(command.code()), text(command.displayName(), "displayName", 200),
                command.parentId(), optional(command.description(), 1000), TerritoryStatus.ACTIVE,
                command.ruleType(), normalizeRule(command.ruleDefinition()), command.priority(),
                null, null, actorId, actorId));
        mutation(tenantId, actorId, "CREATE", "TERRITORY", created.id(), null, snapshot(created),
                "crm.territory.created", "Territory created");
        return created;
    }

    public Territory get(UUID tenantId, UUID territoryId) {
        return requireTerritory(tenantId, territoryId);
    }

    public List<Territory> list(UUID tenantId, TerritoryStatus status) {
        if (tenantId == null) throw new OwnershipDomainException("tenantId required");
        return territories.findByTenant(tenantId, status != null ? status : TerritoryStatus.ACTIVE);
    }

    public List<Territory> children(UUID tenantId, UUID territoryId) {
        requireTerritory(tenantId, territoryId);
        return territories.findChildren(tenantId, territoryId);
    }

    /** Tenant-scoped active assignments for territory detail and authorization-safe reads. */
    public List<TerritoryAssignment> activeAssignments(UUID tenantId, UUID territoryId) {
        requireTerritory(tenantId, territoryId);
        return List.copyOf(assignments.findActiveByTerritory(tenantId, territoryId));
    }

    @Transactional
    public Territory update(UUID tenantId, UUID actorId, UUID territoryId, UpdateCommand command) {
        requireTenantActor(tenantId, actorId);
        Territory current = requireTerritory(tenantId, territoryId);
        if (!current.isActive()) throw new OwnershipDomainException("Archived territory is immutable");
        if (command == null) throw new OwnershipDomainException("Territory update command required");
        UUID parent = command.parentIdSet() ? command.parentId() : current.parentId();
        if (parent != null) requireActive(tenantId, parent);
        String rule = command.ruleDefinitionSet()
                ? normalizeRule(command.ruleDefinition()) : current.ruleDefinition();
        validateRule(rule);
        Territory updated = territories.save(new Territory(
                current.id(), tenantId, current.code(),
                command.displayName() != null
                        ? text(command.displayName(), "displayName", 200) : current.displayName(),
                parent,
                command.descriptionSet() ? optional(command.description(), 1000) : current.description(),
                TerritoryStatus.ACTIVE,
                command.ruleType() != null ? command.ruleType() : current.ruleType(),
                rule,
                command.priority() != null ? command.priority() : current.priority(),
                current.createdAt(), current.updatedAt(), current.createdBy(), actorId));
        mutation(tenantId, actorId, "UPDATE", "TERRITORY", territoryId,
                snapshot(current), snapshot(updated), "crm.territory.updated", "Territory updated");
        return updated;
    }

    @Transactional
    public Territory move(UUID tenantId, UUID actorId, UUID territoryId, UUID parentId) {
        requireTenantActor(tenantId, actorId);
        Territory current = requireActive(tenantId, territoryId);
        if (parentId != null) requireActive(tenantId, parentId);
        territories.updateParent(tenantId, territoryId, parentId, actorId);
        Territory moved = requireTerritory(tenantId, territoryId);
        mutation(tenantId, actorId, "MOVE", "TERRITORY", territoryId,
                snapshot(current), snapshot(moved), "crm.territory.moved", "Territory moved");
        return moved;
    }

    @Transactional
    public Territory archive(UUID tenantId, UUID actorId, UUID territoryId) {
        requireTenantActor(tenantId, actorId);
        Territory current = requireTerritory(tenantId, territoryId);
        if (!current.isActive()) return current;
        List<Territory> activeDescendants = territories.findDescendants(tenantId, territoryId).stream()
                .filter(Territory::isActive).toList();
        if (!activeDescendants.isEmpty()) {
            throw new OwnershipDomainException(
                    "Archive active child territories first: " + activeDescendants.size());
        }
        if (!assignments.findActiveByTerritory(tenantId, territoryId).isEmpty()) {
            throw new OwnershipDomainException("Deactivate territory assignments before archive");
        }
        territories.archive(tenantId, territoryId, actorId);
        Territory archived = requireTerritory(tenantId, territoryId);
        mutation(tenantId, actorId, "ARCHIVE", "TERRITORY", territoryId,
                snapshot(current), snapshot(archived), "crm.territory.archived", "Territory archived");
        return archived;
    }

    @Transactional
    public TerritoryAssignment assign(UUID tenantId,
                                      UUID actorId,
                                      UUID territoryId,
                                      AssignCommand command) {
        requireTenantActor(tenantId, actorId);
        requireActive(tenantId, territoryId);
        if (command == null || command.assigneeType() == null || command.assigneeId() == null) {
            throw new OwnershipDomainException("Territory assignee type and id required");
        }
        validateAssignee(tenantId, command.assigneeType(), command.assigneeId());
        TerritoryAssignment created = assignments.save(new TerritoryAssignment(
                null, tenantId, territoryId, command.assigneeType(), command.assigneeId(),
                command.role() != null ? command.role() : TerritoryAssignmentRole.PRIMARY,
                command.priority(), TerritoryAssignmentStatus.ACTIVE,
                command.effectiveFrom() != null ? command.effectiveFrom() : Instant.now(),
                command.effectiveTo(), null, null, actorId, actorId));
        mutation(tenantId, actorId, "CREATE", "TERRITORY_ASSIGNMENT", created.id(),
                null, snapshot(created), "crm.territory_assignment.created", "Territory assignment created");
        return created;
    }

    @Transactional
    public TerritoryAssignment deactivate(UUID tenantId,
                                          UUID actorId,
                                          UUID territoryId,
                                          UUID assignmentId) {
        requireTenantActor(tenantId, actorId);
        requireTerritory(tenantId, territoryId);
        TerritoryAssignment current = assignments.findById(tenantId, assignmentId)
                .filter(value -> territoryId.equals(value.territoryId()) && value.isActive())
                .orElseThrow(() -> new OwnershipDomainException(
                        "Active territory assignment not found on path: " + assignmentId));
        assignments.deactivate(tenantId, assignmentId, actorId);
        TerritoryAssignment inactive = assignments.findById(tenantId, assignmentId).orElseThrow();
        mutation(tenantId, actorId, "DEACTIVATE", "TERRITORY_ASSIGNMENT", assignmentId,
                snapshot(current), snapshot(inactive), "crm.territory_assignment.deactivated",
                "Territory assignment deactivated");
        return inactive;
    }

    /** Higher priority wins; equal highest priority with different assignees fails closed. */
    public Optional<TerritoryAssignment> resolve(UUID tenantId, List<UUID> matchingTerritoryIds) {
        if (tenantId == null) throw new OwnershipDomainException("tenantId required");
        if (matchingTerritoryIds == null || matchingTerritoryIds.isEmpty()) return Optional.empty();
        List<TerritoryAssignment> candidates = matchingTerritoryIds.stream()
                .distinct()
                .peek(id -> requireActive(tenantId, id))
                .flatMap(id -> assignments.findActiveByTerritory(tenantId, id).stream())
                .sorted(Comparator.comparingInt(TerritoryAssignment::priority).reversed()
                        .thenComparing(TerritoryAssignment::effectiveFrom)
                        .thenComparing(TerritoryAssignment::id))
                .toList();
        if (candidates.isEmpty()) return Optional.empty();
        int highest = candidates.get(0).priority();
        List<TerritoryAssignment> winners = candidates.stream()
                .filter(value -> value.priority() == highest).toList();
        TerritoryAssignment first = winners.get(0);
        boolean ambiguous = winners.stream().anyMatch(value ->
                value.assigneeType() != first.assigneeType()
                        || !Objects.equals(value.assigneeId(), first.assigneeId()));
        if (ambiguous) throw new TerritoryAssignmentAmbiguityException(tenantId, highest);
        return Optional.of(first);
    }

    private void validateAssignee(UUID tenantId, AssigneeType type, UUID id) {
        if (type == AssigneeType.USER) {
            if (!users.isActiveUser(tenantId, id)) {
                throw new OwnershipDomainException("Territory user must be ACTIVE in same tenant: " + id);
            }
            return;
        }
        SalesTeam team = teams.findById(tenantId, id)
                .orElseThrow(() -> new OwnershipDomainException("Territory team not found in tenant: " + id));
        if (!team.isActive()) throw new OwnershipDomainException("Territory team must be ACTIVE: " + id);
    }

    private Territory requireTerritory(UUID tenantId, UUID id) {
        if (tenantId == null || id == null) throw new TerritoryNotFoundException(tenantId, id);
        return territories.findById(tenantId, id)
                .orElseThrow(() -> new TerritoryNotFoundException(tenantId, id));
    }

    private Territory requireActive(UUID tenantId, UUID id) {
        Territory territory = requireTerritory(tenantId, id);
        if (!territory.isActive()) throw new OwnershipDomainException("Territory must be ACTIVE: " + id);
        return territory;
    }

    private void validateRule(String json) {
        try {
            JsonNode node = mapper.readTree(normalizeRule(json));
            if (!node.isObject()) throw new OwnershipDomainException("Territory ruleDefinition must be JSON object");
        } catch (OwnershipDomainException domain) {
            throw domain;
        } catch (Exception invalid) {
            throw new OwnershipDomainException("Invalid territory ruleDefinition JSON", invalid);
        }
    }

    private String normalizeRule(String json) {
        return json == null || json.isBlank() ? "{}" : json.trim();
    }

    private JsonNode snapshot(Object value) {
        ObjectNode node = mapper.createObjectNode();
        if (value instanceof Territory territory) {
            put(node, "id", territory.id()); put(node, "tenantId", territory.tenantId());
            node.put("code", territory.code()); node.put("displayName", territory.displayName());
            put(node, "parentId", territory.parentId()); node.put("status", territory.status().name());
            node.put("ruleType", territory.ruleType().name()); node.put("priority", territory.priority());
        } else if (value instanceof TerritoryAssignment assignment) {
            put(node, "id", assignment.id()); put(node, "territoryId", assignment.territoryId());
            node.put("assigneeType", assignment.assigneeType().name());
            put(node, "assigneeId", assignment.assigneeId()); node.put("role", assignment.role().name());
            node.put("priority", assignment.priority()); node.put("status", assignment.status().name());
        }
        return node;
    }

    private void put(ObjectNode node, String name, UUID value) {
        if (value == null) node.putNull(name); else node.put(name, value.toString());
    }

    private void mutation(UUID tenantId, UUID actorId, String action, String type, UUID id,
                          JsonNode before, JsonNode after, String event, String summary) {
        Instant now = Instant.now();
        audit.record(tenantId, actorId, action, type, id, new AuditChange(before, after), now);
        timeline.record(tenantId, type, id, event, summary, type, id, actorId, now);
    }

    private void requireTenantActor(UUID tenantId, UUID actorId) {
        if (tenantId == null || actorId == null) throw new OwnershipDomainException("tenantId and actorId required");
    }

    private String code(String value) { return text(value, "code", 64).toUpperCase(Locale.ROOT); }

    private String text(String value, String field, int max) {
        if (value == null || value.isBlank()) throw new OwnershipDomainException(field + " required");
        String normalized = value.trim();
        if (normalized.length() > max) throw new OwnershipDomainException(field + " exceeds " + max);
        return normalized;
    }

    private String optional(String value, int max) {
        if (value == null) return null;
        String normalized = value.trim();
        if (normalized.length() > max) throw new OwnershipDomainException("Value exceeds " + max);
        return normalized;
    }

    public record CreateCommand(String code, String displayName, UUID parentId, String description,
                                TerritoryRuleType ruleType, String ruleDefinition, int priority) { }

    public record UpdateCommand(String displayName, boolean parentIdSet, UUID parentId,
                                boolean descriptionSet, String description, TerritoryRuleType ruleType,
                                boolean ruleDefinitionSet, String ruleDefinition, Integer priority) { }

    public record AssignCommand(AssigneeType assigneeType, UUID assigneeId,
                                TerritoryAssignmentRole role, int priority,
                                Instant effectiveFrom, Instant effectiveTo) { }
}
