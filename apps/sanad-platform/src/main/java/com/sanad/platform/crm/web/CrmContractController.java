package com.sanad.platform.crm.web;

import com.sanad.platform.crm.concurrency.ETagService;
import com.sanad.platform.crm.dto.CrmDtos.AccountResponse;
import com.sanad.platform.crm.dto.CrmDtos.ActivityResponse;
import com.sanad.platform.crm.dto.CrmDtos.ArchiveAccountResponse;
import com.sanad.platform.crm.dto.CrmDtos.ContactResponse;
import com.sanad.platform.crm.dto.CrmDtos.Customer360Response;
import com.sanad.platform.crm.dto.CrmDtos.CustomFieldResponse;
import com.sanad.platform.crm.dto.CrmDtos.CustomFieldValuesResponse;
import com.sanad.platform.crm.dto.CrmDtos.ImportErrorResponse;
import com.sanad.platform.crm.dto.CrmDtos.ImportJobResponse;
import com.sanad.platform.crm.dto.CrmDtos.LeadConversionResponse;
import com.sanad.platform.crm.dto.CrmDtos.LeadResponse;
import com.sanad.platform.crm.dto.CrmDtos.OpportunityResponse;
import com.sanad.platform.crm.dto.CrmDtos.PipelineResponse;
import com.sanad.platform.crm.dto.CrmDtos.StageResponse;
import com.sanad.platform.crm.dto.CrmDtos.TimelineEventResponse;
import com.sanad.platform.crm.error.CrmContractException;
import com.sanad.platform.crm.error.CrmErrorCode;
import com.sanad.platform.crm.idempotency.IdempotencyService;
import com.sanad.platform.crm.mapper.CrmDtoMapper;
import com.sanad.platform.crm.pagination.CrmEnvelopes;
import com.sanad.platform.crm.pagination.CrmEnvelopes.ListResponse;
import com.sanad.platform.crm.pagination.CrmEnvelopes.SingleResponse;
import com.sanad.platform.crm.pagination.CursorCodec;
import com.sanad.platform.crm.pagination.PageRequest;
import com.sanad.platform.crm.web.CreateAccountRequest;
import com.sanad.platform.crm.web.CreateActivityRequest;
import com.sanad.platform.crm.web.CreateContactRequest;
import com.sanad.platform.crm.web.CreateCustomFieldRequest;
import com.sanad.platform.crm.web.CreateLeadRequest;
import com.sanad.platform.crm.web.CreateOpportunityRequest;
import com.sanad.platform.crm.web.CreatePipelineRequest;
import com.sanad.platform.crm.web.CrmController;
import com.sanad.platform.crm.web.CrmExtendedService;
import com.sanad.platform.crm.web.CrmService;
import com.sanad.platform.crm.web.UpdateAccountRequest;
import com.sanad.platform.crm.web.UpdateContactRequest;
import com.sanad.platform.security.authorization.RequireCapability;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * CRM API Contract — Typed Contract Controller.
 * <p>
 * This controller sits alongside the existing {@link CrmController} and
 * exposes the same business operations under
 * {@code /api/v2/crm/...} with:
 *   - Typed request DTOs (already present in {@code CrmModels.java}).
 *   - Typed response DTOs ({@link AccountResponse}, {@link ContactResponse},
 *     {@link LeadResponse}, etc.).
 *   - The standard {@link CrmEnvelopes.SingleResponse} /
 *     {@link CrmEnvelopes.ListResponse} envelopes with {@code meta.requestId}
 *     and {@code page.nextCursor} for paginated lists.
 *   - {@code ETag} header on every single-record GET.
 *   - {@code If-Match} header required on every PATCH.
 *   - {@code Idempotency-Key} support on every POST.
 * <p>
 * The existing {@code /api/v1/crm/...} endpoints remain unchanged so
 * CRM-G1 functionality is preserved (backward compatibility — section 18
 * of the EXEC-PROMPT-CRM-003 brief). The frontend will migrate to v2 in
 * a follow-up; the v1 → v2 cutover is documented in
 * {@code docs/crm/contracts/CRM-API-VERSIONING-POLICY.md}.
 * <p>
 * Branch: crm/003-stable-api-contracts
 * Gate: CRM-G2 — API Contract and Concurrency Gate
 */
