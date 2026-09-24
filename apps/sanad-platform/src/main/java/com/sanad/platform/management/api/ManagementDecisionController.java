package com.sanad.platform.management.api;

import com.sanad.platform.management.application.EscalationService;
import com.sanad.platform.management.application.ExecutiveDecisionService;
import com.sanad.platform.management.application.IssueService;
import com.sanad.platform.management.application.RiskService;
import com.sanad.platform.management.domain.Escalation;
import com.sanad.platform.management.domain.ExecutiveDecision;
import com.sanad.platform.management.domain.Issue;
import com.sanad.platform.management.domain.Risk;
import com.sanad.platform.security.authorization.RequireCapability;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.sanad.platform.security.SecurityContextUtils.tenantId;
import static com.sanad.platform.security.SecurityContextUtils.userId;

/**
 * Senior Management API — Decisions, Risks, Issues, and Escalations.
 *
 * <p>All endpoints require EXECUTIVE_DECISIONS.* / RISK.* / ISSUE.* / ESCALATION.*
 * capabilities respectively. Segregation of duties is enforced:
 * the user who created a decision cannot approve it.
 */
@RestController
@RequestMapping("/api/v1/management")
public class ManagementDecisionController {

    private final ExecutiveDecisionService decisionService;
    private final RiskService riskService;
    private final IssueService issueService;
    private final EscalationService escalationService;

    public ManagementDecisionController(
            ExecutiveDecisionService decisionService,
            RiskService riskService,
            IssueService issueService,
            EscalationService escalationService) {
        this.decisionService = decisionService;
        this.riskService = riskService;
        this.issueService = issueService;
        this.escalationService = escalationService;
    }

    // ===== Executive Decisions =====

    @PostMapping("/decisions")
    @RequireCapability("EXECUTIVE_DECISIONS.WRITE")
    public ResponseEntity<Map<String, Object>> createDecision(
            Authentication auth, @Valid @RequestBody DecisionRequest req) {
        var d = ExecutiveDecision.create(
                tenantId(auth), req.decisionNumber(), req.title(), req.description(),
                req.rationale(), req.category(),
                ExecutiveDecision.Priority.valueOf(req.priority()),
                req.impact(), req.expectedOutcome(),
                req.ownerUserId(), userId(auth), req.dueDate()
        );
        var saved = decisionService.create(d, userId(auth));
        return ResponseEntity.ok(toDecisionMap(saved));
    }

    @GetMapping("/decisions")
    @RequireCapability("EXECUTIVE_DECISIONS.VIEW")
    public ResponseEntity<List<Map<String, Object>>> listDecisions(
            Authentication auth, @RequestParam(defaultValue = "50") int limit) {
        var decisions = decisionService.findByTenant(tenantId(auth), limit);
        return ResponseEntity.ok(decisions.stream().map(this::toDecisionMap).toList());
    }

