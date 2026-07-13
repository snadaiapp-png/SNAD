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
import com.sanad.platform.crm.mapper.CrmDtoMapper;
import com.sanad.platform.crm.pagination.CrmEnvelopes;
import com.sanad.platform.crm.pagination.CrmEnvelopes.ListResponse;
import com.sanad.platform.crm.pagination.CrmEnvelopes.SingleResponse;
import com.sanad.platform.crm.pagination.CursorCodec;
import com.sanad.platform.crm.pagination.PageRequest;
import com.sanad.platform.security.authorization.RequireCapability;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Typed and governed CRM v2 contract surface. */
@RestController
@RequestMapping("/api/v2/crm")
public class CrmContractController {
    private final CrmService legacy;
    private final CrmExtendedService extended;
    private final CrmV2AtomicMutationService atomic;
    private final CrmDtoMapper mapper;
    private final ETagService etags;
    private final CursorCodec cursors;
    private final CrmIdempotencyHttpSupport idempotency;

    public CrmContractController(
            CrmService legacy,
            CrmExtendedService extended,
            CrmV2AtomicMutationService atomic,
            CrmDtoMapper mapper,
            ETagService etags,
            CursorCodec cursors,
            CrmIdempotencyHttpSupport idempotency) {
        this.legacy = legacy;
        this.extended = extended;
        this.atomic = atomic;
        this.mapper = mapper;
        this.etags = etags;
        this.cursors = cursors;
        this.idempotency = idempotency;
    }

    @RequireCapability("CRM.ACCOUNT.READ")
    @GetMapping("/accounts/{accountId}")
    public ResponseEntity<SingleResponse<AccountResponse>> getAccount(
            Authentication auth, @PathVariable UUID accountId, HttpServletRequest request) {
        AccountResponse body = mapper.toAccountResponse(legacy.getAccount(auth, accountId));
        return wrapSingle(body, "account", body.version(), request);
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
            HttpServletRequest request) {
        PageRequest page = page(limit, cursor, sort, direction, auth);
        return paginate(
                legacy.listAccounts(auth, page.limit() + 1, search),
                page,
                auth,
                request,
                mapper::toAccountResponse);
    }

    @RequireCapability("CRM.ACCOUNT.WRITE")
    @PostMapping("/accounts")
    public ResponseEntity<SingleResponse<AccountResponse>> createAccount(
            Authentication auth,
            @Valid @RequestBody CreateAccountRequest body,
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            HttpServletRequest request) {
        var guard = idempotency.begin(auth, "POST:/api/v2/crm/accounts", key, body, request);
        if (guard.isReplay()) return idempotency.replay(guard, AccountResponse.class);
        try {
            AccountResponse response = mapper.toAccountResponse(legacy.createAccount(auth, body));
            return idempotency.complete(
                    guard, response, "account", response.version(), HttpStatus.CREATED);
        } catch (RuntimeException exception) {
            idempotency.fail(guard);
            throw exception;
        }
    }

    @RequireCapability("CRM.ACCOUNT.WRITE")
    @PatchMapping("/accounts/{accountId}")
    public ResponseEntity<SingleResponse<AccountResponse>> updateAccount(
            Authentication auth,
            @PathVariable UUID accountId,
            @Valid @RequestBody UpdateAccountRequest body,
            @RequestHeader(value = "If-Match", required = false) String ifMatch,
            HttpServletRequest request) {
        Map<String, Object> current = legacy.getAccount(auth, accountId);
        long expectedVersion = asLong(current.get("version"));
        etags.validateIfMatch(ifMatch, "account", accountId, expectedVersion);
        AccountResponse response = mapper.toAccountResponse(
                atomic.updateAccount(auth, accountId, body, expectedVersion));
        return wrapSingle(response, "account", response.version(), request);
    }