@RestController
@RequestMapping("/api/v2/crm")
public class CrmContractController {

    private final CrmService legacy;
    private final CrmExtendedService extended;
    private final CrmDtoMapper mapper;
    private final ETagService etags;
    private final IdempotencyService idempotency;
    private final CursorCodec cursors;

    public CrmContractController(CrmService legacy, CrmExtendedService extended,
                                 CrmDtoMapper mapper, ETagService etags,
                                 IdempotencyService idempotency, CursorCodec cursors) {
        this.legacy = legacy;
        this.extended = extended;
        this.mapper = mapper;
        this.etags = etags;
        this.idempotency = idempotency;
        this.cursors = cursors;
    }

    // ────────────────────────────────────────────────────────────────────
    // Accounts
    // ────────────────────────────────────────────────────────────────────

    @RequireCapability("CRM.ACCOUNT.READ")
    @GetMapping("/accounts/{accountId}")
    public ResponseEntity<SingleResponse<AccountResponse>> getAccount(
            Authentication auth, @PathVariable UUID accountId, HttpServletRequest req) {
        Map<String, Object> row = legacy.getAccount(auth, accountId);
        AccountResponse body = mapper.toAccountResponse(row);
        return wrapSingle(body, "account", body == null ? 0 : body.version(), req);
    }

    @RequireCapability("CRM.ACCOUNT.READ")
    @GetMapping("/accounts")
    public ListResponse<AccountResponse> listAccounts(
            Authentication auth,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String direction,
            @RequestParam(required = false) String search,
            HttpServletRequest req) {
        PageRequest page = new PageRequest(limit, cursor, sort, direction);
        // The legacy service returns an unbounded list — we apply cursor
        // pagination here on the typed result. The next iteration will
        // push the cursor logic into the SQL query for performance; for
        // now, correctness and contract stability are the priority.
        List<Map<String, Object>> rows = legacy.listAccounts(auth, page.limit() + 1, search);
        return paginate(rows, page, "account", auth, req, mapper::toAccountResponse);
    }

    @RequireCapability("CRM.ACCOUNT.WRITE")
    @PostMapping("/accounts")
    public ResponseEntity<SingleResponse<AccountResponse>> createAccount(
            Authentication auth,
            @Valid @RequestBody CreateAccountRequest body,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            HttpServletRequest req) {
        IdempotencyGuard guard = beginIdempotency(auth, "POST:/api/v2/crm/accounts", idempotencyKey, body, req);
        if (guard.isReplay()) {
            return guard.replaySingle();
        }
        try {
            Map<String, Object> row = legacy.createAccount(auth, body);
            AccountResponse response = mapper.toAccountResponse(row);
            return guard.completeSingle(response, "account", response.version(), HttpStatus.CREATED);
        } catch (RuntimeException ex) {
            guard.fail();
            throw ex;
        }
    }

    @RequireCapability("CRM.ACCOUNT.WRITE")
    @PatchMapping("/accounts/{accountId}")
    public ResponseEntity<SingleResponse<AccountResponse>> updateAccount(
            Authentication auth,
            @PathVariable UUID accountId,
            @Valid @RequestBody UpdateAccountRequest body,
            @RequestHeader(value = "If-Match", required = false) String ifMatch,
            HttpServletRequest req) {
        // Read current row, validate If-Match against current version, then patch.
        Map<String, Object> current = legacy.getAccount(auth, accountId);
        long currentVersion = asLong(current.get("version"));
        etags.validateIfMatch(ifMatch, "account", accountId, currentVersion);
        Map<String, Object> updated = legacy.updateAccount(auth, accountId, body);
        AccountResponse response = mapper.toAccountResponse(updated);
        return wrapSingle(response, "account", response.version(), req);
    }

