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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** CRM-008B WP-06 deterministic assignment-rule and distribution service. */
public class AssignmentRuleUseCases {

    private final AssignmentRuleRepository rules;
    private final SalesTeamRepository teams;
    private final TeamMembershipRepository teamMemberships;
    private final QueueRepository queues;
    private final AssignmentRepository assignments;
    private final TerritoryUseCases territories;
    private final OwnershipUserValidationPort users;
    private final AuditPort audit;
    private final TimelineEventPort timeline;
    private final ObjectMapper mapper;

    public AssignmentRuleUseCases(AssignmentRuleRepository rules,
                                  SalesTeamRepository teams,
                                  TeamMembershipRepository teamMemberships,
                                  QueueRepository queues,
                                  AssignmentRepository assignments,
                                  TerritoryUseCases territories,
                                  OwnershipUserValidationPort users,
                                  AuditPort audit,
                                  TimelineEventPort timeline,
                                  ObjectMapper mapper) {
        this.rules = rules;
        this.teams = teams;
        this.teamMemberships = teamMemberships;
        this.queues = queues;
        this.assignments = assignments;
        this.territories = territories;
        this.users = users;
        this.audit = audit;
        this.timeline = timeline;
        this.mapper = mapper;
    }

    @Transactional
    public AssignmentRule createRule(UUID tenantId, UUID actorId, CreateRuleCommand command) {
        requireTenantActor(tenantId, actorId);
        if (command == null) throw new OwnershipDomainException("Rule command required");
        validateDefinition(tenantId, command.definition());
        AssignmentRule rule = rules.save(new AssignmentRule(
                null, tenantId, code(command.code()), 1, RuleStatus.ACTIVE,
                null, null, actorId, actorId));
        VersionDefinition definition = command.definition();
        rules.saveVersion(toVersion(
                tenantId, rule.id(), 1, RuleStatus.ACTIVE, actorId, definition));
        mutation(tenantId, actorId, "CREATE", "ASSIGNMENT_RULE", rule.id(), null,
                snapshot(rule), "crm.assignment_rule.created", "Assignment rule created");
        return rule;
    }

    public AssignmentRule getRule(UUID tenantId, UUID ruleId) {
        return requireRule(tenantId, ruleId);
    }

    public List<AssignmentRule> listRules(UUID tenantId, RuleStatus status) {
        if (tenantId == null) throw new OwnershipDomainException("tenantId required");
        return rules.findByTenant(tenantId, status != null ? status : RuleStatus.ACTIVE);
    }

    public List<AssignmentRuleVersion> listVersions(UUID tenantId, UUID ruleId) {
        requireRule(tenantId, ruleId);
        return rules.findAllVersions(tenantId, ruleId);
    }

    @Transactional
    public AssignmentRuleVersion createVersion(UUID tenantId,
                                               UUID actorId,
                                               UUID ruleId,
                                               VersionDefinition definition) {
        requireTenantActor(tenantId, actorId);
        requireRule(tenantId, ruleId);
        validateDefinition(tenantId, definition);
        int next = rules.findAllVersions(tenantId, ruleId).stream()
                .mapToInt(AssignmentRuleVersion::version).max().orElse(0) + 1;
        AssignmentRuleVersion created = rules.saveVersion(
                toVersion(tenantId, ruleId, next, RuleStatus.INACTIVE, actorId, definition));
        mutation(tenantId, actorId, "CREATE", "ASSIGNMENT_RULE_VERSION", created.id(), null,
                snapshot(created), "crm.assignment_rule_version.created", "Assignment rule version created");
        return created;
    }

    @Transactional
    public AssignmentRuleVersion activateVersion(UUID tenantId,
                                                  UUID actorId,
                                                  UUID ruleId,
                                                  int version) {
        requireTenantActor(tenantId, actorId);
        AssignmentRule rule = requireRule(tenantId, ruleId);
        AssignmentRuleVersion target = rules.findVersion(tenantId, ruleId, version)
                .orElseThrow(() -> new AssignmentRuleNotFoundException(tenantId, ruleId));
        rules.activateVersion(tenantId, ruleId, version, actorId);
        AssignmentRuleVersion active = rules.findActiveVersion(tenantId, ruleId).orElseThrow();
        mutation(tenantId, actorId, "ACTIVATE", "ASSIGNMENT_RULE_VERSION", active.id(),
                snapshot(target), snapshot(active), "crm.assignment_rule_version.activated",
                "Assignment rule version activated");
        if (rule.currentVersion() == version && active.version() != version) {
            throw new OwnershipDomainException("Rule activation postcondition failed");
        }
        return active;
    }

