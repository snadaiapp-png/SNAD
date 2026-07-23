package com.sanad.platform.crm.web;

import com.sanad.platform.crm.ownership.application.QueueUseCases;
import com.sanad.platform.crm.ownership.application.SalesTeamUseCases;
import com.sanad.platform.crm.ownership.application.TerritoryUseCases;
import com.sanad.platform.crm.ownership.domain.*;
import com.sanad.platform.security.authorization.RequireCapability;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** CRM-008 governed resource APIs: 8 teams + 8 queues + 6 territories. */
@RestController
@RequestMapping("/api/v2/crm")
public class CrmOwnershipResourceController {

    private final SalesTeamUseCases teams;
    private final QueueUseCases queues;
    private final TerritoryUseCases territories;
    private final CrmOwnershipHttpSupport http;

    public CrmOwnershipResourceController(
            SalesTeamUseCases teams,
            QueueUseCases queues,
            TerritoryUseCases territories,
            CrmOwnershipHttpSupport http) {
        this.teams = teams;
        this.queues = queues;
        this.territories = territories;
        this.http = http;
    }

    // ---------------------------------------------------------------------
    // Teams — 8 operations
    // ---------------------------------------------------------------------

    @RequireCapability("CRM.TEAM.READ")
    @GetMapping("/teams")
    public ResponseEntity<CrmOwnershipHttpSupport.OwnershipListResponse<SalesTeam>> listTeams(
            Authentication authentication,
            @RequestParam(required = false) TeamStatus status,
            @RequestParam(defaultValue = "100") @Min(1) @Max(100) int pageSize,
            HttpServletRequest request) {
        var context = http.context(authentication);
        List<SalesTeam> data = teams.listTeams(
                context.tenantId(), status == null ? TeamStatus.ACTIVE : status);
        List<SalesTeam> bounded = data.stream().limit(pageSize).toList();
        return http.list(bounded, null, data.size() > bounded.size(), pageSize, http.trace(request));
    }

    @RequireCapability("CRM.TEAM.ADMIN")
    @PostMapping("/teams")
    public ResponseEntity<CrmOwnershipHttpSupport.OwnershipResponse<SalesTeam>> createTeam(
            Authentication authentication,
            @Valid @RequestBody CreateTeamRequest body,
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            HttpServletRequest request) {
        var guard = http.begin(authentication, "POST:/api/v2/crm/teams", key, body, request);
        if (guard.isReplay()) return http.replay(guard, SalesTeam.class);
        try {
            var context = http.context(authentication);
            SalesTeam created = teams.createTeam(
                    context.tenantId(), context.userId(),
                    new SalesTeamUseCases.CreateTeamCommand(
                            body.code(), body.displayName(), body.description(),
                            body.managerUserId(), body.defaultQueueId(), body.defaultTerritoryId()));
            return http.complete(
                    guard, created, SalesTeam.class, HttpStatus.CREATED,
                    "sales-team", created.id(), http.timestampVersion(created.updatedAt()));
        } catch (RuntimeException error) {
            http.fail(guard);
            throw error;
        }
    }

    @RequireCapability("CRM.TEAM.READ")
    @GetMapping("/teams/{teamId}")
    public ResponseEntity<CrmOwnershipHttpSupport.OwnershipResponse<TeamDetail>> getTeam(
            Authentication authentication,
            @PathVariable UUID teamId,
            HttpServletRequest request) {
        var context = http.context(authentication);
        SalesTeam team = teams.getTeam(context.tenantId(), teamId);
        TeamDetail detail = new TeamDetail(
                team, teams.listActiveMemberships(context.tenantId(), teamId));
        return http.single(
                detail, http.trace(request), HttpStatus.OK,
                "sales-team", team.id(), http.timestampVersion(team.updatedAt()));
    }

    @RequireCapability("CRM.TEAM.ADMIN")
    @PatchMapping("/teams/{teamId}")
    public ResponseEntity<CrmOwnershipHttpSupport.OwnershipResponse<SalesTeam>> updateTeam(
            Authentication authentication,
            @PathVariable UUID teamId,
            @Valid @RequestBody UpdateTeamRequest body,
            @RequestHeader(value = "If-Match", required = false) String ifMatch,
            HttpServletRequest request) {
        var context = http.context(authentication);
        SalesTeam current = teams.getTeam(context.tenantId(), teamId);
        http.validateIfMatch(
                ifMatch, "sales-team", teamId, http.timestampVersion(current.updatedAt()));
        SalesTeam updated = teams.updateTeam(
                context.tenantId(), context.userId(), teamId,
                new SalesTeamUseCases.UpdateTeamCommand(
                        body.displayName(), body.description(), body.status(),
                        body.managerUserId(), body.defaultQueueId(), body.defaultTerritoryId()));
        return http.single(
                updated, http.trace(request), HttpStatus.OK,
                "sales-team", updated.id(), http.timestampVersion(updated.updatedAt()));
    }

