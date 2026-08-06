package com.sanad.platform.crm.cases.web;

import com.sanad.platform.crm.cases.application.CaseUseCases;
import com.sanad.platform.crm.cases.domain.CaseRepository.CreateCaseCommand;
import com.sanad.platform.crm.cases.domain.CaseRepository.CaseRecord;
import com.sanad.platform.crm.cases.domain.CaseRepository.UpdateCaseCommand;
import com.sanad.platform.crm.cases.web.CaseModels.AssignRequest;
import com.sanad.platform.crm.cases.web.CaseModels.CreateCaseRequest;
import com.sanad.platform.crm.cases.web.CaseModels.ResolveRequest;
import com.sanad.platform.crm.cases.web.CaseModels.UpdateCaseRequest;
import com.sanad.platform.crm.pagination.CrmEnvelopes;
import com.sanad.platform.security.authorization.RequireCapability;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * V2 REST controller for CRM Cases/Tickets.
 * <p>
 * Mounted under {@code /api/v2/crm/cases}.
 * Returns {@link CrmEnvelopes.SingleResponse} / {@link CrmEnvelopes.ListResponse}.
 * <p>
 * Capabilities enforced via {@link RequireCapability}:
 *   - {@code CRM.CASE.READ} for GET endpoints
 *   - {@code CRM.CASE.WRITE} for POST/PUT endpoints
 */
@RestController
@RequestMapping("/api/v2/crm/cases")
public class CaseController {

    private final CaseUseCases cases;

    public CaseController(CaseUseCases cases) {
        this.cases = cases;
    }

    @RequireCapability("CRM.CASE.READ")
    @GetMapping
    public CrmEnvelopes.ListResponse<Map<String, Object>> listCases(
            Authentication authentication,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID assigneeUserId,
            @RequestParam(required = false) UUID customerId) {
        UUID tenantId = tenantId(authentication);
        int safeLimit = Math.max(1, Math.min(limit, 200));
        List<CaseRecord> rows = cases.list(tenantId, safeLimit, status, assigneeUserId, customerId);
        UUID requestId = UUID.randomUUID();
        return CrmEnvelopes.ListResponse.of(
                rows.stream().map(this::toRow).toList(),
                CrmEnvelopes.Page.empty(safeLimit),
                requestId);
    }

    @RequireCapability("CRM.CASE.READ")
    @GetMapping("/{caseId}")
    public CrmEnvelopes.SingleResponse<Map<String, Object>> getCase(
            Authentication authentication,
            @PathVariable UUID caseId) {
        UUID tenantId = tenantId(authentication);
        CaseRecord record = cases.getById(tenantId, caseId);
        UUID requestId = UUID.randomUUID();
        return CrmEnvelopes.SingleResponse.of(toRow(record), requestId);
    }

    @RequireCapability("CRM.CASE.WRITE")
    @PostMapping
    public ResponseEntity<CrmEnvelopes.SingleResponse<Map<String, Object>>> createCase(
            Authentication authentication,
            @Valid @RequestBody CreateCaseRequest request) {
        UUID tenantId = tenantId(authentication);
        UUID actorId = userId(authentication);

        CreateCaseCommand cmd = new CreateCaseCommand(
                request.subject(),
                request.description(),
                request.caseType(),
                request.priority() != null ? request.priority() : 50,
                request.customerId(),
                request.assigneeUserId(),
                request.relatedId(),
                request.dueAt());

        CaseRecord created = cases.create(tenantId, actorId, cmd);
        UUID requestId = UUID.randomUUID();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(CrmEnvelopes.SingleResponse.of(toRow(created), requestId));
    }

    @RequireCapability("CRM.CASE.WRITE")
    @PutMapping("/{caseId}")
    public CrmEnvelopes.SingleResponse<Map<String, Object>> updateCase(
            Authentication authentication,
            @PathVariable UUID caseId,
            @Valid @RequestBody UpdateCaseRequest request) {
        UUID tenantId = tenantId(authentication);
        UUID actorId = userId(authentication);
        CaseRecord current = cases.getById(tenantId, caseId);

        UpdateCaseCommand cmd = new UpdateCaseCommand(
                request.subject(),
                request.description(),
                request.caseType(),
                request.priority(),
                request.customerId(),
                request.dueAt());

        CaseRecord updated = cases.update(tenantId, actorId, caseId, cmd, current.version());
        UUID requestId = UUID.randomUUID();
        return CrmEnvelopes.SingleResponse.of(toRow(updated), requestId);
    }