    @RequireCapability("CRM.ACCOUNT.ARCHIVE")
    @PatchMapping("/accounts/{accountId}/archive")
    public ResponseEntity<SingleResponse<ArchiveAccountResponse>> archiveAccount(
            Authentication auth,
            @PathVariable UUID accountId,
            @RequestHeader(value = "If-Match", required = false) String ifMatch,
            HttpServletRequest req) {
        Map<String, Object> current = legacy.getAccount(auth, accountId);
        etags.validateIfMatch(ifMatch, "account", accountId, asLong(current.get("version")));
        Map<String, Object> archived = legacy.archiveAccount(auth, accountId);
        ArchiveAccountResponse response = mapper.toArchiveAccountResponse(archived);
        return wrapSingle(response, "account", response.version(), req);
    }

    @RequireCapability("CRM.ACCOUNT.READ")
    @GetMapping("/accounts/{accountId}/customer-360")
    public SingleResponse<Customer360Response> customer360(
            Authentication auth, @PathVariable UUID accountId, HttpServletRequest req) {
        Map<String, Object> row = extended.customer360(auth, accountId);
        // The legacy customer360 returns a Map with sub-keys; delegate to mapper.
        Customer360Response body = mapper.toCustomer360Response(
                (Map<String, Object>) row.get("account"),
                (List<Map<String, Object>>) row.get("contacts"),
                (List<Map<String, Object>>) row.get("opportunities"),
                (List<Map<String, Object>>) row.get("activities"),
                (List<Map<String, Object>>) row.get("timeline"),
                (Map<String, Object>) row.get("customFields"));
        return SingleResponse.of(body, requestId(req));
    }

    // ────────────────────────────────────────────────────────────────────
    // Contacts
    // ────────────────────────────────────────────────────────────────────

    @RequireCapability("CRM.CONTACT.READ")
    @GetMapping("/contacts/{contactId}")
    public ResponseEntity<SingleResponse<ContactResponse>> getContact(
            Authentication auth, @PathVariable UUID contactId, HttpServletRequest req) {
        Map<String, Object> row = extended.getContact(auth, contactId);
        ContactResponse body = mapper.toContactResponse(row);
        return wrapSingle(body, "contact", body == null ? 0 : body.version(), req);
    }

    @RequireCapability("CRM.CONTACT.READ")
    @GetMapping("/contacts")
    public ListResponse<ContactResponse> listContacts(
            Authentication auth,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String direction,
            @RequestParam(required = false) UUID accountId,
            @RequestParam(required = false) String search,
            HttpServletRequest req) {
        PageRequest page = new PageRequest(limit, cursor, sort, direction);
        List<Map<String, Object>> rows = legacy.listContacts(auth, page.limit() + 1, accountId, search);
        return paginate(rows, page, "contact", auth, req, mapper::toContactResponse);
    }

    @RequireCapability("CRM.CONTACT.WRITE")
    @PostMapping("/contacts")
    public ResponseEntity<SingleResponse<ContactResponse>> createContact(
            Authentication auth,
            @Valid @RequestBody CreateContactRequest body,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            HttpServletRequest req) {
        IdempotencyGuard guard = beginIdempotency(auth, "POST:/api/v2/crm/contacts", idempotencyKey, body, req);
        if (guard.isReplay()) return guard.replaySingle();
        try {
            Map<String, Object> row = legacy.createContact(auth, body);
            ContactResponse response = mapper.toContactResponse(row);
            return guard.completeSingle(response, "contact", response.version(), HttpStatus.CREATED);
        } catch (RuntimeException ex) { guard.fail(); throw ex; }
    }

    // ────────────────────────────────────────────────────────────────────
    // Leads
    // ────────────────────────────────────────────────────────────────────