    @RequireCapability("CRM.TEAM.READ")
    @GetMapping("/teams/{teamId}/memberships")
    public ResponseEntity<CrmOwnershipHttpSupport.OwnershipListResponse<TeamMembership>> listTeamMemberships(
            Authentication authentication,
            @PathVariable UUID teamId,
            HttpServletRequest request) {
        var context = http.context(authentication);
        List<TeamMembership> data = teams.listActiveMemberships(context.tenantId(), teamId);
        return http.list(data, null, false, Math.max(1, data.size()), http.trace(request));
    }

    @RequireCapability("CRM.TEAM.ADMIN")
    @PostMapping("/teams/{teamId}/memberships")
    public ResponseEntity<CrmOwnershipHttpSupport.OwnershipResponse<TeamMembership>> addTeamMembership(
            Authentication authentication,
            @PathVariable UUID teamId,
            @Valid @RequestBody AddTeamMembershipRequest body,
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            HttpServletRequest request) {
        String endpoint = "POST:/api/v2/crm/teams/" + teamId + "/memberships";
        var guard = http.begin(authentication, endpoint, key, body, request);
        if (guard.isReplay()) return http.replay(guard, TeamMembership.class);
        try {
            var context = http.context(authentication);
            TeamMembership created = teams.addMembership(
                    context.tenantId(), context.userId(), teamId,
                    new SalesTeamUseCases.AddMembershipCommand(
                            body.userId(), body.role(), body.primary(),
                            body.capacityMax(), body.metadata()));
            return http.complete(
                    guard, created, TeamMembership.class, HttpStatus.CREATED,
                    "team-membership", created.id(), http.timestampVersion(created.updatedAt()));
        } catch (RuntimeException error) {
            http.fail(guard);
            throw error;
        }
    }

    @RequireCapability("CRM.TEAM.ADMIN")
    @PatchMapping("/teams/{teamId}/memberships/{membershipId}")
    public ResponseEntity<CrmOwnershipHttpSupport.OwnershipResponse<TeamMembership>> updateTeamMembership(
            Authentication authentication,
            @PathVariable UUID teamId,
            @PathVariable UUID membershipId,
            @Valid @RequestBody UpdateTeamMembershipRequest body,
            @RequestHeader(value = "If-Match", required = false) String ifMatch,
            HttpServletRequest request) {
        var context = http.context(authentication);
        TeamMembership current = membership(
                teams.listActiveMemberships(context.tenantId(), teamId), membershipId);
        http.validateIfMatch(
                ifMatch, "team-membership", membershipId,
                http.timestampVersion(current.updatedAt()));
        TeamMembership updated = teams.updateMembership(
                context.tenantId(), context.userId(), teamId, membershipId,
                new SalesTeamUseCases.UpdateMembershipCommand(
                        body.role(), body.primary(), body.capacityMax(), body.metadata()));
        return http.single(
                updated, http.trace(request), HttpStatus.OK,
                "team-membership", updated.id(), http.timestampVersion(updated.updatedAt()));
    }

    @RequireCapability("CRM.TEAM.ADMIN")
    @DeleteMapping("/teams/{teamId}/memberships/{membershipId}")
    public ResponseEntity<CrmOwnershipHttpSupport.OwnershipResponse<TeamMembership>> endTeamMembership(
            Authentication authentication,
            @PathVariable UUID teamId,
            @PathVariable UUID membershipId,
            @RequestParam @NotBlank String reason,
            @RequestHeader(value = "If-Match", required = false) String ifMatch,
            HttpServletRequest request) {
        var context = http.context(authentication);
        TeamMembership current = membership(
                teams.listActiveMemberships(context.tenantId(), teamId), membershipId);
        http.validateIfMatch(
                ifMatch, "team-membership", membershipId,
                http.timestampVersion(current.updatedAt()));
        TeamMembership ended = teams.endMembership(
                context.tenantId(), context.userId(), teamId, membershipId, reason);
        return http.single(
                ended, http.trace(request), HttpStatus.OK,
                "team-membership", ended.id(), http.timestampVersion(ended.updatedAt()));
    }