    @GetMapping("/decisions/{id}")
    @RequireCapability("EXECUTIVE_DECISIONS.VIEW")
    public ResponseEntity<Map<String, Object>> getDecision(
            Authentication auth, @PathVariable UUID id) {
        return decisionService.findById(tenantId(auth), id)
                .map(d -> ResponseEntity.ok(toDecisionMap(d)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/decisions/{id}/submit")
    @RequireCapability("EXECUTIVE_DECISIONS.WRITE")
    public ResponseEntity<Map<String, Object>> submitDecision(
            Authentication auth, @PathVariable UUID id) {
        return ResponseEntity.ok(toDecisionMap(
                decisionService.submit(tenantId(auth), id, userId(auth))));
    }

    @PostMapping("/decisions/{id}/review")
    @RequireCapability("EXECUTIVE_DECISIONS.WRITE")
    public ResponseEntity<Map<String, Object>> startReview(
            Authentication auth, @PathVariable UUID id) {
        return ResponseEntity.ok(toDecisionMap(
                decisionService.startReview(tenantId(auth), id, userId(auth))));
    }

    @PostMapping("/decisions/{id}/approve")
    @RequireCapability("EXECUTIVE_DECISIONS.APPROVE")
    public ResponseEntity<Map<String, Object>> approveDecision(
            Authentication auth, @PathVariable UUID id) {
        return ResponseEntity.ok(toDecisionMap(
                decisionService.approve(tenantId(auth), id, userId(auth))));
    }

    @PostMapping("/decisions/{id}/reject")
    @RequireCapability("EXECUTIVE_DECISIONS.APPROVE")
    public ResponseEntity<Map<String, Object>> rejectDecision(
            Authentication auth, @PathVariable UUID id) {
        return ResponseEntity.ok(toDecisionMap(
                decisionService.reject(tenantId(auth), id, userId(auth))));
    }

    @PostMapping("/decisions/{id}/execute")
    @RequireCapability("EXECUTIVE_DECISIONS.WRITE")
    public ResponseEntity<Map<String, Object>> executeDecision(
            Authentication auth, @PathVariable UUID id) {
        return ResponseEntity.ok(toDecisionMap(
                decisionService.startExecuting(tenantId(auth), id, userId(auth))));
    }

    @PostMapping("/decisions/{id}/complete")
    @RequireCapability("EXECUTIVE_DECISIONS.WRITE")
    public ResponseEntity<Map<String, Object>> completeDecision(
            Authentication auth, @PathVariable UUID id,
            @RequestBody Map<String, String> body) {
        var actualOutcome = body.getOrDefault("actualOutcome", "");
        return ResponseEntity.ok(toDecisionMap(
                decisionService.complete(tenantId(auth), id, actualOutcome, userId(auth))));
    }

    @PostMapping("/decisions/{id}/cancel")
    @RequireCapability("EXECUTIVE_DECISIONS.ADMIN")
    public ResponseEntity<Map<String, Object>> cancelDecision(
            Authentication auth, @PathVariable UUID id) {
        return ResponseEntity.ok(toDecisionMap(
                decisionService.cancel(tenantId(auth), id, userId(auth))));
    }

    // ===== Risks =====

    @PostMapping("/risks")
    @RequireCapability("RISK.WRITE")
    public ResponseEntity<Map<String, Object>> createRisk(
            Authentication auth, @Valid @RequestBody RiskRequest req) {
        var r = Risk.create(
                tenantId(auth), req.code(), req.title(), req.description(),
                req.category(), req.probability(), req.impact(),
                req.ownerUserId(), userId(auth), req.dueDate()
        );
        var saved = riskService.create(r, userId(auth));
        return ResponseEntity.ok(toRiskMap(saved));
    }

    @GetMapping("/risks")
    @RequireCapability("RISK.VIEW")
    public ResponseEntity<List<Map<String, Object>>> listRisks(
            Authentication auth, @RequestParam(defaultValue = "50") int limit) {
        var risks = riskService.findByTenant(tenantId(auth), limit);
        return ResponseEntity.ok(risks.stream().map(this::toRiskMap).toList());
    }

    @GetMapping("/risks/{id}")
    @RequireCapability("RISK.VIEW")
    public ResponseEntity<Map<String, Object>> getRisk(
            Authentication auth, @PathVariable UUID id) {
        return riskService.findById(tenantId(auth), id)
                .map(r -> ResponseEntity.ok(toRiskMap(r)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/risks/{id}/reassess")
    @RequireCapability("RISK.WRITE")
    public ResponseEntity<Map<String, Object>> reassessRisk(
            Authentication auth, @PathVariable UUID id,
            @RequestBody Map<String, Integer> body) {
        int prob = body.getOrDefault("probability", 3);
        int impact = body.getOrDefault("impact", 3);
        return ResponseEntity.ok(toRiskMap(
                riskService.reassess(tenantId(auth), id, prob, impact, userId(auth))));
    }

    @PostMapping("/risks/{id}/mitigate")
    @RequireCapability("RISK.WRITE")
    public ResponseEntity<Map<String, Object>> mitigateRisk(
            Authentication auth, @PathVariable UUID id,
            @RequestBody MitigationRequest req) {
        return ResponseEntity.ok(toRiskMap(riskService.startMitigation(
                tenantId(auth), id, req.mitigation(), req.contingency(),
                req.treatmentStrategy(), userId(auth))));
    }

    @PostMapping("/risks/{id}/monitor")
    @RequireCapability("RISK.WRITE")
    public ResponseEntity<Map<String, Object>> monitorRisk(
            Authentication auth, @PathVariable UUID id) {
        return ResponseEntity.ok(toRiskMap(
                riskService.monitor(tenantId(auth), id, userId(auth))));
    }

    @PostMapping("/risks/{id}/accept")
    @RequireCapability("RISK.WRITE")
    public ResponseEntity<Map<String, Object>> acceptRisk(
            Authentication auth, @PathVariable UUID id,
            @RequestBody Map<String, String> body) {
        var residual = body.getOrDefault("residualRisk", "");
        return ResponseEntity.ok(toRiskMap(
                riskService.accept(tenantId(auth), id, residual, userId(auth))));
    }

    @PostMapping("/risks/{id}/close")
    @RequireCapability("RISK.WRITE")
    public ResponseEntity<Map<String, Object>> closeRisk(
            Authentication auth, @PathVariable UUID id) {
        return ResponseEntity.ok(toRiskMap(
                riskService.close(tenantId(auth), id, userId(auth))));
    }

    // ===== Issues =====

    @PostMapping("/issues")
    @RequireCapability("ISSUE.WRITE")
    public ResponseEntity<Map<String, Object>> createIssue(
            Authentication auth, @Valid @RequestBody IssueRequest req) {
        var i = Issue.create(
                tenantId(auth), req.code(), req.title(), req.description(),
                Issue.Severity.valueOf(req.severity()),
                Issue.Priority.valueOf(req.priority()),
                req.source(), req.impact(),
                req.ownerUserId(), userId(auth), req.dueDate()
        );
        var saved = issueService.create(i, userId(auth));
        return ResponseEntity.ok(toIssueMap(saved));
    }

    @GetMapping("/issues")
    @RequireCapability("ISSUE.VIEW")
    public ResponseEntity<List<Map<String, Object>>> listIssues(
            Authentication auth, @RequestParam(defaultValue = "50") int limit) {
        var issues = issueService.findByTenant(tenantId(auth), limit);
        return ResponseEntity.ok(issues.stream().map(this::toIssueMap).toList());
    }

    @GetMapping("/issues/{id}")
    @RequireCapability("ISSUE.VIEW")
    public ResponseEntity<Map<String, Object>> getIssue(
            Authentication auth, @PathVariable UUID id) {
        return issueService.findById(tenantId(auth), id)
                .map(i -> ResponseEntity.ok(toIssueMap(i)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/issues/{id}/triage")
    @RequireCapability("ISSUE.WRITE")
    public ResponseEntity<Map<String, Object>> triageIssue(
            Authentication auth, @PathVariable UUID id) {
        return ResponseEntity.ok(toIssueMap(
                issueService.triage(tenantId(auth), id, userId(auth))));
    }

    @PostMapping("/issues/{id}/start")
    @RequireCapability("ISSUE.WRITE")
    public ResponseEntity<Map<String, Object>> startIssue(
            Authentication auth, @PathVariable UUID id) {
        return ResponseEntity.ok(toIssueMap(
                issueService.startProgress(tenantId(auth), id, userId(auth))));
    }

    @PostMapping("/issues/{id}/resolve")
    @RequireCapability("ISSUE.WRITE")
    public ResponseEntity<Map<String, Object>> resolveIssue(
            Authentication auth, @PathVariable UUID id,
            @RequestBody Map<String, String> body) {
        var resolution = body.getOrDefault("resolution", "");
        return ResponseEntity.ok(toIssueMap(
                issueService.resolve(tenantId(auth), id, resolution, userId(auth))));
    }

    @PostMapping("/issues/{id}/close")
    @RequireCapability("ISSUE.WRITE")
    public ResponseEntity<Map<String, Object>> closeIssue(
            Authentication auth, @PathVariable UUID id) {
        return ResponseEntity.ok(toIssueMap(
                issueService.close(tenantId(auth), id, userId(auth))));
    }

    // ===== Escalations =====

    @GetMapping("/escalations")
    @RequireCapability("ESCALATION.VIEW")
    public ResponseEntity<List<Map<String, Object>>> listEscalations(
            Authentication auth, @RequestParam(defaultValue = "50") int limit) {
        var escalations = escalationService.findByTenant(tenantId(auth), limit);
        return ResponseEntity.ok(escalations.stream().map(this::toEscalationMap).toList());
    }

    @GetMapping("/escalations/{id}")
    @RequireCapability("ESCALATION.VIEW")
    public ResponseEntity<Map<String, Object>> getEscalation(
            Authentication auth, @PathVariable UUID id) {
        return escalationService.findById(tenantId(auth), id)
                .map(e -> ResponseEntity.ok(toEscalationMap(e)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/escalations/{id}/acknowledge")
    @RequireCapability("ESCALATION.WRITE")
    public ResponseEntity<Map<String, Object>> acknowledgeEscalation(
            Authentication auth, @PathVariable UUID id) {
        return ResponseEntity.ok(toEscalationMap(
                escalationService.acknowledge(tenantId(auth), id, userId(auth))));
    }

    @PostMapping("/escalations/{id}/resolve")
    @RequireCapability("ESCALATION.WRITE")
    public ResponseEntity<Map<String, Object>> resolveEscalation(
            Authentication auth, @PathVariable UUID id,
            @RequestBody Map<String, String> body) {
        var resolution = body.getOrDefault("resolution", "");
        return ResponseEntity.ok(toEscalationMap(
                escalationService.resolve(tenantId(auth), id, resolution, userId(auth))));
    }

    @PostMapping("/escalations/{id}/cancel")
    @RequireCapability("ESCALATION.ADMIN")
    public ResponseEntity<Map<String, Object>> cancelEscalation(
            Authentication auth, @PathVariable UUID id) {
        return ResponseEntity.ok(toEscalationMap(
                escalationService.cancel(tenantId(auth), id, userId(auth))));
    }

    // ===== Request DTOs (inner records for brevity) =====

    public record DecisionRequest(
            String decisionNumber, String title, String description, String rationale,
            String category, String priority, String impact, String expectedOutcome,
            UUID ownerUserId, LocalDate dueDate
    ) {}

    public record RiskRequest(
            String code, String title, String description, String category,
            int probability, int impact, UUID ownerUserId, LocalDate dueDate
    ) {}

    public record MitigationRequest(
            String mitigation, String contingency, String treatmentStrategy
    ) {}

    public record IssueRequest(
            String code, String title, String description, String severity, String priority,
            String source, String impact, UUID ownerUserId, LocalDate dueDate
    ) {}

    // ===== Response helpers (using Map for simplicity) =====

    private Map<String, Object> toDecisionMap(ExecutiveDecision d) {
        return Map.of(
                "id", d.id(),
                "decisionNumber", d.decisionNumber(),
                "title", d.title(),
                "status", d.status().name(),
                "priority", d.priority().name(),
                "category", d.category() != null ? d.category() : "",
                "createdBy", d.createdBy(),
                "decidedBy", d.decidedBy() != null ? d.decidedBy() : "",
                "version", d.version()
        );
    }

    private Map<String, Object> toRiskMap(Risk r) {
        return Map.of(
                "id", r.id(),
                "code", r.code(),
                "title", r.title(),
                "status", r.status().name(),
                "severity", r.severity().name(),
                "riskScore", r.riskScore(),
                "probability", r.probability(),
                "impact", r.impact(),
                "version", r.version()
        );
    }

    private Map<String, Object> toIssueMap(Issue i) {
        return Map.of(
                "id", i.id(),
                "code", i.code(),
                "title", i.title(),
                "status", i.status().name(),
                "severity", i.severity().name(),
                "priority", i.priority().name(),
                "version", i.version()
        );
    }

    private Map<String, Object> toEscalationMap(Escalation e) {
        return Map.of(
                "id", e.id(),
                "code", e.code(),
                "sourceEntityType", e.sourceEntityType().name(),
                "sourceEntityId", e.sourceEntityId(),
                "reason", e.reason(),
                "status", e.status().name(),
                "severity", e.severity().name(),
                "escalationLevel", e.escalationLevel(),
                "version", e.version()
        );
    }
}