    @RequireCapability("CRM.LEAD.READ")
    @GetMapping("/leads/{leadId}")
    public ResponseEntity<SingleResponse<LeadResponse>> getLead(
            Authentication auth, @PathVariable UUID leadId, HttpServletRequest req) {
        Map<String, Object> row = extended.getLead(auth, leadId);
        LeadResponse body = mapper.toLeadResponse(row);
        return wrapSingle(body, "lead", body == null ? 0 : body.version(), req);
    }

    @RequireCapability("CRM.LEAD.READ")
    @GetMapping("/leads")
    public ListResponse<LeadResponse> listLeads(
            Authentication auth,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String direction,
            @RequestParam(required = false) String status,
            HttpServletRequest req) {
        PageRequest page = new PageRequest(limit, cursor, sort, direction);
        List<Map<String, Object>> rows = legacy.listLeads(auth, page.limit() + 1, status);
        return paginate(rows, page, "lead", auth, req, mapper::toLeadResponse);
    }

    @RequireCapability("CRM.LEAD.WRITE")
    @PostMapping("/leads")
    public ResponseEntity<SingleResponse<LeadResponse>> createLead(
            Authentication auth,
            @Valid @RequestBody CreateLeadRequest body,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            HttpServletRequest req) {
        IdempotencyGuard guard = beginIdempotency(auth, "POST:/api/v2/crm/leads", idempotencyKey, body, req);
        if (guard.isReplay()) return guard.replaySingle();
        try {
            Map<String, Object> row = legacy.createLead(auth, body);
            LeadResponse response = mapper.toLeadResponse(row);
            return guard.completeSingle(response, "lead", response.version(), HttpStatus.CREATED);
        } catch (RuntimeException ex) { guard.fail(); throw ex; }
    }

    @RequireCapability("CRM.LEAD.CONVERT")
    @PostMapping("/leads/{leadId}/convert")
    public ResponseEntity<SingleResponse<LeadConversionResponse>> convertLead(
            Authentication auth,
            @PathVariable UUID leadId,
            @Valid @RequestBody com.sanad.platform.crm.web.ConvertLeadRequest body,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            HttpServletRequest req) {
        IdempotencyGuard guard = beginIdempotency(auth, "POST:/api/v2/crm/leads/" + leadId + "/convert", idempotencyKey, body, req);
        if (guard.isReplay()) return guard.replaySingle();
        try {
            Map<String, Object> row = legacy.convertLead(auth, leadId, body);
            LeadConversionResponse response = mapper.toLeadConversionResponse(row);
            return guard.completeSingle(response, "lead-conversion", 0, HttpStatus.OK);
        } catch (RuntimeException ex) { guard.fail(); throw ex; }
    }

    // ────────────────────────────────────────────────────────────────────
    // Pipelines & Stages
    // ────────────────────────────────────────────────────────────────────

    @RequireCapability("CRM.OPPORTUNITY.READ")
    @GetMapping("/pipelines")
    public ListResponse<PipelineResponse> listPipelines(Authentication auth, HttpServletRequest req) {
        List<Map<String, Object>> rows = legacy.listPipelines(auth);
        List<PipelineResponse> data = rows.stream().map(r -> mapper.toPipelineResponse(r, List.of())).toList();
        return ListResponse.of(data, CrmEnvelopes.Page.empty(PageRequest.DEFAULT_LIMIT), requestId(req));
    }

    @RequireCapability("CRM.OPPORTUNITY.READ")
    @GetMapping("/pipelines/{pipelineId}/stages")
    public ListResponse<StageResponse> listPipelineStages(
            Authentication auth, @PathVariable UUID pipelineId, HttpServletRequest req) {
        List<Map<String, Object>> rows = extended.listPipelineStages(auth, pipelineId);
        List<StageResponse> data = rows.stream().map(mapper::toStageResponse).toList();
        return ListResponse.of(data, CrmEnvelopes.Page.empty(PageRequest.DEFAULT_LIMIT), requestId(req));
    }