    @RequireCapability("CRM.ACCOUNT.ARCHIVE")
    @PatchMapping("/accounts/{accountId}/archive")
    public ResponseEntity<SingleResponse<ArchiveAccountResponse>> archiveAccount(
            Authentication auth,
            @PathVariable UUID accountId,
            @RequestHeader(value = "If-Match", required = false) String ifMatch,
            HttpServletRequest request) {
        Map<String, Object> current = legacy.getAccount(auth, accountId);
        long expectedVersion = asLong(current.get("version"));
        etags.validateIfMatch(ifMatch, "account", accountId, expectedVersion);
        ArchiveAccountResponse response = mapper.toArchiveAccountResponse(
                atomic.setAccountArchived(auth, accountId, true, expectedVersion));
        return wrapSingle(response, "account", response.version(), request);
    }

    @SuppressWarnings("unchecked")
    @RequireCapability("CRM.ACCOUNT.READ")
    @GetMapping("/accounts/{accountId}/customer-360")
    public SingleResponse<Customer360Response> customer360(
            Authentication auth, @PathVariable UUID accountId, HttpServletRequest request) {
        Map<String, Object> row = extended.customer360(auth, accountId);
        Customer360Response body = mapper.toCustomer360Response(
                (Map<String, Object>) row.get("account"),
                (List<Map<String, Object>>) row.get("contacts"),
                (List<Map<String, Object>>) row.get("opportunities"),
                (List<Map<String, Object>>) row.get("activities"),
                (List<Map<String, Object>>) row.get("timeline"),
                (Map<String, Object>) row.get("customFields"));
        return SingleResponse.of(body, requestId(request));
    }

    @RequireCapability("CRM.CONTACT.READ")
    @GetMapping("/contacts/{contactId}")
    public ResponseEntity<SingleResponse<ContactResponse>> getContact(
            Authentication auth, @PathVariable UUID contactId, HttpServletRequest request) {
        ContactResponse body = mapper.toContactResponse(extended.getContact(auth, contactId));
        return wrapSingle(body, "contact", body.version(), request);
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
            HttpServletRequest request) {
        PageRequest page = page(limit, cursor, sort, direction, auth);
        return paginate(
                legacy.listContacts(auth, page.limit() + 1, accountId, search),
                page,
                auth,
                request,
                mapper::toContactResponse);
    }

    @RequireCapability("CRM.CONTACT.WRITE")
    @PostMapping("/contacts")
    public ResponseEntity<SingleResponse<ContactResponse>> createContact(
            Authentication auth,
            @Valid @RequestBody CreateContactRequest body,
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            HttpServletRequest request) {
        var guard = idempotency.begin(auth, "POST:/api/v2/crm/contacts", key, body, request);
        if (guard.isReplay()) return idempotency.replay(guard, ContactResponse.class);
        try {
            ContactResponse response = mapper.toContactResponse(legacy.createContact(auth, body));
            return idempotency.complete(
                    guard, response, "contact", response.version(), HttpStatus.CREATED);
        } catch (RuntimeException exception) {
            idempotency.fail(guard);
            throw exception;
        }
    }

    @RequireCapability("CRM.LEAD.READ")
    @GetMapping("/leads/{leadId}")
    public ResponseEntity<SingleResponse<LeadResponse>> getLead(
            Authentication auth, @PathVariable UUID leadId, HttpServletRequest request) {
        LeadResponse body = mapper.toLeadResponse(extended.getLead(auth, leadId));
        return wrapSingle(body, "lead", body.version(), request);
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
            HttpServletRequest request) {
        PageRequest page = page(limit, cursor, sort, direction, auth);
        return paginate(
                legacy.listLeads(auth, page.limit() + 1, status),
                page,
                auth,
                request,
                mapper::toLeadResponse);
    }

    @RequireCapability("CRM.LEAD.WRITE")
    @PostMapping("/leads")
    public ResponseEntity<SingleResponse<LeadResponse>> createLead(
            Authentication auth,
            @Valid @RequestBody CreateLeadRequest body,
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            HttpServletRequest request) {
        var guard = idempotency.begin(auth, "POST:/api/v2/crm/leads", key, body, request);
        if (guard.isReplay()) return idempotency.replay(guard, LeadResponse.class);
        try {
            LeadResponse response = mapper.toLeadResponse(legacy.createLead(auth, body));
            return idempotency.complete(
                    guard, response, "lead", response.version(), HttpStatus.CREATED);
        } catch (RuntimeException exception) {
            idempotency.fail(guard);
            throw exception;
        }
    }

