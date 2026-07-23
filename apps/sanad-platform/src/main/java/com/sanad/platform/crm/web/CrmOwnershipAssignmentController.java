package com.sanad.platform.crm.web;

import com.sanad.platform.crm.ownership.application.AssignmentRuleUseCases;
import com.sanad.platform.crm.ownership.application.OwnershipCommandUseCases;
import com.sanad.platform.crm.ownership.application.OwnershipQueryUseCases;
import com.sanad.platform.crm.ownership.domain.*;
import com.sanad.platform.security.authorization.RequireCapability;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
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
import java.util.Map;
import java.util.UUID;

/** CRM-008 governed APIs: 6 assignment-rule + 4 assignment + 1 My Work operations. */
@RestController
@RequestMapping("/api/v2/crm")
public class CrmOwnershipAssignmentController {

    private final AssignmentRuleUseCases rules;
    private final OwnershipCommandUseCases commands;
    private final OwnershipQueryUseCases queries;
    private final CrmOwnershipHttpSupport http;

    public CrmOwnershipAssignmentController(
            AssignmentRuleUseCases rules,
            OwnershipCommandUseCases commands,
            OwnershipQueryUseCases queries,
            CrmOwnershipHttpSupport http) {
        this.rules = rules;
        this.commands = commands;
        this.queries = queries;
        this.http = http;
    }

    // ---------------------------------------------------------------------
    // Assignment rules — 6 operations. Rule detail includes all versions.
    // ---------------------------------------------------------------------

    @RequireCapability("CRM.ASSIGNMENT_RULE.READ")
    @GetMapping("/assignment-rules")
    public ResponseEntity<CrmOwnershipHttpSupport.OwnershipListResponse<AssignmentRule>> listRules(
            Authentication authentication,
            @RequestParam(required = false) RuleStatus status,
            @RequestParam(defaultValue = "100") @Min(1) @Max(100) int pageSize,
            HttpServletRequest request) {
        var context = http.context(authentication);
        List<AssignmentRule> data = rules.listRules(context.tenantId(), status);
        List<AssignmentRule> bounded = data.stream().limit(pageSize).toList();
        return http.list(bounded, null, data.size() > bounded.size(), pageSize, http.trace(request));
    }

    @RequireCapability("CRM.ASSIGNMENT_RULE.ADMIN")
    @PostMapping("/assignment-rules")
    public ResponseEntity<CrmOwnershipHttpSupport.OwnershipResponse<AssignmentRule>> createRule(
            Authentication authentication,
            @Valid @RequestBody CreateRuleRequest body,
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            HttpServletRequest request) {
        var guard = http.begin(authentication, "POST:/api/v2/crm/assignment-rules", key, body, request);
        if (guard.isReplay()) return http.replay(guard, AssignmentRule.class);
        try {
            var context = http.context(authentication);
            AssignmentRule created = rules.createRule(
                    context.tenantId(), context.userId(),
                    new AssignmentRuleUseCases.CreateRuleCommand(
                            body.code(), definition(body.definition())));
            return http.complete(
                    guard, created, AssignmentRule.class, HttpStatus.CREATED,
                    "assignment-rule", created.id(), http.timestampVersion(created.updatedAt()));
        } catch (RuntimeException error) {
            http.fail(guard);
            throw error;
        }
    }

    @RequireCapability("CRM.ASSIGNMENT_RULE.READ")
    @GetMapping("/assignment-rules/{ruleId}")
    public ResponseEntity<CrmOwnershipHttpSupport.OwnershipResponse<RuleDetail>> getRule(
            Authentication authentication,
            @PathVariable UUID ruleId,
            HttpServletRequest request) {
        var context = http.context(authentication);
        AssignmentRule rule = rules.getRule(context.tenantId(), ruleId);
        RuleDetail detail = new RuleDetail(rule, rules.listVersions(context.tenantId(), ruleId));
        return http.single(
                detail, http.trace(request), HttpStatus.OK,
                "assignment-rule", rule.id(), http.timestampVersion(rule.updatedAt()));
    }

    @RequireCapability("CRM.ASSIGNMENT_RULE.ADMIN")
    @PostMapping("/assignment-rules/{ruleId}/versions")
    public ResponseEntity<CrmOwnershipHttpSupport.OwnershipResponse<AssignmentRuleVersion>> createRuleVersion(
            Authentication authentication,
            @PathVariable UUID ruleId,
            @Valid @RequestBody VersionDefinitionRequest body,
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            HttpServletRequest request) {
        String endpoint = "POST:/api/v2/crm/assignment-rules/" + ruleId + "/versions";
        var guard = http.begin(authentication, endpoint, key, body, request);
        if (guard.isReplay()) return http.replay(guard, AssignmentRuleVersion.class);
        try {
            var context = http.context(authentication);
            AssignmentRuleVersion created = rules.createVersion(
                    context.tenantId(), context.userId(), ruleId, definition(body));
            return http.complete(
                    guard, created, AssignmentRuleVersion.class, HttpStatus.CREATED,
                    "assignment-rule-version", created.id(),
                    http.timestampVersion(created.updatedAt()));
        } catch (RuntimeException error) {
            http.fail(guard);
            throw error;
        }
    }