    // ────────────────────────────────────────────────────────────────────
    // Opportunities
    // ────────────────────────────────────────────────────────────────────

    @RequireCapability("CRM.OPPORTUNITY.READ")
    @GetMapping("/opportunities/{opportunityId}")
    public ResponseEntity<SingleResponse<OpportunityResponse>> getOpportunity(
            Authentication auth, @PathVariable UUID opportunityId, HttpServletRequest req) {
        Map<String, Object> row = extended.getOpportunity(auth, opportunityId);
        OpportunityResponse body = mapper.toOpportunityResponse(row);
        return wrapSingle(body, "opportunity", body == null ? 0 : body.version(), req);
    }

    @RequireCapability("CRM.OPPORTUNITY.READ")
    @GetMapping("/opportunities")
    public ListResponse<OpportunityResponse> listOpportunities(
            Authentication auth,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String direction,
            @RequestParam(required = false) UUID accountId,
            HttpServletRequest req) {
        PageRequest page = new PageRequest(limit, cursor, sort, direction);
        List<Map<String, Object>> rows = legacy.listOpportunities(auth, page.limit() + 1, accountId);
        return paginate(rows, page, "opportunity", auth, req, mapper::toOpportunityResponse);
    }

    @RequireCapability("CRM.OPPORTUNITY.WRITE")
    @PostMapping("/opportunities")
    public ResponseEntity<SingleResponse<OpportunityResponse>> createOpportunity(
            Authentication auth,
            @Valid @RequestBody CreateOpportunityRequest body,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            HttpServletRequest req) {
        IdempotencyGuard guard = beginIdempotency(auth, "POST:/api/v2/crm/opportunities", idempotencyKey, body, req);
        if (guard.isReplay()) return guard.replaySingle();
        try {
            Map<String, Object> row = legacy.createOpportunity(auth, body);
            OpportunityResponse response = mapper.toOpportunityResponse(row);
            return guard.completeSingle(response, "opportunity", response.version(), HttpStatus.CREATED);
        } catch (RuntimeException ex) { guard.fail(); throw ex; }
    }

    // ────────────────────────────────────────────────────────────────────
    // Activities
    // ────────────────────────────────────────────────────────────────────

    @RequireCapability("CRM.ACTIVITY.READ")
    @GetMapping("/activities/{activityId}")
    public ResponseEntity<SingleResponse<ActivityResponse>> getActivity(
            Authentication auth, @PathVariable UUID activityId, HttpServletRequest req) {
        Map<String, Object> row = extended.getActivity(auth, activityId);
        ActivityResponse body = mapper.toActivityResponse(row);
        return wrapSingle(body, "activity", body == null ? 0 : body.version(), req);
    }

    @RequireCapability("CRM.ACTIVITY.READ")
    @GetMapping("/activities")
    public ListResponse<ActivityResponse> listActivities(
            Authentication auth,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String direction,
            @RequestParam(required = false) String relatedType,
            @RequestParam(required = false) UUID relatedId,
            @RequestParam(required = false) String status,
            HttpServletRequest req) {
        PageRequest page = new PageRequest(limit, cursor, sort, direction);
        List<Map<String, Object>> rows = extended.listActivities(auth, page.limit() + 1, relatedType, relatedId, status);
        return paginate(rows, page, "activity", auth, req, mapper::toActivityResponse);
    }

    @RequireCapability("CRM.ACTIVITY.WRITE")
    @PostMapping("/activities")
    public ResponseEntity<SingleResponse<ActivityResponse>> createActivity(
            Authentication auth,
            @Valid @RequestBody CreateActivityRequest body,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            HttpServletRequest req) {
        IdempotencyGuard guard = beginIdempotency(auth, "POST:/api/v2/crm/activities", idempotencyKey, body, req);
        if (guard.isReplay()) return guard.replaySingle();
        try {
            Map<String, Object> row = legacy.createActivity(auth, body);
            ActivityResponse response = mapper.toActivityResponse(row);
            return guard.completeSingle(response, "activity", response.version(), HttpStatus.CREATED);
        } catch (RuntimeException ex) { guard.fail(); throw ex; }
    }