    @RequireCapability("CRM.CASE.WRITE")
    @PostMapping("/{caseId}/start")
    public CrmEnvelopes.SingleResponse<Map<String, Object>> startCase(
            Authentication authentication,
            @PathVariable UUID caseId) {
        UUID tenantId = tenantId(authentication);
        UUID actorId = userId(authentication);
        CaseRecord current = cases.getById(tenantId, caseId);
        CaseRecord started = cases.start(tenantId, actorId, caseId, current.version());
        UUID requestId = UUID.randomUUID();
        return CrmEnvelopes.SingleResponse.of(toRow(started), requestId);
    }

    @RequireCapability("CRM.CASE.WRITE")
    @PostMapping("/{caseId}/resolve")
    public CrmEnvelopes.SingleResponse<Map<String, Object>> resolveCase(
            Authentication authentication,
            @PathVariable UUID caseId,
            @RequestBody(required = false) ResolveRequest request) {
        UUID tenantId = tenantId(authentication);
        UUID actorId = userId(authentication);
        CaseRecord current = cases.getById(tenantId, caseId);
        String resolution = request == null ? null : request.resolution();
        CaseRecord resolved = cases.resolve(tenantId, actorId, caseId, resolution, current.version());
        UUID requestId = UUID.randomUUID();
        return CrmEnvelopes.SingleResponse.of(toRow(resolved), requestId);
    }

    @RequireCapability("CRM.CASE.WRITE")
    @PostMapping("/{caseId}/close")
    public CrmEnvelopes.SingleResponse<Map<String, Object>> closeCase(
            Authentication authentication,
            @PathVariable UUID caseId) {
        UUID tenantId = tenantId(authentication);
        UUID actorId = userId(authentication);
        CaseRecord current = cases.getById(tenantId, caseId);
        CaseRecord closed = cases.close(tenantId, actorId, caseId, current.version());
        UUID requestId = UUID.randomUUID();
        return CrmEnvelopes.SingleResponse.of(toRow(closed), requestId);
    }

    @RequireCapability("CRM.CASE.WRITE")
    @PostMapping("/{caseId}/reopen")
    public CrmEnvelopes.SingleResponse<Map<String, Object>> reopenCase(
            Authentication authentication,
            @PathVariable UUID caseId) {
        UUID tenantId = tenantId(authentication);
        UUID actorId = userId(authentication);
        CaseRecord current = cases.getById(tenantId, caseId);
        CaseRecord reopened = cases.reopen(tenantId, actorId, caseId, current.version());
        UUID requestId = UUID.randomUUID();
        return CrmEnvelopes.SingleResponse.of(toRow(reopened), requestId);
    }

    @RequireCapability("CRM.CASE.WRITE")
    @PostMapping("/{caseId}/assign")
    public CrmEnvelopes.SingleResponse<Map<String, Object>> assignCase(
            Authentication authentication,
            @PathVariable UUID caseId,
            @Valid @RequestBody AssignRequest request) {
        UUID tenantId = tenantId(authentication);
        UUID actorId = userId(authentication);
        CaseRecord current = cases.getById(tenantId, caseId);
        CaseRecord assigned = cases.assign(tenantId, actorId, caseId, request.assigneeUserId(), current.version());
        UUID requestId = UUID.randomUUID();
        return CrmEnvelopes.SingleResponse.of(toRow(assigned), requestId);
    }

    private Map<String, Object> toRow(CaseRecord r) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", r.id());
        row.put("version", r.version());
        row.put("subject", r.subject());
        row.put("description", r.description());
        row.put("case_type", r.caseType());
        row.put("status", r.status());
        row.put("priority", r.priority());
        row.put("customer_id", r.customerId());
        row.put("assignee_user_id", r.assigneeUserId());
        row.put("owner_user_id", r.ownerUserId());
        row.put("related_id", r.relatedId());
        row.put("due_at", toIso(r.dueAt()));
        row.put("resolved_at", toIso(r.resolvedAt()));
        row.put("closed_at", toIso(r.closedAt()));
        row.put("created_at", toIsoInstant(r.createdAt()));
        row.put("updated_at", toIsoInstant(r.updatedAt()));
        return row;
    }

    private static String toIso(OffsetDateTime v) {
        return v == null ? null : v.toInstant().toString();
    }

    private static String toIsoInstant(Instant v) {
        return v == null ? null : v.toString();
    }

    private static UUID tenantId(Authentication authentication) {
        return context(authentication, "tenant_id");
    }

    private static UUID userId(Authentication authentication) {
        return context(authentication, "user_id");
    }

    private static UUID context(Authentication authentication, String key) {
        if (authentication == null
                || !authentication.isAuthenticated()
                || !(authentication.getDetails() instanceof Map<?, ?> details)
                || details.get(key) == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated CRM context is required");
        }
        try {
            return UUID.fromString(details.get(key).toString());
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid authenticated CRM context", exception);
        }
    }
}