    /** Non-mutating simulation. Round-robin reads the current counter without creating/incrementing it. */
    public AssignmentDecision simulate(UUID tenantId, EvaluationInput input) {
        return evaluate(tenantId, input, false);
    }

    /** Produces a decision and atomically consumes a round-robin counter when that strategy wins. */
    @Transactional
    public AssignmentDecision decide(UUID tenantId, EvaluationInput input) {
        return evaluate(tenantId, input, true);
    }

    private AssignmentDecision evaluate(UUID tenantId, EvaluationInput input, boolean consumeCounter) {
        if (tenantId == null || input == null || input.recordType() == null) {
            throw new OwnershipDomainException("tenantId and recordType required for rule evaluation");
        }
        List<String> trace = new ArrayList<>();
        for (AssignmentRuleVersion version :
                rules.findActiveVersionsByRecordType(tenantId, input.recordType())) {
            trace.add("rule=" + version.ruleId() + " version=" + version.version()
                    + " priority=" + version.priority());
            if (!matches(version.matchConditions(), input.facts(), trace)) {
                trace.add("result=NO_MATCH");
                continue;
            }
            trace.add("result=MATCH");
            Optional<OwnerSelection> selection = select(
                    tenantId, version, input, consumeCounter, trace);
            if (selection.isPresent()) {
                OwnerSelection owner = selection.get();
                return new AssignmentDecision(
                        true, version.ruleId(), version.version(), version.distributionMethod(),
                        owner.type(), owner.id(), false, trace);
            }
            if (version.fallbackOwnerId() != null
                    && users.isActiveUser(tenantId, version.fallbackOwnerId())) {
                trace.add("fallback=USER:" + version.fallbackOwnerId());
                return new AssignmentDecision(
                        true, version.ruleId(), version.version(), version.distributionMethod(),
                        OwnerType.USER, version.fallbackOwnerId(), true, trace);
            }
            throw new OwnershipDomainException(
                    "Matched rule produced no eligible owner and no active fallback: " + version.ruleId());
        }
        trace.add("no_active_rule_matched");
        return AssignmentDecision.noMatch(trace);
    }

