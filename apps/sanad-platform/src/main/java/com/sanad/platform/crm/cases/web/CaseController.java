package com.sanad.platform.crm.cases.web;

import com.sanad.platform.crm.cases.application.CaseUseCases;
import com.sanad.platform.crm.cases.domain.CaseRepository.CreateCaseCommand;
import com.sanad.platform.crm.cases.domain.CaseRepository.CaseRecord;
import com.sanad.platform.crm.cases.domain.CaseRepository.UpdateCaseCommand;
import com.sanad.platform.crm.cases.web.CaseModels.AssignRequest;
import com.sanad.platform.crm.cases.web.CaseModels.CreateCaseRequest;
import com.sanad.platform.crm.cases.web.CaseModels.ResolveRequest;
import com.sanad.platform.crm.cases.web.CaseModels.UpdateCaseRequest;
import com.sanad.platform.crm.dto.CrmDtos.CaseResponse;
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
    public CrmEnvelopes.ListResponse<CaseResponse> listCases(
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
                rows.stream().map(this::toResponse).toList(),
                CrmEnvelopes.Page.empty(safeLimit),
                requestId);
    }

    @RequireCapability("CRM.CASE.READ")
    @GetMapping("/{caseId}")
    public CrmEnvelopes.SingleResponse<CaseResponse> getCase(
            Authentication authentication,
            @PathVariable UUID caseId) {
        UUID tenantId = tenantId(authentication);
        CaseRecord record = cases.getById(tenantId, caseId);
        UUID requestId = UUID.randomUUID();
        return CrmEnvelopes.SingleResponse.of(toResponse(record), requestId);
    }

    @RequireCapability("CRM.CASE.WRITE")
    @PostMapping
    public ResponseEntity<CrmEnvelopes.SingleResponse<CaseResponse>> createCase(
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
                .body(CrmEnvelopes.SingleResponse.of(toResponse(created), requestId));
    }

    @RequireCapability("CRM.CASE.WRITE")
    @PutMapping("/{caseId}")
    public CrmEnvelopes.SingleResponse<CaseResponse> updateCase(
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
        return CrmEnvelopes.SingleResponse.of(toResponse(updated), requestId);
    }

    @RequireCapability("CRM.CASE.WRITE")
    @PostMapping("/{caseId}/start")
    public CrmEnvelopes.SingleResponse<CaseResponse> startCase(
            Authentication authentication,
            @PathVariable UUID caseId) {
        UUID tenantId = tenantId(authentication);
        UUID actorId = userId(authentication);
        CaseRecord current = cases.getById(tenantId, caseId);
        CaseRecord started = cases.start(tenantId, actorId, caseId, current.version());
        UUID requestId = UUID.randomUUID();
        return CrmEnvelopes.SingleResponse.of(toResponse(started), requestId);
    }

    @RequireCapability("CRM.CASE.WRITE")
    @PostMapping("/{caseId}/resolve")
    public CrmEnvelopes.SingleResponse<CaseResponse> resolveCase(
            Authentication authentication,
            @PathVariable UUID caseId,
            @RequestBody(required = false) ResolveRequest request) {
        UUID tenantId = tenantId(authentication);
        UUID actorId = userId(authentication);
        CaseRecord current = cases.getById(tenantId, caseId);
        String resolution = request == null ? null : request.resolution();
        CaseRecord resolved = cases.resolve(tenantId, actorId, caseId, resolution, current.version());
        UUID requestId = UUID.randomUUID();
        return CrmEnvelopes.SingleResponse.of(toResponse(resolved), requestId);
    }

    @RequireCapability("CRM.CASE.WRITE")
    @PostMapping("/{caseId}/close")
    public CrmEnvelopes.SingleResponse<CaseResponse> closeCase(
            Authentication authentication,
            @PathVariable UUID caseId) {
        UUID tenantId = tenantId(authentication);
        UUID actorId = userId(authentication);
        CaseRecord current = cases.getById(tenantId, caseId);
        CaseRecord closed = cases.close(tenantId, actorId, caseId, current.version());
        UUID requestId = UUID.randomUUID();
        return CrmEnvelopes.SingleResponse.of(toResponse(closed), requestId);
    }

    @RequireCapability("CRM.CASE.WRITE")
    @PostMapping("/{caseId}/reopen")
    public CrmEnvelopes.SingleResponse<CaseResponse> reopenCase(
            Authentication authentication,
            @PathVariable UUID caseId) {
        UUID tenantId = tenantId(authentication);
        UUID actorId = userId(authentication);
        CaseRecord current = cases.getById(tenantId, caseId);
        CaseRecord reopened = cases.reopen(tenantId, actorId, caseId, current.version());
        UUID requestId = UUID.randomUUID();
        return CrmEnvelopes.SingleResponse.of(toResponse(reopened), requestId);
    }

    @RequireCapability("CRM.CASE.WRITE")
    @PostMapping("/{caseId}/assign")
    public CrmEnvelopes.SingleResponse<CaseResponse> assignCase(
            Authentication authentication,
            @PathVariable UUID caseId,
            @Valid @RequestBody AssignRequest request) {
        UUID tenantId = tenantId(authentication);
        UUID actorId = userId(authentication);
        CaseRecord current = cases.getById(tenantId, caseId);
        CaseRecord assigned = cases.assign(tenantId, actorId, caseId, request.assigneeUserId(), current.version());
        UUID requestId = UUID.randomUUID();
        return CrmEnvelopes.SingleResponse.of(toResponse(assigned), requestId);
    }

    private CaseResponse toResponse(CaseRecord r) {
        return new CaseResponse(
                r.id(),
                r.version(),
                r.subject(),
                r.description(),
                r.caseType(),
                r.status(),
                r.priority(),
                r.customerId(),
                r.assigneeUserId(),
                r.ownerUserId(),
                r.relatedId(),
                r.dueAt(),
                r.resolvedAt(),
                r.closedAt(),
                toOffsetDateTime(r.createdAt()),
                toOffsetDateTime(r.updatedAt()));
    }

    private static OffsetDateTime toOffsetDateTime(Instant v) {
        return v == null ? null : OffsetDateTime.ofInstant(v, java.time.ZoneOffset.UTC);
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
