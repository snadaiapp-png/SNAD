package com.sanad.platform.crm.web;

import com.sanad.platform.crm.legacy.infrastructure.LegacyCrmInfrastructureService;
import com.sanad.platform.crm.legacy.infrastructure.CrmV2AtomicMutationInfrastructureService;
import com.sanad.platform.crm.party.application.AccountUseCases;
import com.sanad.platform.crm.party.application.ContactUseCases;
import com.sanad.platform.crm.party.domain.AccountRepository.AccountRecord;
import com.sanad.platform.crm.party.domain.AccountRepository.CreateAccountCommand;
import com.sanad.platform.crm.party.domain.AccountRepository.UpdateAccountCommand;
import com.sanad.platform.crm.party.domain.ContactRepository.ContactRecord;
import com.sanad.platform.crm.party.domain.ContactRepository.CreateContactCommand;
import com.sanad.platform.crm.party.domain.ContactRepository.UpdateContactCommand;

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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Typed and governed CRM v2 contract surface. */
@RestController
@RequestMapping("/api/v2/crm")
public class CrmContractController {
    private final CrmService legacy;
    private final LegacyCrmInfrastructureService extended;
    private final AccountUseCases accountUseCases;
    private final ContactUseCases contactUseCases;
    private final CrmV2AtomicMutationInfrastructureService atomic;
    private final CrmDtoMapper mapper;
    private final ETagService etags;
    private final CursorCodec cursors;
    private final CrmIdempotencyHttpSupport idempotency;

    public CrmContractController(
            CrmService legacy,
            LegacyCrmInfrastructureService extended,
            AccountUseCases accountUseCases,
            ContactUseCases contactUseCases,
            CrmV2AtomicMutationInfrastructureService atomic,
            CrmDtoMapper mapper,
            ETagService etags,
            CursorCodec cursors,
            CrmIdempotencyHttpSupport idempotency) {
        this.legacy = legacy;
        this.extended = extended;
        this.accountUseCases = accountUseCases;
        this.contactUseCases = contactUseCases;
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
        AccountRecord record = accountUseCases.getById(tenantId(auth), accountId);
        AccountResponse body = mapper.toAccountResponse(toAccountMap(record));
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
        List<AccountRecord> rows = accountUseCases.list(tenantId(auth), page.limit() + 1, search);
        boolean hasMore = rows.size() > page.limit();
        List<AccountRecord> pageRows = hasMore ? rows.subList(0, page.limit()) : rows;
        List<AccountResponse> data = pageRows.stream().map(r -> mapper.toAccountResponse(toAccountMap(r))).toList();
        CrmEnvelopes.Page pageInfo = hasMore && !pageRows.isEmpty() ? CrmEnvelopes.Page.of(
                cursors.encode(tenantId(auth), page.sort(), page.direction(),
                        pageRows.get(pageRows.size()-1).updatedAt() == null ? null : pageRows.get(pageRows.size()-1).updatedAt().toString(),
                        pageRows.get(pageRows.size()-1).id()),
                true, page.limit()) : CrmEnvelopes.Page.empty(page.limit());
        return ListResponse.of(data, pageInfo, requestId(request));
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
            AccountRecord record = accountUseCases.create(tenantId(auth), userId(auth),
                    new CreateAccountCommand(body.displayName(), body.accountType(), body.ownerUserId(),
                            body.parentAccountId(), body.primaryCurrencyCode(), body.preferredLocale(),
                            body.timeZone(), body.source()));
            AccountResponse response = mapper.toAccountResponse(toAccountMap(record));
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
        AccountRecord current = accountUseCases.getById(tenantId(auth), accountId);
        long expectedVersion = current.version();
        etags.validateIfMatch(ifMatch, "account", accountId, expectedVersion);
        AccountRecord updated = accountUseCases.update(tenantId(auth), userId(auth), accountId,
                new UpdateAccountCommand(body.displayName(), body.ownerUserId(), body.parentAccountId(),
                        body.primaryCurrencyCode(), body.preferredLocale(), body.timeZone(), body.source()),
                expectedVersion);
        AccountResponse response = mapper.toAccountResponse(toAccountMap(updated));
        return wrapSingle(response, "account", response.version(), request);
    }