    @RequireCapability("CRM.ASSIGNMENT_RULE.ADMIN")
    @PatchMapping("/assignment-rules/{ruleId}/versions/{version}/activate")
    public ResponseEntity<CrmOwnershipHttpSupport.OwnershipResponse<AssignmentRuleVersion>> activateRuleVersion(
            Authentication authentication,
            @PathVariable UUID ruleId,
            @PathVariable int version,
            @RequestHeader(value = "If-Match", required = false) String ifMatch,
            HttpServletRequest request) {
        var context = http.context(authentication);
        AssignmentRule current = rules.getRule(context.tenantId(), ruleId);
        http.validateIfMatch(
                ifMatch, "assignment-rule", ruleId,
                http.timestampVersion(current.updatedAt()));
        AssignmentRuleVersion activated = rules.activateVersion(
                context.tenantId(), context.userId(), ruleId, version);
        return http.single(
                activated, http.trace(request), HttpStatus.OK,
                "assignment-rule-version", activated.id(),
                http.timestampVersion(activated.updatedAt()));
    }

    @RequireCapability("CRM.ASSIGNMENT_RULE.ADMIN")
    @PostMapping("/assignment-rules/{ruleId}/simulate")
    public ResponseEntity<CrmOwnershipHttpSupport.OwnershipResponse<AssignmentDecision>> simulateRule(
            Authentication authentication,
            @PathVariable UUID ruleId,
            @Valid @RequestBody SimulateRuleRequest body,
            HttpServletRequest request) {
        var context = http.context(authentication);
        AssignmentRule rule = rules.getRule(context.tenantId(), ruleId);
        AssignmentDecision decision = rules.simulate(
                context.tenantId(),
                new AssignmentRuleUseCases.EvaluationInput(
                        body.recordType(), body.facts(), body.territoryIds()));
        if (decision.matched() && !ruleId.equals(decision.ruleId())) {
            throw new OwnershipDomainException(
                    "Simulation matched another higher-priority rule; invoke the collection simulation endpoint instead");
        }
        return http.single(
                decision, http.trace(request), HttpStatus.OK,
                null, null, 0L);
    }

    // ---------------------------------------------------------------------
    // Assignments — 4 operations
    // ---------------------------------------------------------------------

    @RequireCapability("CRM.ASSIGNMENT.READ")
    @GetMapping("/assignments/{recordType}/{recordId}")
    public ResponseEntity<CrmOwnershipHttpSupport.OwnershipResponse<Assignment>> getCurrentAssignment(
            Authentication authentication,
            @PathVariable AssignmentRecordType recordType,
            @PathVariable UUID recordId,
            HttpServletRequest request) {
        var context = http.context(authentication);
        Assignment assignment = queries.current(context.tenantId(), recordType, recordId)
                .orElseThrow(() -> new AssignmentNotFoundException(
                        context.tenantId(), recordType, recordId));
        return http.single(
                assignment, http.trace(request), HttpStatus.OK,
                "assignment", assignment.id(), assignment.version());
    }

    @RequireCapability("CRM.ASSIGNMENT.WRITE")
    @PostMapping("/assignments/reassign")
    public ResponseEntity<CrmOwnershipHttpSupport.OwnershipResponse<Assignment>> reassign(
            Authentication authentication,
            @Valid @RequestBody ReassignRequest body,
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            HttpServletRequest request) {
        var guard = http.begin(authentication, "POST:/api/v2/crm/assignments/reassign", key, body, request);
        if (guard.isReplay()) return http.replay(guard, Assignment.class);
        try {
            var context = http.context(authentication);
            Assignment assignment = commands.reassign(
                    new OwnershipCommandUseCases.ReassignCommand(
                            context.tenantId(), body.recordType(), body.recordId(),
                            body.ownerType(), body.ownerId(), context.userId(), body.reason(),
                            guard.trace().requestId(), guard.trace().correlationId(),
                            body.expectedAssignmentId(), body.assignedByRuleId()));
            return http.complete(
                    guard, assignment, Assignment.class, HttpStatus.OK,
                    "assignment", assignment.id(), assignment.version());
        } catch (RuntimeException error) {
            http.fail(guard);
            throw error;
        }
    }