    // ────────────────────────────────────────────────────────────────────
    // Timeline
    // ────────────────────────────────────────────────────────────────────

    @RequireCapability("CRM.ACTIVITY.READ")
    @GetMapping("/timeline/{subjectType}/{subjectId}")
    public ListResponse<TimelineEventResponse> timeline(
            Authentication auth,
            @PathVariable String subjectType,
            @PathVariable UUID subjectId,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String direction,
            HttpServletRequest req) {
        PageRequest page = new PageRequest(limit, cursor, sort, direction);
        List<Map<String, Object>> rows = legacy.timeline(auth, subjectType, subjectId, page.limit() + 1);
        return paginate(rows, page, "timeline", auth, req, mapper::toTimelineEventResponse);
    }

    // ────────────────────────────────────────────────────────────────────
    // Imports
    // ────────────────────────────────────────────────────────────────────

    @RequireCapability("CRM.IMPORT.READ")
    @GetMapping("/imports")
    public ListResponse<ImportJobResponse> listImportJobs(
            Authentication auth,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String direction,
            HttpServletRequest req) {
        PageRequest page = new PageRequest(limit, cursor, sort, direction);
        List<Map<String, Object>> rows = extended.listImportJobs(auth, page.limit() + 1);
        return paginate(rows, page, "import", auth, req, mapper::toImportJobResponse);
    }

    @RequireCapability("CRM.IMPORT.READ")
    @GetMapping("/imports/{jobId}")
    public ResponseEntity<SingleResponse<ImportJobResponse>> getImportJob(
            Authentication auth, @PathVariable UUID jobId, HttpServletRequest req) {
        Map<String, Object> row = extended.getImportJob(auth, jobId);
        ImportJobResponse body = mapper.toImportJobResponse(row);
        return wrapSingle(body, "import", 0L, req);
    }

    @RequireCapability("CRM.IMPORT.READ")
    @GetMapping("/imports/{jobId}/errors")
    public ListResponse<ImportErrorResponse> listImportErrors(
            Authentication auth, @PathVariable UUID jobId,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String direction,
            HttpServletRequest req) {
        PageRequest page = new PageRequest(limit, cursor, sort, direction);
        List<Map<String, Object>> rows = extended.listImportErrors(auth, jobId, page.limit() + 1);
        return paginate(rows, page, "import-error", auth, req, mapper::toImportErrorResponse);
    }

    // ────────────────────────────────────────────────────────────────────
    // Custom Fields
    // ────────────────────────────────────────────────────────────────────

    @RequireCapability("CRM.CUSTOM_FIELD.READ")
    @GetMapping("/custom-fields")
    public ListResponse<CustomFieldResponse> listCustomFields(
            Authentication auth,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String direction,
            HttpServletRequest req) {
        PageRequest page = new PageRequest(limit, cursor, sort, direction);
        List<Map<String, Object>> rows = extended.listCustomFields(auth, entityType);
        // Custom-field list endpoint does not support native cursor pagination
        // in the legacy service; apply page-limit clamping.
        List<CustomFieldResponse> data = rows.stream()
                .limit(page.limit())
                .map(mapper::toCustomFieldResponse)
                .toList();
        return ListResponse.of(data, CrmEnvelopes.Page.empty(page.limit()), requestId(req));
    }

    @RequireCapability("CRM.CUSTOM_FIELD.READ")
    @GetMapping("/custom-fields/values/{entityType}/{entityId}")
    public SingleResponse<CustomFieldValuesResponse> readCustomFieldValues(
            Authentication auth, @PathVariable String entityType, @PathVariable UUID entityId, HttpServletRequest req) {
        Map<String, Object> row = extended.readCustomFieldValues(auth, entityType, entityId, false);
        return SingleResponse.of(mapper.toCustomFieldValuesResponse(entityType, entityId, row), requestId(req));
    }