    // ---------------------------------------------------------------------
    // Queues — 8 operations
    // ---------------------------------------------------------------------

    @RequireCapability("CRM.QUEUE.READ")
    @GetMapping("/queues")
    public ResponseEntity<CrmOwnershipHttpSupport.OwnershipListResponse<Queue>> listQueues(
            Authentication authentication,
            @RequestParam(required = false) QueueStatus status,
            @RequestParam(defaultValue = "100") @Min(1) @Max(100) int pageSize,
            HttpServletRequest request) {
        var context = http.context(authentication);
        List<Queue> data = queues.listQueues(context.tenantId(), status);
        List<Queue> bounded = data.stream().limit(pageSize).toList();
        return http.list(bounded, null, data.size() > bounded.size(), pageSize, http.trace(request));
    }

    @RequireCapability("CRM.QUEUE.ADMIN")
    @PostMapping("/queues")
    public ResponseEntity<CrmOwnershipHttpSupport.OwnershipResponse<Queue>> createQueue(
            Authentication authentication,
            @Valid @RequestBody CreateQueueRequest body,
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            HttpServletRequest request) {
        var guard = http.begin(authentication, "POST:/api/v2/crm/queues", key, body, request);
        if (guard.isReplay()) return http.replay(guard, Queue.class);
        try {
            var context = http.context(authentication);
            Queue created = queues.createQueue(
                    context.tenantId(), context.userId(),
                    new QueueUseCases.CreateQueueCommand(
                            body.code(), body.displayName(), body.recordType(), body.description(),
                            body.maxItemsPerUser(), body.slaMinutes(),
                            body.escalationTargetQueueId(), body.defaultOwnerId()));
            return http.complete(
                    guard, created, Queue.class, HttpStatus.CREATED,
                    "queue", created.id(), http.timestampVersion(created.updatedAt()));
        } catch (RuntimeException error) {
            http.fail(guard);
            throw error;
        }
    }

    @RequireCapability("CRM.QUEUE.READ")
    @GetMapping("/queues/{queueId}")
    public ResponseEntity<CrmOwnershipHttpSupport.OwnershipResponse<QueueDetail>> getQueue(
            Authentication authentication,
            @PathVariable UUID queueId,
            HttpServletRequest request) {
        var context = http.context(authentication);
        Queue queue = queues.getQueue(context.tenantId(), queueId);
        QueueDetail detail = new QueueDetail(
                queue,
                queues.waitingCount(context.tenantId(), queueId),
                queues.listMemberships(context.tenantId(), queueId));
        return http.single(
                detail, http.trace(request), HttpStatus.OK,
                "queue", queue.id(), http.timestampVersion(queue.updatedAt()));
    }

    @RequireCapability("CRM.QUEUE.ADMIN")
    @PatchMapping("/queues/{queueId}")
    public ResponseEntity<CrmOwnershipHttpSupport.OwnershipResponse<Queue>> updateQueue(
            Authentication authentication,
            @PathVariable UUID queueId,
            @Valid @RequestBody UpdateQueueRequest body,
            @RequestHeader(value = "If-Match", required = false) String ifMatch,
            HttpServletRequest request) {
        var context = http.context(authentication);
        Queue current = queues.getQueue(context.tenantId(), queueId);
        http.validateIfMatch(
                ifMatch, "queue", queueId, http.timestampVersion(current.updatedAt()));
        Queue updated = queues.updateQueue(
                context.tenantId(), context.userId(), queueId,
                new QueueUseCases.UpdateQueueCommand(
                        body.displayName(), body.descriptionSet(), body.description(),
                        body.status(), body.maxItemsPerUser(),
                        body.slaMinutesSet(), body.slaMinutes(),
                        body.escalationTargetQueueIdSet(), body.escalationTargetQueueId(),
                        body.defaultOwnerIdSet(), body.defaultOwnerId()));
        return http.single(
                updated, http.trace(request), HttpStatus.OK,
                "queue", updated.id(), http.timestampVersion(updated.updatedAt()));
    }