    @RequireCapability("CRM.ACCOUNT.ARCHIVE")
    @PatchMapping("/accounts/{accountId}/archive")
    public ResponseEntity<SingleResponse<ArchiveAccountResponse>> archiveAccount(
            Authentication auth,
            @PathVariable UUID accountId,
            @RequestHeader(value = "If-Match", required = false) String ifMatch,
            HttpServletRequest request) {
        AccountRecord current = accountUseCases.getById(tenantId(auth), accountId);
        long expectedVersion = current.version();
        etags.validateIfMatch(ifMatch, "account", accountId, expectedVersion);
        AccountRecord archived = accountUseCases.archive(tenantId(auth), userId(auth), accountId, expectedVersion);
        ArchiveAccountResponse response = new ArchiveAccountResponse(archived.id(), archived.version(), archived.lifecycleStatus(), null);
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
        ContactRecord contactRecord = contactUseCases.getById(tenantId(auth), contactId);
        ContactResponse body = mapper.toContactResponse(toContactMap(contactRecord));
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
        List<ContactRecord> contactRows = contactUseCases.list(tenantId(auth), page.limit() + 1, accountId, search);
        boolean contactHasMore = contactRows.size() > page.limit();
        List<ContactRecord> contactPageRows = contactHasMore ? contactRows.subList(0, page.limit()) : contactRows;
        List<ContactResponse> contactData = contactPageRows.stream().map(r -> mapper.toContactResponse(toContactMap(r))).toList();
        CrmEnvelopes.Page contactPageInfo = contactHasMore && !contactPageRows.isEmpty() ? CrmEnvelopes.Page.of(
                cursors.encode(tenantId(auth), page.sort(), page.direction(),
                        contactPageRows.get(contactPageRows.size()-1).updatedAt() == null ? null : contactPageRows.get(contactPageRows.size()-1).updatedAt().toString(),
                        contactPageRows.get(contactPageRows.size()-1).id()),
                true, page.limit()) : CrmEnvelopes.Page.empty(page.limit());
        return ListResponse.of(contactData, contactPageInfo, requestId(request));
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
            ContactRecord record = contactUseCases.create(tenantId(auth), userId(auth),
                    new com.sanad.platform.crm.party.domain.ContactRepository.CreateContactCommand(
                            body.accountId(), body.givenName(), body.familyName(), body.primaryEmail(),
                            body.primaryPhone(), body.preferredLocale(), body.timeZone(),
                            body.ownerUserId(), body.consentSummary()));
            ContactResponse response = mapper.toContactResponse(toContactMap(record));
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


    private static UUID tenantId(Authentication auth) {
        if (auth == null || auth.getDetails() == null) return null;
        Object d = auth.getDetails();
        if (d instanceof Map<?, ?> m && m.get("tenant_id") != null) {
            try { return UUID.fromString(m.get("tenant_id").toString()); } catch (Exception e) { return null; }
        }
        return null;
    }

    private static UUID userId(Authentication auth) {
        if (auth == null || auth.getDetails() == null) return null;
        Object d = auth.getDetails();
        if (d instanceof Map<?, ?> m && m.get("user_id") != null) {
            try { return UUID.fromString(m.get("user_id").toString()); } catch (Exception e) { return null; }
        }
        return null;
    }

    private static Map<String, Object> toAccountMap(AccountRecord r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.id()); m.put("version", r.version()); m.put("display_name", r.displayName());
        m.put("normalized_name", r.normalizedName()); m.put("account_type", r.accountType());
        m.put("lifecycle_status", r.lifecycleStatus()); m.put("primary_currency_code", r.primaryCurrencyCode());
        m.put("preferred_locale", r.preferredLocale()); m.put("time_zone", r.timeZone());
        m.put("source", r.source()); m.put("parent_account_id", r.parentAccountId());
        m.put("owner_user_id", r.ownerUserId());
        m.put("created_at", r.createdAt() == null ? null : java.sql.Timestamp.from(r.createdAt()));
        m.put("updated_at", r.updatedAt() == null ? null : java.sql.Timestamp.from(r.updatedAt()));
        return m;
    }

    private static java.util.Map<String, Object> toContactMap(ContactRecord r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.id()); m.put("version", r.version()); m.put("account_id", r.accountId());
        m.put("given_name", r.givenName()); m.put("family_name", r.familyName());
        m.put("display_name", r.displayName()); m.put("primary_email", r.primaryEmail());
        m.put("normalized_email", r.normalizedEmail()); m.put("primary_phone", r.primaryPhone());
        m.put("preferred_locale", r.preferredLocale()); m.put("time_zone", r.timeZone());
        m.put("lifecycle_status", r.lifecycleStatus()); m.put("owner_user_id", r.ownerUserId());
        m.put("consent_summary", r.consentSummary());
        m.put("created_at", r.createdAt() == null ? null : java.sql.Timestamp.from(r.createdAt()));
        m.put("updated_at", r.updatedAt() == null ? null : java.sql.Timestamp.from(r.updatedAt()));
        return m;
    }
}