    @RequireCapability("CRM.LEAD.CONVERT")
    @PostMapping("/leads/{leadId}/convert")
    public ResponseEntity<SingleResponse<LeadConversionResponse>> convertLead(
            Authentication auth,
            @PathVariable UUID leadId,
            @Valid @RequestBody ConvertLeadRequest body,
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            HttpServletRequest request) {
        String endpoint = "POST:/api/v2/crm/leads/" + leadId + "/convert";
        var guard = idempotency.begin(auth, endpoint, key, body, request);
        if (guard.isReplay()) return idempotency.replay(guard, LeadConversionResponse.class);
        try {
            LeadConversionResponse response = mapper.toLeadConversionResponse(legacy.convertLead(auth, leadId, body));
            return idempotency.complete(
                    guard, response, "lead-conversion", 0L, HttpStatus.OK);
        } catch (RuntimeException exception) {
            idempotency.fail(guard);
            throw exception;
        }
    }

    @RequireCapability("CRM.OPPORTUNITY.READ")
    @GetMapping("/pipelines")
    public ListResponse<PipelineResponse> listPipelines(
            Authentication auth, HttpServletRequest request) {
        List<PipelineResponse> data = legacy.listPipelines(auth).stream()
                .map(row -> mapper.toPipelineResponse(row, List.of()))
                .toList();
        return ListResponse.of(data, CrmEnvelopes.Page.empty(PageRequest.DEFAULT_LIMIT), requestId(request));
    }

    @RequireCapability("CRM.OPPORTUNITY.READ")
    @GetMapping("/pipelines/{pipelineId}/stages")
    public ListResponse<StageResponse> listPipelineStages(
            Authentication auth, @PathVariable UUID pipelineId, HttpServletRequest request) {
        List<StageResponse> data = extended.listPipelineStages(auth, pipelineId).stream()
                .map(mapper::toStageResponse)
                .toList();
        return ListResponse.of(data, CrmEnvelopes.Page.empty(PageRequest.DEFAULT_LIMIT), requestId(request));
    }

    @RequireCapability("CRM.OPPORTUNITY.READ")
    @GetMapping("/opportunities/{opportunityId}")
    public ResponseEntity<SingleResponse<OpportunityResponse>> getOpportunity(
            Authentication auth, @PathVariable UUID opportunityId, HttpServletRequest request) {
        OpportunityResponse body = mapper.toOpportunityResponse(extended.getOpportunity(auth, opportunityId));
        return wrapSingle(body, "opportunity", body.version(), request);
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
            HttpServletRequest request) {
        PageRequest page = page(limit, cursor, sort, direction, auth);
        return paginate(
                legacy.listOpportunities(auth, page.limit() + 1, accountId),
                page,
                auth,
                request,
                mapper::toOpportunityResponse);
    }

    @RequireCapability("CRM.OPPORTUNITY.WRITE")
    @PostMapping("/opportunities")
    public ResponseEntity<SingleResponse<OpportunityResponse>> createOpportunity(
            Authentication auth,
            @Valid @RequestBody CreateOpportunityRequest body,
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            HttpServletRequest request) {
        var guard = idempotency.begin(auth, "POST:/api/v2/crm/opportunities", key, body, request);
        if (guard.isReplay()) return idempotency.replay(guard, OpportunityResponse.class);
        try {
            OpportunityResponse response = mapper.toOpportunityResponse(legacy.createOpportunity(auth, body));
            return idempotency.complete(
                    guard, response, "opportunity", response.version(), HttpStatus.CREATED);
        } catch (RuntimeException exception) {
            idempotency.fail(guard);
            throw exception;
        }
    }

    @RequireCapability("CRM.ACTIVITY.READ")
    @GetMapping("/activities/{activityId}")
    public ResponseEntity<SingleResponse<ActivityResponse>> getActivity(
            Authentication auth, @PathVariable UUID activityId, HttpServletRequest request) {
        ActivityResponse body = mapper.toActivityResponse(extended.getActivity(auth, activityId));
        return wrapSingle(body, "activity", body.version(), request);
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
            HttpServletRequest request) {
        PageRequest page = page(limit, cursor, sort, direction, auth);
        return paginate(
                extended.listActivities(auth, page.limit() + 1, relatedType, relatedId, status),
                page,
                auth,
                request,
                mapper::toActivityResponse);
    }