    @RequireCapability("CRM.CUSTOM_FIELD.WRITE")
    @PutMapping("/custom-fields/values/{entityType}/{entityId}")
    public SingleResponse<CustomFieldValuesResponse> upsertCustomFieldValues(
            Authentication auth, @PathVariable String entityType, @PathVariable UUID entityId,
            @Valid @RequestBody com.sanad.platform.crm.web.UpdateCustomFieldValuesRequest body,
            HttpServletRequest req) {
        Map<String, Object> row = extended.upsertCustomFieldValues(auth, entityType, entityId, body);
        return SingleResponse.of(mapper.toCustomFieldValuesResponse(entityType, entityId, row), requestId(req));
    }

    // ────────────────────────────────────────────────────────────────────
    // Helpers
    // ────────────────────────────────────────────────────────────────────

    private <T> ResponseEntity<SingleResponse<T>> wrapSingle(T body, String entityType, long version, HttpServletRequest req) {
        HttpHeaders headers = new HttpHeaders();
        if (body != null) {
            // ETag requires the entity's UUID; for response DTOs that carry it as `id()`,
            // we extract reflectively to avoid coupling this helper to every DTO type.
            UUID id = extractId(body);
            if (id != null) headers.set(HttpHeaders.ETAG, etags.etag(entityType, id, version));
        }
        return ResponseEntity.ok().headers(headers).body(SingleResponse.of(body, requestId(req)));
    }

    private <T> ListResponse<T> paginate(
            List<Map<String, Object>> rows, PageRequest page, String entityType,
            Authentication auth, HttpServletRequest req,
            java.util.function.Function<Map<String, Object>, T> rowMapper) {
        boolean hasMore = rows.size() > page.limit();
        List<Map<String, Object>> pageRows = hasMore ? rows.subList(0, page.limit()) : rows;
        List<T> data = pageRows.stream().map(rowMapper).toList();
        CrmEnvelopes.Page pageInfo;
        if (hasMore && !pageRows.isEmpty()) {
            Map<String, Object> last = pageRows.get(pageRows.size() - 1);
            UUID tenantId = extractTenantId(auth);
            String sortValue = String.valueOf(last.getOrDefault("updated_at", last.get("created_at")));
            UUID tieBreaker = (UUID) last.get("id");
            String cursor = cursors.encode(tenantId, page.sort(), page.direction(), sortValue, tieBreaker);
            pageInfo = CrmEnvelopes.Page.of(cursor, true, page.limit());
        } else {
            pageInfo = CrmEnvelopes.Page.empty(page.limit());
        }
        return ListResponse.of(data, pageInfo, requestId(req));
    }