    @RequireCapability("CRM.QUEUE.READ")
    @GetMapping("/queues/{queueId}/items")
    public ResponseEntity<CrmOwnershipHttpSupport.OwnershipListResponse<QueueItemSummary>> listQueueItems(
            Authentication authentication,
            @PathVariable UUID queueId,
            @RequestParam(required = false) UUID cursor,
            @RequestParam(defaultValue = "25") @Min(1) @Max(100) int pageSize,
            HttpServletRequest request) {
        var context = http.context(authentication);
        QueueItemPage page = queues.listItems(context.tenantId(), queueId, cursor, pageSize);
        return http.list(
                page.items(), page.nextCursor() == null ? null : page.nextCursor().toString(),
                page.hasMore(), pageSize, http.trace(request));
    }

    @RequireCapability("CRM.QUEUE.CLAIM")
    @PostMapping("/queues/{queueId}/items/{itemId}/claim")
    public ResponseEntity<CrmOwnershipHttpSupport.OwnershipResponse<QueueUseCases.ClaimResult>> claimQueueItem(
            Authentication authentication,
            @PathVariable UUID queueId,
            @PathVariable UUID itemId,
            @Valid @RequestBody ClaimQueueItemRequest body,
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            HttpServletRequest request) {
        var context = http.context(authentication);
        UUID idempotencyKey = requiredUuid(key, "Idempotency-Key");
        QueueUseCases.ClaimResult result = queues.claim(
                context.tenantId(), context.userId(), queueId,
                new QueueUseCases.ClaimCommand(
                        body.recordType(), itemId, idempotencyKey,
                        http.trace(request).correlationId()));
        return http.single(
                result, http.trace(request), HttpStatus.OK,
                "assignment", result.assignment().id(), result.assignment().version());
    }

    @RequireCapability("CRM.QUEUE.CLAIM")
    @PostMapping("/queues/{queueId}/items/{itemId}/release")
    public ResponseEntity<CrmOwnershipHttpSupport.OwnershipResponse<Assignment>> releaseQueueItem(
            Authentication authentication,
            @PathVariable UUID queueId,
            @PathVariable UUID itemId,
            @Valid @RequestBody ReleaseQueueItemRequest body,
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            HttpServletRequest request) {
        String endpoint = "POST:/api/v2/crm/queues/" + queueId + "/items/" + itemId + "/release";
        var guard = http.begin(authentication, endpoint, key, body, request);
        if (guard.isReplay()) return http.replay(guard, Assignment.class);
        try {
            var context = http.context(authentication);
            Assignment released = queues.release(
                    context.tenantId(), context.userId(), queueId,
                    new QueueUseCases.ReleaseCommand(
                            body.recordType(), itemId, body.reason(), guard.trace().correlationId()));
            return http.complete(
                    guard, released, Assignment.class, HttpStatus.OK,
                    "assignment", released.id(), released.version());
        } catch (RuntimeException error) {
            http.fail(guard);
            throw error;
        }
    }

    @RequireCapability("CRM.QUEUE.READ")
    @GetMapping("/queues/{queueId}/memberships")
    public ResponseEntity<CrmOwnershipHttpSupport.OwnershipListResponse<QueueMembership>> listQueueMemberships(
            Authentication authentication,
            @PathVariable UUID queueId,
            HttpServletRequest request) {
        var context = http.context(authentication);
        List<QueueMembership> data = queues.listMemberships(context.tenantId(), queueId);
        return http.list(data, null, false, Math.max(1, data.size()), http.trace(request));
    }

    // ---------------------------------------------------------------------
    // Territories — 6 operations
    // ---------------------------------------------------------------------

    @RequireCapability("CRM.TERRITORY.READ")
    @GetMapping("/territories")
    public ResponseEntity<CrmOwnershipHttpSupport.OwnershipListResponse<Territory>> listTerritories(
            Authentication authentication,
            @RequestParam(required = false) TerritoryStatus status,
            @RequestParam(defaultValue = "100") @Min(1) @Max(100) int pageSize,
            HttpServletRequest request) {
        var context = http.context(authentication);
        List<Territory> data = territories.list(context.tenantId(), status);
        List<Territory> bounded = data.stream().limit(pageSize).toList();
        return http.list(bounded, null, data.size() > bounded.size(), pageSize, http.trace(request));
    }