    @RequireCapability("CRM.ACTIVITY.WRITE")
    @PostMapping("/activities")
    public ResponseEntity<SingleResponse<ActivityResponse>> createActivity(
            Authentication auth,
            @Valid @RequestBody CreateActivityRequest body,
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            HttpServletRequest request) {
        var guard = idempotency.begin(auth, "POST:/api/v2/crm/activities", key, body, request);
        if (guard.isReplay()) return idempotency.replay(guard, ActivityResponse.class);
        try {
            ActivityResponse response = mapper.toActivityResponse(legacy.createActivity(auth, body));
            return idempotency.complete(
                    guard, response, "activity", response.version(), HttpStatus.CREATED);
        } catch (RuntimeException exception) {
            idempotency.fail(guard);
            throw exception;
        }
    }

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
            HttpServletRequest request) {
        PageRequest page = page(limit, cursor, sort, direction, auth);
        return paginate(
                legacy.timeline(auth, subjectType, subjectId, page.limit() + 1),
                page,
                auth,
                request,
                mapper::toTimelineEventResponse);
    }

    @RequireCapability("CRM.IMPORT.READ")
    @GetMapping("/imports")
    public ListResponse<ImportJobResponse> listImportJobs(
            Authentication auth,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String direction,
            HttpServletRequest request) {
        PageRequest page = page(limit, cursor, sort, direction, auth);
        return paginate(
                extended.listImportJobs(auth, page.limit() + 1),
                page,
                auth,
                request,
                mapper::toImportJobResponse);
    }

    @RequireCapability("CRM.IMPORT.READ")
    @GetMapping("/imports/{jobId}")
    public ResponseEntity<SingleResponse<ImportJobResponse>> getImportJob(
            Authentication auth, @PathVariable UUID jobId, HttpServletRequest request) {
        ImportJobResponse body = mapper.toImportJobResponse(extended.getImportJob(auth, jobId));
        return wrapSingle(body, "import", 0L, request);
    }

    @RequireCapability("CRM.IMPORT.READ")
    @GetMapping("/imports/{jobId}/errors")
    public ListResponse<ImportErrorResponse> listImportErrors(
            Authentication auth,
            @PathVariable UUID jobId,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String direction,
            HttpServletRequest request) {
        PageRequest page = page(limit, cursor, sort, direction, auth);
        return paginate(
                extended.listImportErrors(auth, jobId, page.limit() + 1),
                page,
                auth,
                request,
                mapper::toImportErrorResponse);
    }

    @RequireCapability("CRM.CUSTOM_FIELD.READ")
    @GetMapping("/custom-fields")
    public ListResponse<CustomFieldResponse> listCustomFields(
            Authentication auth,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String direction,
            HttpServletRequest request) {
        PageRequest page = page(limit, cursor, sort, direction, auth);
        List<CustomFieldResponse> data = extended.listCustomFields(auth, entityType).stream()
                .limit(page.limit())
                .map(mapper::toCustomFieldResponse)
                .toList();
        return ListResponse.of(data, CrmEnvelopes.Page.empty(page.limit()), requestId(request));
    }

    @RequireCapability("CRM.CUSTOM_FIELD.READ")
    @GetMapping("/custom-fields/values/{entityType}/{entityId}")
    public SingleResponse<CustomFieldValuesResponse> readCustomFieldValues(
            Authentication auth,
            @PathVariable String entityType,
            @PathVariable UUID entityId,
            HttpServletRequest request) {
        Map<String, Object> row = extended.readCustomFieldValues(auth, entityType, entityId, false);
        return SingleResponse.of(
                mapper.toCustomFieldValuesResponse(entityType, entityId, row),
                requestId(request));
    }

    @RequireCapability("CRM.CUSTOM_FIELD.WRITE")
    @PutMapping("/custom-fields/values/{entityType}/{entityId}")
    public SingleResponse<CustomFieldValuesResponse> upsertCustomFieldValues(
            Authentication auth,
            @PathVariable String entityType,
            @PathVariable UUID entityId,
            @Valid @RequestBody UpdateCustomFieldValuesRequest body,
            HttpServletRequest request) {
        Map<String, Object> row = extended.upsertCustomFieldValues(auth, entityType, entityId, body);
        return SingleResponse.of(
                mapper.toCustomFieldValuesResponse(entityType, entityId, row),
                requestId(request));
    }

    private PageRequest page(
            Integer limit, String cursor, String sort, String direction, Authentication auth) {
        PageRequest page = new PageRequest(limit, cursor, sort, direction);
        if (page.cursor() != null && !page.cursor().isBlank()) {
            cursors.decode(page.cursor(), requiredTenant(auth), page.sort(), page.direction());
        }
        return page;
    }

    private <T> ResponseEntity<SingleResponse<T>> wrapSingle(
            T body, String entityType, long version, HttpServletRequest request) {
        HttpHeaders headers = new HttpHeaders();
        UUID id = extractId(body);
        if (id != null) headers.setETag(etags.etag(entityType, id, version));
        return ResponseEntity.ok().headers(headers).body(SingleResponse.of(body, requestId(request)));
    }

    private <T> ListResponse<T> paginate(
            List<Map<String, Object>> rows,
            PageRequest page,
            Authentication auth,
            HttpServletRequest request,
            java.util.function.Function<Map<String, Object>, T> mapperFunction) {
        boolean hasMore = rows.size() > page.limit();
        List<Map<String, Object>> pageRows = hasMore ? rows.subList(0, page.limit()) : rows;
        List<T> data = pageRows.stream().map(mapperFunction).toList();
        CrmEnvelopes.Page pageInfo = CrmEnvelopes.Page.empty(page.limit());
        if (hasMore && !pageRows.isEmpty()) {
            Map<String, Object> last = pageRows.get(pageRows.size() - 1);
            Object sortRaw = last.getOrDefault("updated_at", last.get("created_at"));
            String sortValue = sortRaw == null ? "" : String.valueOf(sortRaw);
            UUID tieBreaker = asUuid(last.get("id"));
            String next = cursors.encode(
                    requiredTenant(auth), page.sort(), page.direction(), sortValue, tieBreaker);
            pageInfo = CrmEnvelopes.Page.of(next, true, page.limit());
        }
        return ListResponse.of(data, pageInfo, requestId(request));
    }

    private static UUID requiredTenant(Authentication auth) {
        if (auth == null || !auth.isAuthenticated()
                || !(auth.getDetails() instanceof Map<?, ?> details)
                || details.get("tenant_id") == null) {
            throw new CrmContractException(CrmErrorCode.UNAUTHORIZED);
        }
        try {
            return UUID.fromString(details.get("tenant_id").toString());
        } catch (IllegalArgumentException exception) {
            throw new CrmContractException(CrmErrorCode.UNAUTHORIZED);
        }
    }

    private static UUID requestId(HttpServletRequest request) {
        if (request != null) {
            String value = request.getHeader("X-Request-ID");
            if (value != null && !value.isBlank()) {
                try {
                    return UUID.fromString(value);
                } catch (IllegalArgumentException ignored) {
                    // Generate a valid request id below.
                }
            }
        }
        return UUID.randomUUID();
    }

    private static UUID extractId(Object dto) {
        if (dto == null || !dto.getClass().isRecord()) return null;
        try {
            for (var component : dto.getClass().getRecordComponents()) {
                if ("id".equals(component.getName()) && component.getType() == UUID.class) {
                    return (UUID) component.getAccessor().invoke(dto);
                }
            }
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
        return null;
    }

    private static UUID asUuid(Object value) {
        if (value == null) return null;
        return value instanceof UUID uuid ? uuid : UUID.fromString(value.toString());
    }

    private static long asLong(Object value) {
        if (value == null) return 0L;
        if (value instanceof Number number) return number.longValue();
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException exception) {
            return 0L;
        }
    }
}