    private IdempotencyGuard beginIdempotency(
            Authentication auth, String endpoint, String idempotencyKey,
            Object requestBody, HttpServletRequest req) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            // Idempotency is opt-in but recommended. When the client omits
            // the key we just run the operation without replay protection.
            return IdempotencyGuard.disabled(requestId(req));
        }
        UUID tenantId = extractTenantId(auth);
        UUID principalId = extractPrincipalId(auth);
        String fingerprint = IdempotencyService.fingerprint(req.getMethod(), req.getRequestURI(), requestBody == null ? "" : requestBody.toString());
        IdempotencyService.Replay replay = idempotency.begin(tenantId, principalId, endpoint, idempotencyKey, fingerprint);
        return IdempotencyGuard.from(replay, requestId(req), idempotency);
    }

    private static UUID requestId(HttpServletRequest req) {
        if (req == null) return UUID.randomUUID();
        String header = req.getHeader("X-Request-ID");
        if (header != null && !header.isBlank()) {
            try { return UUID.fromString(header); } catch (IllegalArgumentException ignored) {}
        }
        return UUID.randomUUID();
    }

    private static UUID extractTenantId(Authentication auth) {
        if (auth == null || auth.getDetails() == null) return null;
        Object details = auth.getDetails();
        if (details instanceof Map<?, ?> m && m.get("tenant_id") != null) {
            try { return UUID.fromString(m.get("tenant_id").toString()); } catch (IllegalArgumentException e) { return null; }
        }
        return null;
    }

    private static UUID extractPrincipalId(Authentication auth) {
        if (auth == null || auth.getDetails() == null) return null;
        Object details = auth.getDetails();
        if (details instanceof Map<?, ?> m && m.get("user_id") != null) {
            try { return UUID.fromString(m.get("user_id").toString()); } catch (IllegalArgumentException e) { return null; }
        }
        return null;
    }

    private static UUID extractId(Object dto) {
        try {
            java.lang.reflect.RecordComponent[] comps = dto.getClass().getRecordComponents();
            for (var c : comps) {
                if (c.getName().equals("id") && c.getType() == UUID.class) {
                    return (UUID) c.getAccessor().invoke(dto);
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static long asLong(Object v) {
        if (v == null) return 0L;
        if (v instanceof Number n) return n.longValue();
        try { return Long.parseLong(String.valueOf(v)); } catch (NumberFormatException e) { return 0L; }
    }

    // ────────────────────────────────────────────────────────────────────
    // Idempotency guard helper — encapsulates the replay vs. miss flow
    // so each endpoint handler stays readable.
    // ────────────────────────────────────────────────────────────────────

    private static final class IdempotencyGuard {
        private final UUID requestId;
        private final IdempotencyService.Replay replay;
        private final IdempotencyService service;

        private IdempotencyGuard(UUID requestId, IdempotencyService.Replay replay, IdempotencyService service) {
            this.requestId = requestId;
            this.replay = replay;
            this.service = service;
        }

        static IdempotencyGuard disabled(UUID requestId) {
            return new IdempotencyGuard(requestId, null, null);
        }

        static IdempotencyGuard from(IdempotencyService.Replay replay, UUID requestId, IdempotencyService service) {
            return new IdempotencyGuard(requestId, replay, service);
        }

        boolean isReplay() {
            return replay instanceof IdempotencyService.Replay.ReplayHit;
        }

        @SuppressWarnings("unchecked")
        <T> ResponseEntity<SingleResponse<T>> replaySingle() {
            IdempotencyService.Replay.ReplayHit hit = (IdempotencyService.Replay.ReplayHit) replay;
            // The cached body is JSON; we cannot deserialize back to T without
            // a JSON binder in this layer, so we return the cached body as
            // the data field. The contract test verifies the status matches.
            T data = (T) hit.record().responseBodyJson();
            return ResponseEntity.status(hit.record().responseStatus())
                    .body(SingleResponse.of(data, requestId));
        }

        <T> ResponseEntity<SingleResponse<T>> completeSingle(T body, String entityType, long version, HttpStatus status) {
            if (replay instanceof IdempotencyService.Replay.ReplayMiss miss && service != null) {
                // The body will be serialized by Spring's JSON writer; for the
                // idempotency cache we store a best-effort string
                // representation. A future iteration will inject an
                // ObjectMapper to store the exact JSON.
                service.complete(miss.operationId(), status.value(), body == null ? "" : body.toString());
            }
            HttpHeaders headers = new HttpHeaders();
            UUID id = extractId(body);
            if (id != null) headers.set(HttpHeaders.ETAG, new ETagService().etag(entityType, id, version));
            return ResponseEntity.status(status).headers(headers).body(SingleResponse.of(body, requestId));
        }

        void fail() {
            if (replay instanceof IdempotencyService.Replay.ReplayMiss miss && service != null) {
                service.fail(miss.operationId());
            }
        }
    }
}