    @RequireCapability("CRM.TERRITORY.ADMIN")
    @PostMapping("/territories")
    public ResponseEntity<CrmOwnershipHttpSupport.OwnershipResponse<Territory>> createTerritory(
            Authentication authentication,
            @Valid @RequestBody CreateTerritoryRequest body,
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            HttpServletRequest request) {
        var guard = http.begin(authentication, "POST:/api/v2/crm/territories", key, body, request);
        if (guard.isReplay()) return http.replay(guard, Territory.class);
        try {
            var context = http.context(authentication);
            Territory created = territories.create(
                    context.tenantId(), context.userId(),
                    new TerritoryUseCases.CreateCommand(
                            body.code(), body.displayName(), body.parentId(), body.description(),
                            body.ruleType(), body.ruleDefinition(), body.priority()));
            return http.complete(
                    guard, created, Territory.class, HttpStatus.CREATED,
                    "territory", created.id(), http.timestampVersion(created.updatedAt()));
        } catch (RuntimeException error) {
            http.fail(guard);
            throw error;
        }
    }

    @RequireCapability("CRM.TERRITORY.READ")
    @GetMapping("/territories/{territoryId}")
    public ResponseEntity<CrmOwnershipHttpSupport.OwnershipResponse<TerritoryDetail>> getTerritory(
            Authentication authentication,
            @PathVariable UUID territoryId,
            HttpServletRequest request) {
        var context = http.context(authentication);
        Territory territory = territories.get(context.tenantId(), territoryId);
        TerritoryDetail detail = new TerritoryDetail(
                territory,
                territories.children(context.tenantId(), territoryId),
                territories.activeAssignments(context.tenantId(), territoryId));
        return http.single(
                detail, http.trace(request), HttpStatus.OK,
                "territory", territory.id(), http.timestampVersion(territory.updatedAt()));
    }

    @RequireCapability("CRM.TERRITORY.ADMIN")
    @PatchMapping("/territories/{territoryId}")
    public ResponseEntity<CrmOwnershipHttpSupport.OwnershipResponse<Territory>> updateTerritory(
            Authentication authentication,
            @PathVariable UUID territoryId,
            @Valid @RequestBody UpdateTerritoryRequest body,
            @RequestHeader(value = "If-Match", required = false) String ifMatch,
            HttpServletRequest request) {
        var context = http.context(authentication);
        Territory current = territories.get(context.tenantId(), territoryId);
        http.validateIfMatch(
                ifMatch, "territory", territoryId,
                http.timestampVersion(current.updatedAt()));
        Territory updated = territories.update(
                context.tenantId(), context.userId(), territoryId,
                new TerritoryUseCases.UpdateCommand(
                        body.displayName(), body.parentIdSet(), body.parentId(),
                        body.descriptionSet(), body.description(), body.ruleType(),
                        body.ruleDefinitionSet(), body.ruleDefinition(), body.priority()));
        return http.single(
                updated, http.trace(request), HttpStatus.OK,
                "territory", updated.id(), http.timestampVersion(updated.updatedAt()));
    }

    @RequireCapability("CRM.TERRITORY.ADMIN")
    @PostMapping("/territories/{territoryId}/assignments")
    public ResponseEntity<CrmOwnershipHttpSupport.OwnershipResponse<TerritoryAssignment>> assignTerritory(
            Authentication authentication,
            @PathVariable UUID territoryId,
            @Valid @RequestBody AssignTerritoryRequest body,
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            HttpServletRequest request) {
        String endpoint = "POST:/api/v2/crm/territories/" + territoryId + "/assignments";
        var guard = http.begin(authentication, endpoint, key, body, request);
        if (guard.isReplay()) return http.replay(guard, TerritoryAssignment.class);
        try {
            var context = http.context(authentication);
            TerritoryAssignment created = territories.assign(
                    context.tenantId(), context.userId(), territoryId,
                    new TerritoryUseCases.AssignCommand(
                            body.assigneeType(), body.assigneeId(), body.role(), body.priority(),
                            body.effectiveFrom(), body.effectiveTo()));
            return http.complete(
                    guard, created, TerritoryAssignment.class, HttpStatus.CREATED,
                    "territory-assignment", created.id(), http.timestampVersion(created.updatedAt()));
        } catch (RuntimeException error) {
            http.fail(guard);
            throw error;
        }
    }