    private boolean matches(String conditionsJson, Map<String, Object> facts, List<String> trace) {
        JsonNode conditions = jsonObject(conditionsJson, "matchConditions");
        JsonNode factNode = mapper.valueToTree(facts == null ? Map.of() : new LinkedHashMap<>(facts));
        var fields = conditions.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> condition = fields.next();
            JsonNode actual = factNode.get(condition.getKey());
            boolean match = actual != null && actual.equals(condition.getValue());
            trace.add("condition=" + condition.getKey() + " matched=" + match);
            if (!match) return false;
        }
        return true;
    }

    private Optional<OwnerSelection> select(UUID tenantId,
                                            AssignmentRuleVersion version,
                                            EvaluationInput input,
                                            boolean consumeCounter,
                                            List<String> trace) {
        return switch (version.distributionMethod()) {
            case DIRECT_OWNER -> activeUser(tenantId, version.targetOwnerId(), trace);
            case TEAM_ASSIGNMENT -> activeTeam(tenantId, version.targetTeamId(), trace)
                    .map(team -> new OwnerSelection(OwnerType.TEAM, team.id()));
            case QUEUE_ASSIGNMENT -> activeQueue(
                    tenantId, version.targetQueueId(), version.recordType(), trace)
                    .map(queue -> new OwnerSelection(OwnerType.QUEUE, queue.id()));
            case ROUND_ROBIN -> roundRobin(tenantId, version, consumeCounter, trace);
            case LEAST_LOADED -> leastLoaded(tenantId, version, trace);
            case TERRITORY_BASED -> territorySelection(tenantId, input.territoryIds(), trace);
            case WEIGHTED, SKILL_BASED, RULE_CHAIN -> throw new OwnershipDomainException(
                    "Distribution method is outside CRM-008B WP-06 approved executable scope: "
                            + version.distributionMethod());
        };
    }

    private Optional<OwnerSelection> activeUser(UUID tenantId, UUID userId, List<String> trace) {
        boolean active = userId != null && users.isActiveUser(tenantId, userId);
        trace.add("direct_owner_active=" + active);
        return active ? Optional.of(new OwnerSelection(OwnerType.USER, userId)) : Optional.empty();
    }

    private Optional<SalesTeam> activeTeam(UUID tenantId, UUID teamId, List<String> trace) {
        Optional<SalesTeam> team = teamId == null ? Optional.empty() : teams.findById(tenantId, teamId)
                .filter(SalesTeam::isActive);
        trace.add("target_team_active=" + team.isPresent());
        return team;
    }

    private Optional<Queue> activeQueue(UUID tenantId,
                                        UUID queueId,
                                        AssignmentRecordType recordType,
                                        List<String> trace) {
        Optional<Queue> queue = queueId == null ? Optional.empty() : queues.findById(tenantId, queueId)
                .filter(Queue::acceptsNewItems)
                .filter(value -> value.recordType().name().equals(recordType.name()));
        trace.add("target_queue_active_and_compatible=" + queue.isPresent());
        return queue;
    }

    private Optional<OwnerSelection> roundRobin(UUID tenantId,
                                                AssignmentRuleVersion version,
                                                boolean consume,
                                                List<String> trace) {
        List<TeamMembership> eligible = eligibleMembers(tenantId, version.targetTeamId(), trace);
        if (eligible.isEmpty()) return Optional.empty();
        long nextCounter = consume
                ? rules.incrementCounter(tenantId, version.ruleId()).counter()
                : rules.findCounter(tenantId, version.ruleId()).map(AssignmentRuleCounter::counter).orElse(0L) + 1L;
        int index = Math.floorMod(nextCounter - 1L, eligible.size());
        UUID selected = eligible.get(index).userId();
        trace.add("round_robin_counter=" + nextCounter + " index=" + index + " user=" + selected);
        return Optional.of(new OwnerSelection(OwnerType.USER, selected));
    }

    private Optional<OwnerSelection> leastLoaded(UUID tenantId,
                                                 AssignmentRuleVersion version,
                                                 List<String> trace) {
        List<TeamMembership> eligible = eligibleMembers(tenantId, version.targetTeamId(), trace);
        Optional<TeamMembership> selected = eligible.stream()
                .min(Comparator
                        .comparingLong((TeamMembership value) ->
                                assignments.countActiveByUser(tenantId, value.userId()))
                        .thenComparing(value -> value.userId().toString()));
        selected.ifPresent(value -> trace.add("least_loaded_user=" + value.userId()
                + " load=" + assignments.countActiveByUser(tenantId, value.userId())));
        return selected.map(value -> new OwnerSelection(OwnerType.USER, value.userId()));
    }

    private List<TeamMembership> eligibleMembers(UUID tenantId, UUID teamId, List<String> trace) {
        if (activeTeam(tenantId, teamId, trace).isEmpty()) return List.of();
        List<TeamMembership> eligible = teamMemberships.findActiveByTeam(tenantId, teamId).stream()
                .filter(value -> users.isActiveUser(tenantId, value.userId()))
                .filter(value -> assignments.countActiveByUser(tenantId, value.userId()) < value.capacityMax())
                .sorted(Comparator.comparing(value -> value.userId().toString()))
                .toList();
        trace.add("eligible_team_members=" + eligible.size());
        return eligible;
    }

    private Optional<OwnerSelection> territorySelection(UUID tenantId,
                                                         List<UUID> territoryIds,
                                                         List<String> trace) {
        Optional<TerritoryAssignment> resolved = territories.resolve(tenantId, territoryIds);
        trace.add("territory_resolution=" + resolved.map(TerritoryAssignment::id).orElse(null));
        return resolved.map(value -> new OwnerSelection(
                value.assigneeType() == AssigneeType.USER ? OwnerType.USER : OwnerType.TEAM,
                value.assigneeId()));
    }

    private void validateDefinition(UUID tenantId, VersionDefinition definition) {
        if (definition == null || definition.recordType() == null
                || definition.distributionMethod() == null) {
            throw new OwnershipDomainException("Complete assignment-rule definition required");
        }
        jsonObject(definition.matchConditions(), "matchConditions");
        if (definition.fallbackOwnerId() != null
                && !users.isActiveUser(tenantId, definition.fallbackOwnerId())) {
            throw new OwnershipDomainException("Fallback owner must be ACTIVE in same tenant");
        }
        List<String> trace = new ArrayList<>();
        switch (definition.distributionMethod()) {
            case DIRECT_OWNER -> {
                if (activeUser(tenantId, definition.targetOwnerId(), trace).isEmpty())
                    throw new OwnershipDomainException("DIRECT_OWNER requires active same-tenant user");
            }
            case TEAM_ASSIGNMENT, ROUND_ROBIN, LEAST_LOADED -> {
                if (activeTeam(tenantId, definition.targetTeamId(), trace).isEmpty())
                    throw new OwnershipDomainException("Strategy requires active same-tenant team");
            }
            case QUEUE_ASSIGNMENT -> {
                if (activeQueue(tenantId, definition.targetQueueId(), definition.recordType(), trace).isEmpty())
                    throw new OwnershipDomainException("QUEUE_ASSIGNMENT requires active compatible queue");
            }
            case TERRITORY_BASED -> { }
            case WEIGHTED, SKILL_BASED, RULE_CHAIN -> throw new OwnershipDomainException(
                    "Strategy is outside approved WP-06 executable scope: " + definition.distributionMethod());
        }
    }

    private AssignmentRuleVersion toVersion(UUID tenantId,
                                            UUID ruleId,
                                            int version,
                                            RuleStatus status,
                                            UUID actorId,
                                            VersionDefinition definition) {
        return new AssignmentRuleVersion(
                null, tenantId, ruleId, version,
                text(definition.displayName(), "displayName", 200),
                optional(definition.description(), 1000), definition.recordType(), definition.priority(),
                normalizeJson(definition.matchConditions()), definition.distributionMethod(),
                definition.targetOwnerId(), definition.targetTeamId(), definition.targetQueueId(),
                definition.fallbackOwnerId(),
                definition.effectiveFrom() != null ? definition.effectiveFrom() : Instant.now(),
                definition.effectiveTo(), status, actorId, null);
    }

    private AssignmentRule requireRule(UUID tenantId, UUID ruleId) {
        if (tenantId == null || ruleId == null) throw new AssignmentRuleNotFoundException(tenantId, ruleId);
        return rules.findById(tenantId, ruleId)
                .orElseThrow(() -> new AssignmentRuleNotFoundException(tenantId, ruleId));
    }

    private JsonNode jsonObject(String json, String field) {
        try {
            JsonNode node = mapper.readTree(normalizeJson(json));
            if (!node.isObject()) throw new OwnershipDomainException(field + " must be JSON object");
            return node;
        } catch (OwnershipDomainException domain) { throw domain; }
        catch (Exception invalid) { throw new OwnershipDomainException("Invalid " + field + " JSON", invalid); }
    }

    private String normalizeJson(String json) { return json == null || json.isBlank() ? "{}" : json.trim(); }

    private JsonNode snapshot(Object value) {
        ObjectNode node = mapper.createObjectNode();
        if (value instanceof AssignmentRule rule) {
            put(node, "id", rule.id()); node.put("code", rule.code());
            node.put("currentVersion", rule.currentVersion()); node.put("status", rule.status().name());
        } else if (value instanceof AssignmentRuleVersion version) {
            put(node, "id", version.id()); put(node, "ruleId", version.ruleId());
            node.put("version", version.version()); node.put("method", version.distributionMethod().name());
            node.put("priority", version.priority()); node.put("status", version.status().name());
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

    private record OwnerSelection(OwnerType type, UUID id) {}

    public record CreateRuleCommand(String code, VersionDefinition definition) {}
    public record VersionDefinition(
            String displayName,
            String description,
            AssignmentRecordType recordType,
            int priority,
            String matchConditions,
            DistributionMethod distributionMethod,
            UUID targetOwnerId,
            UUID targetTeamId,
            UUID targetQueueId,
            UUID fallbackOwnerId,
            Instant effectiveFrom,
            Instant effectiveTo
    ) {}
    public record EvaluationInput(
            AssignmentRecordType recordType,
            Map<String, Object> facts,
            List<UUID> territoryIds
    ) {
        public EvaluationInput {
            facts = facts == null ? Map.of() : Map.copyOf(facts);
            territoryIds = territoryIds == null ? List.of() : List.copyOf(territoryIds);
        }
    }
}