    @RequireCapability("CRM.ASSIGNMENT.ADMIN")
    @PostMapping("/assignments/bulk-reassign")
    public ResponseEntity<CrmOwnershipHttpSupport.OwnershipResponse<BulkAssignmentResponse>> bulkReassign(
            Authentication authentication,
            @Valid @RequestBody BulkReassignRequest body,
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            HttpServletRequest request) {
        var guard = http.begin(
                authentication, "POST:/api/v2/crm/assignments/bulk-reassign", key, body, request);
        if (guard.isReplay()) return http.replay(guard, BulkAssignmentResponse.class);
        try {
            var context = http.context(authentication);
            List<Assignment> assignments = commands.bulkReassign(
                    new OwnershipCommandUseCases.BulkReassignCommand(
                            context.tenantId(), body.recordType(), body.recordIds(),
                            body.ownerType(), body.ownerId(), context.userId(),
                            body.reason(), guard.trace().requestId()));
            BulkAssignmentResponse response = new BulkAssignmentResponse(assignments);
            return http.complete(
                    guard, response, BulkAssignmentResponse.class, HttpStatus.OK,
                    null, null, 0L);
        } catch (RuntimeException error) {
            http.fail(guard);
            throw error;
        }
    }

    @RequireCapability("CRM.OWNERSHIP_HISTORY.READ")
    @GetMapping("/ownership-history/{recordType}/{recordId}")
    public ResponseEntity<CrmOwnershipHttpSupport.OwnershipListResponse<OwnershipHistory>> history(
            Authentication authentication,
            @PathVariable AssignmentRecordType recordType,
            @PathVariable UUID recordId,
            @RequestParam(required = false) UUID cursor,
            @RequestParam(defaultValue = "25") @Min(1) @Max(100) int pageSize,
            HttpServletRequest request) {
        var context = http.context(authentication);
        OwnershipHistoryPage page = queries.history(
                context.tenantId(), recordType, recordId, cursor, pageSize);
        return http.list(
                page.entries(), page.nextCursor() == null ? null : page.nextCursor().toString(),
                page.hasMore(), pageSize, http.trace(request));
    }

    // ---------------------------------------------------------------------
    // My Work — 1 operation
    // ---------------------------------------------------------------------

    @RequireCapability("CRM.ASSIGNMENT.READ")
    @GetMapping("/my-work")
    public ResponseEntity<CrmOwnershipHttpSupport.OwnershipResponse<MyWorkResponse>> myWork(
            Authentication authentication,
            HttpServletRequest request) {
        var context = http.context(authentication);
        MyWorkResponse response = new MyWorkResponse(
                queries.workload(context.tenantId(), context.userId()),
                queries.queueClaimCount(context.tenantId(), context.userId()));
        return http.single(response, http.trace(request), HttpStatus.OK, null, null, 0L);
    }

    private AssignmentRuleUseCases.VersionDefinition definition(VersionDefinitionRequest body) {
        if (body == null) throw new OwnershipDomainException("Rule version definition required");
        return new AssignmentRuleUseCases.VersionDefinition(
                body.displayName(), body.description(), body.recordType(), body.priority(),
                body.matchConditions(), body.distributionMethod(), body.targetOwnerId(),
                body.targetTeamId(), body.targetQueueId(), body.fallbackOwnerId(),
                body.effectiveFrom(), body.effectiveTo());
    }

    public record RuleDetail(AssignmentRule rule, List<AssignmentRuleVersion> versions) { }

    public record BulkAssignmentResponse(List<Assignment> assignments) {
        public BulkAssignmentResponse {
            assignments = assignments == null ? List.of() : List.copyOf(assignments);
        }
    }

    public record MyWorkResponse(WorkloadSummary workload, int activeQueueClaims) { }

    public record CreateRuleRequest(
            @NotBlank String code,
            @NotNull @Valid VersionDefinitionRequest definition) { }

    public record VersionDefinitionRequest(
            @NotBlank String displayName,
            String description,
            @NotNull AssignmentRecordType recordType,
            int priority,
            String matchConditions,
            @NotNull DistributionMethod distributionMethod,
            UUID targetOwnerId,
            UUID targetTeamId,
            UUID targetQueueId,
            UUID fallbackOwnerId,
            Instant effectiveFrom,
            Instant effectiveTo) { }

    public record SimulateRuleRequest(
            @NotNull AssignmentRecordType recordType,
            Map<String, Object> facts,
            List<UUID> territoryIds) {
        public SimulateRuleRequest {
            facts = facts == null ? Map.of() : Map.copyOf(facts);
            territoryIds = territoryIds == null ? List.of() : List.copyOf(territoryIds);
        }
    }

    public record ReassignRequest(
            @NotNull AssignmentRecordType recordType,
            @NotNull UUID recordId,
            @NotNull OwnerType ownerType,
            @NotNull UUID ownerId,
            @NotBlank @Size(max = 1000) String reason,
            UUID expectedAssignmentId,
            UUID assignedByRuleId) { }

    public record BulkReassignRequest(
            @NotNull AssignmentRecordType recordType,
            @NotEmpty @Size(max = 100) List<UUID> recordIds,
            @NotNull OwnerType ownerType,
            @NotNull UUID ownerId,
            @NotBlank @Size(max = 1000) String reason) {
        public BulkReassignRequest {
            recordIds = recordIds == null ? List.of() : List.copyOf(recordIds);
        }
    }
}