    @RequireCapability("CRM.TERRITORY.ADMIN")
    @DeleteMapping("/territories/{territoryId}/assignments/{assignmentId}")
    public ResponseEntity<CrmOwnershipHttpSupport.OwnershipResponse<TerritoryAssignment>> removeTerritoryAssignment(
            Authentication authentication,
            @PathVariable UUID territoryId,
            @PathVariable UUID assignmentId,
            @RequestHeader(value = "If-Match", required = false) String ifMatch,
            HttpServletRequest request) {
        var context = http.context(authentication);
        TerritoryAssignment current = territories.activeAssignments(context.tenantId(), territoryId).stream()
                .filter(value -> assignmentId.equals(value.id()))
                .findFirst()
                .orElseThrow(() -> new OwnershipDomainException(
                        "Active territory assignment not found on path: " + assignmentId));
        http.validateIfMatch(
                ifMatch, "territory-assignment", assignmentId,
                http.timestampVersion(current.updatedAt()));
        TerritoryAssignment removed = territories.deactivate(
                context.tenantId(), context.userId(), territoryId, assignmentId);
        return http.single(
                removed, http.trace(request), HttpStatus.OK,
                "territory-assignment", removed.id(), http.timestampVersion(removed.updatedAt()));
    }

    private TeamMembership membership(List<TeamMembership> memberships, UUID id) {
        return memberships.stream().filter(value -> id.equals(value.id())).findFirst()
                .orElseThrow(() -> new OwnershipDomainException(
                        "Active team membership not found on path: " + id));
    }

    private UUID requiredUuid(String value, String header) {
        if (value == null || value.isBlank()) {
            throw new OwnershipDomainException(header + " header is required");
        }
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException invalid) {
            throw new OwnershipDomainException(header + " must be a UUID");
        }
    }

    public record TeamDetail(SalesTeam team, List<TeamMembership> memberships) { }

    public record QueueDetail(Queue queue, long waitingCount, List<QueueMembership> memberships) { }

    public record TerritoryDetail(
            Territory territory,
            List<Territory> children,
            List<TerritoryAssignment> assignments) { }

    public record CreateTeamRequest(
            @NotBlank String code,
            @NotBlank String displayName,
            String description,
            UUID managerUserId,
            UUID defaultQueueId,
            UUID defaultTerritoryId) { }

    public record UpdateTeamRequest(
            @NotBlank String displayName,
            String description,
            @NotNull TeamStatus status,
            UUID managerUserId,
            UUID defaultQueueId,
            UUID defaultTerritoryId) { }

    public record AddTeamMembershipRequest(
            @NotNull UUID userId,
            @NotNull MembershipRole role,
            boolean primary,
            @Min(0) @Max(1000) int capacityMax,
            String metadata) { }

    public record UpdateTeamMembershipRequest(
            @NotNull MembershipRole role,
            boolean primary,
            @Min(0) @Max(1000) int capacityMax,
            String metadata) { }

    public record CreateQueueRequest(
            @NotBlank String code,
            @NotBlank String displayName,
            @NotNull QueueRecordType recordType,
            String description,
            @Min(1) @Max(1000) int maxItemsPerUser,
            @Min(1) Integer slaMinutes,
            UUID escalationTargetQueueId,
            UUID defaultOwnerId) { }

    public record UpdateQueueRequest(
            String displayName,
            boolean descriptionSet,
            String description,
            QueueStatus status,
            @Min(1) @Max(1000) Integer maxItemsPerUser,
            boolean slaMinutesSet,
            @Min(1) Integer slaMinutes,
            boolean escalationTargetQueueIdSet,
            UUID escalationTargetQueueId,
            boolean defaultOwnerIdSet,
            UUID defaultOwnerId) { }

    public record ClaimQueueItemRequest(@NotNull AssignmentRecordType recordType) { }

    public record ReleaseQueueItemRequest(
            @NotNull AssignmentRecordType recordType,
            @NotBlank String reason) { }

    public record CreateTerritoryRequest(
            @NotBlank String code,
            @NotBlank String displayName,
            UUID parentId,
            String description,
            @NotNull TerritoryRuleType ruleType,
            String ruleDefinition,
            int priority) { }

    public record UpdateTerritoryRequest(
            String displayName,
            boolean parentIdSet,
            UUID parentId,
            boolean descriptionSet,
            String description,
            TerritoryRuleType ruleType,
            boolean ruleDefinitionSet,
            String ruleDefinition,
            Integer priority) { }

    public record AssignTerritoryRequest(
            @NotNull AssigneeType assigneeType,
            @NotNull UUID assigneeId,
            TerritoryAssignmentRole role,
            int priority,
            Instant effectiveFrom,
            Instant effectiveTo) { }
}
