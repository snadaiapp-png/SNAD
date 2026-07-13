package com.sanad.platform.crm.web;
import com.sanad.platform.crm.party.application.AccountUseCases;
import com.sanad.platform.crm.party.application.ContactUseCases;
import com.sanad.platform.crm.lead.application.LeadUseCases;
import com.sanad.platform.crm.opportunity.application.OpportunityUseCases;
import com.sanad.platform.crm.activity.application.ActivityUseCases;
import com.sanad.platform.crm.configuration.application.ConfigurationUseCases;
import com.sanad.platform.crm.party.domain.AccountRepository.AccountRecord;
import com.sanad.platform.crm.party.domain.AccountRepository.UpdateAccountCommand;
import com.sanad.platform.crm.party.domain.ContactRepository.ContactRecord;
import com.sanad.platform.crm.party.domain.ContactRepository.UpdateContactCommand;

import com.sanad.platform.crm.concurrency.ETagService;
import com.sanad.platform.crm.dto.CrmDtos.AccountResponse;
import com.sanad.platform.crm.dto.CrmDtos.ActivityResponse;
import com.sanad.platform.crm.dto.CrmDtos.ContactResponse;
import com.sanad.platform.crm.dto.CrmDtos.CustomFieldResponse;
import com.sanad.platform.crm.dto.CrmDtos.CustomFieldValuesResponse;
import com.sanad.platform.crm.dto.CrmDtos.ImportJobResponse;
import com.sanad.platform.crm.dto.CrmDtos.ImportRunResponse;
import com.sanad.platform.crm.dto.CrmDtos.LeadResponse;
import com.sanad.platform.crm.dto.CrmDtos.OpportunityResponse;
import com.sanad.platform.crm.dto.CrmDtos.PipelineResponse;
import com.sanad.platform.crm.error.CrmContractException;
import com.sanad.platform.crm.error.CrmErrorCode;
import com.sanad.platform.crm.mapper.CrmDtoMapper;
import com.sanad.platform.crm.pagination.CrmEnvelopes;
import com.sanad.platform.crm.pagination.CrmEnvelopes.ListResponse;
import com.sanad.platform.crm.pagination.CrmEnvelopes.SingleResponse;
import com.sanad.platform.crm.pagination.PageRequest;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Additional governed CRM v2 mutations and import/custom-field operations. */
@RestController
@RequestMapping("/api/v2/crm")
public class CrmContractControllerR1 {
    private final CrmService legacy;
    private final CrmExtendedService extended;
    private final AccountUseCases accountUseCases;
    private final ContactUseCases contactUseCases;
    private final LeadUseCases leadUseCases;
    private final OpportunityUseCases opportunityUseCases;
    private final ActivityUseCases activityUseCases;
    private final ConfigurationUseCases configurationUseCases;
    private final CrmV2AtomicMutationService atomic;
    private final CrmDtoMapper mapper;
    private final ETagService etags;
    private final CrmIdempotencyHttpSupport idempotency;

    public CrmContractControllerR1(
            CrmService legacy,
            CrmExtendedService extended,
            AccountUseCases accountUseCases,
            ContactUseCases contactUseCases,
            LeadUseCases leadUseCases,
            OpportunityUseCases opportunityUseCases,
            ActivityUseCases activityUseCases,
            ConfigurationUseCases configurationUseCases,
            CrmV2AtomicMutationService atomic,
            CrmDtoMapper mapper,
            ETagService etags,
            CrmIdempotencyHttpSupport idempotency) {
        this.legacy = legacy;
        this.extended = extended;
        this.accountUseCases = accountUseCases;
        this.contactUseCases = contactUseCases;
        this.leadUseCases = leadUseCases;
        this.opportunityUseCases = opportunityUseCases;
        this.activityUseCases = activityUseCases;
        this.configurationUseCases = configurationUseCases;
        this.atomic = atomic;
        this.mapper = mapper;
        this.etags = etags;
        this.idempotency = idempotency;
    }

    @RequireCapability("CRM.ACCOUNT.ARCHIVE")
    @PatchMapping("/accounts/{accountId}/restore")
    public ResponseEntity<SingleResponse<AccountResponse>> restoreAccount(
            Authentication auth,
            @PathVariable UUID accountId,
            @RequestHeader(value = "If-Match", required = false) String ifMatch,
            HttpServletRequest request) {
        Map<String, Object> current = legacy.getAccount(auth, accountId);
        long expectedVersion = asLong(current.get("version"));
        etags.validateIfMatch(ifMatch, "account", accountId, expectedVersion);
        AccountResponse response = mapper.toAccountResponse(
                atomic.setAccountArchived(auth, accountId, false, expectedVersion));
        return withEtag(response, "account", accountId, response.version(), request);
    }

    @RequireCapability("CRM.CONTACT.WRITE")
    @PatchMapping("/contacts/{contactId}")
    public ResponseEntity<SingleResponse<ContactResponse>> updateContact(
            Authentication auth,
            @PathVariable UUID contactId,
            @Valid @RequestBody UpdateContactRequest body,
            @RequestHeader(value = "If-Match", required = false) String ifMatch,
            HttpServletRequest request) {
        Map<String, Object> current = extended.getContact(auth, contactId);
        long expectedVersion = asLong(current.get("version"));
        etags.validateIfMatch(ifMatch, "contact", contactId, expectedVersion);
        ContactResponse response = mapper.toContactResponse(
                atomic.updateContact(auth, contactId, body, expectedVersion));
        return withEtag(response, "contact", contactId, response.version(), request);
    }

    @RequireCapability("CRM.CONTACT.ARCHIVE")
    @PatchMapping("/contacts/{contactId}/archive")
    public ResponseEntity<SingleResponse<ContactResponse>> archiveContact(
            Authentication auth,
            @PathVariable UUID contactId,
            @RequestHeader(value = "If-Match", required = false) String ifMatch,
            HttpServletRequest request) {
        Map<String, Object> current = extended.getContact(auth, contactId);
        long expectedVersion = asLong(current.get("version"));
        etags.validateIfMatch(ifMatch, "contact", contactId, expectedVersion);
        ContactResponse response = mapper.toContactResponse(
                atomic.setContactArchived(auth, contactId, true, expectedVersion));
        return withEtag(response, "contact", contactId, response.version(), request);
    }

    @RequireCapability("CRM.CONTACT.ARCHIVE")
    @PatchMapping("/contacts/{contactId}/restore")
    public ResponseEntity<SingleResponse<ContactResponse>> restoreContact(
            Authentication auth,
            @PathVariable UUID contactId,
            @RequestHeader(value = "If-Match", required = false) String ifMatch,
            HttpServletRequest request) {
        Map<String, Object> current = extended.getContact(auth, contactId);
        long expectedVersion = asLong(current.get("version"));
        etags.validateIfMatch(ifMatch, "contact", contactId, expectedVersion);
        ContactResponse response = mapper.toContactResponse(
                atomic.setContactArchived(auth, contactId, false, expectedVersion));
        return withEtag(response, "contact", contactId, response.version(), request);
    }

    @RequireCapability("CRM.LEAD.WRITE")
    @PatchMapping("/leads/{leadId}/status")
    public ResponseEntity<SingleResponse<LeadResponse>> changeLeadStatus(
            Authentication auth,
            @PathVariable UUID leadId,
            @Valid @RequestBody UpdateLeadStatusRequest body,
            @RequestHeader(value = "If-Match", required = false) String ifMatch,
            HttpServletRequest request) {
        Map<String, Object> current = extended.getLead(auth, leadId);
        long expectedVersion = asLong(current.get("version"));
        etags.validateIfMatch(ifMatch, "lead", leadId, expectedVersion);
        LeadResponse response = mapper.toLeadResponse(
                atomic.changeLeadStatus(auth, leadId, body, expectedVersion));
        return withEtag(response, "lead", leadId, response.version(), request);
    }

    @RequireCapability("CRM.OPPORTUNITY.WRITE")
    @PatchMapping("/opportunities/{opportunityId}")
    public ResponseEntity<SingleResponse<OpportunityResponse>> updateOpportunity(
            Authentication auth,
            @PathVariable UUID opportunityId,
            @Valid @RequestBody CrmUpdateDtos.UpdateOpportunityRequest body,
            @RequestHeader(value = "If-Match", required = false) String ifMatch,
            HttpServletRequest request) {
        Map<String, Object> current = extended.getOpportunity(auth, opportunityId);
        long expectedVersion = asLong(current.get("version"));
        etags.validateIfMatch(ifMatch, "opportunity", opportunityId, expectedVersion);
        Map<String, Object> updated = extended.updateOpportunity(
                auth, opportunityId, body.amount(), body.name(), body.ownerUserId(), expectedVersion);
        OpportunityResponse response = mapper.toOpportunityResponse(updated);
        return withEtag(response, "opportunity", opportunityId, response.version(), request);
    }

    @RequireCapability("CRM.OPPORTUNITY.WRITE")
    @PatchMapping("/opportunities/{opportunityId}/stage")
    public ResponseEntity<SingleResponse<OpportunityResponse>> moveOpportunityStage(
            Authentication auth,
            @PathVariable UUID opportunityId,
            @Valid @RequestBody MoveOpportunityRequest body,
            @RequestHeader(value = "If-Match", required = false) String ifMatch,
            HttpServletRequest request) {
        Map<String, Object> current = extended.getOpportunity(auth, opportunityId);
        long expectedVersion = asLong(current.get("version"));
        etags.validateIfMatch(ifMatch, "opportunity", opportunityId, expectedVersion);
        OpportunityResponse response = mapper.toOpportunityResponse(
                atomic.moveOpportunityStage(auth, opportunityId, body, expectedVersion));
        return withEtag(response, "opportunity", opportunityId, response.version(), request);
    }

    @RequireCapability("CRM.ACTIVITY.WRITE")
    @PatchMapping("/activities/{activityId}")
    public ResponseEntity<SingleResponse<ActivityResponse>> updateActivity(
            Authentication auth,
            @PathVariable UUID activityId,
            @Valid @RequestBody CrmUpdateDtos.UpdateActivityRequest body,
            @RequestHeader(value = "If-Match", required = false) String ifMatch,
            HttpServletRequest request) {
        Map<String, Object> current = extended.getActivity(auth, activityId);
        long expectedVersion = asLong(current.get("version"));
        etags.validateIfMatch(ifMatch, "activity", activityId, expectedVersion);
        Map<String, Object> updated = extended.updateActivity(
                auth, activityId, body.subject(), body.body(), body.priority(), expectedVersion);
        ActivityResponse response = mapper.toActivityResponse(updated);
        return withEtag(response, "activity", activityId, response.version(), request);
    }

    @RequireCapability("CRM.ACTIVITY.WRITE")
    @PatchMapping("/activities/{activityId}/complete")
    public ResponseEntity<SingleResponse<ActivityResponse>> completeActivity(
            Authentication auth,
            @PathVariable UUID activityId,
            @Valid @RequestBody CompleteActivityRequest body,
            @RequestHeader(value = "If-Match", required = false) String ifMatch,
            HttpServletRequest request) {
        Map<String, Object> current = extended.getActivity(auth, activityId);
        long expectedVersion = asLong(current.get("version"));
        etags.validateIfMatch(ifMatch, "activity", activityId, expectedVersion);
        ActivityResponse response = mapper.toActivityResponse(
                atomic.completeActivity(auth, activityId, body, expectedVersion));
        return withEtag(response, "activity", activityId, response.version(), request);
    }

    @RequireCapability("CRM.ADMIN")
    @PatchMapping("/pipelines/{pipelineId}")
    public ResponseEntity<SingleResponse<PipelineResponse>> updatePipeline(
            Authentication auth,
            @PathVariable UUID pipelineId,
            @Valid @RequestBody CrmUpdateDtos.UpdatePipelineRequest body,
            @RequestHeader(value = "If-Match", required = false) String ifMatch,
            HttpServletRequest request) {
        Map<String, Object> current = legacy.listPipelines(auth).stream()
                .filter(row -> pipelineId.equals(row.get("id")))
                .findFirst()
                .orElseThrow(() -> new CrmContractException(CrmErrorCode.CRM_PIPELINE_NOT_FOUND));
        long expectedVersion = asLong(current.get("version"));
        etags.validateIfMatch(ifMatch, "pipeline", pipelineId, expectedVersion);
        Map<String, Object> updated = extended.updatePipeline(
                auth, pipelineId, body.name(), body.currencyCode(), expectedVersion);
        PipelineResponse response = mapper.toPipelineResponse(updated, List.of());
        return withEtag(response, "pipeline", pipelineId, response.version(), request);
    }

    @RequireCapability("CRM.CUSTOM_FIELD.WRITE")
    @PostMapping("/custom-fields")
    public ResponseEntity<SingleResponse<CustomFieldResponse>> createCustomField(
            Authentication auth,
            @Valid @RequestBody CreateCustomFieldRequest body,
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            HttpServletRequest request) {
        var guard = idempotency.begin(auth, "POST:/api/v2/crm/custom-fields", key, body, request);
        if (guard.isReplay()) return idempotency.replay(guard, CustomFieldResponse.class);
        try {
            CustomFieldResponse response = mapper.toCustomFieldResponse(
                    extended.createCustomField(auth, body));
            return idempotency.complete(
                    guard, response, "custom-field", response.version(), HttpStatus.CREATED);
        } catch (RuntimeException exception) {
            idempotency.fail(guard);
            throw exception;
        }
    }

    @RequireCapability("CRM.CUSTOM_FIELD.WRITE")
    @PatchMapping("/custom-fields/{customFieldId}")
    public ResponseEntity<SingleResponse<CustomFieldResponse>> updateCustomField(
            Authentication auth,
            @PathVariable UUID customFieldId,
            @Valid @RequestBody CrmUpdateDtos.UpdateCustomFieldRequest body,
            @RequestHeader(value = "If-Match", required = false) String ifMatch,
            HttpServletRequest request) {
        Map<String, Object> current = extended.listCustomFields(auth, null).stream()
                .filter(row -> customFieldId.equals(row.get("id")))
                .findFirst()
                .orElseThrow(() -> new CrmContractException(CrmErrorCode.CRM_CUSTOM_FIELD_NOT_FOUND));
        long expectedVersion = asLong(current.get("version"));
        etags.validateIfMatch(ifMatch, "custom-field", customFieldId, expectedVersion);
        Map<String, Object> updated = extended.updateCustomField(
                auth,
                customFieldId,
                body.labelAr(),
                body.labelEn(),
                body.required(),
                body.searchable(),
                body.sensitive(),
                expectedVersion);
        CustomFieldResponse response = mapper.toCustomFieldResponse(updated);
        return withEtag(response, "custom-field", customFieldId, response.version(), request);
    }

    @RequireCapability("CRM.IMPORT.WRITE")
    @PostMapping(value = "/imports/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<SingleResponse<ImportJobResponse>> uploadImport(
            Authentication auth,
            @RequestPart("entityType") String entityType,
            @RequestPart(value = "mapping", required = false) String mapping,
            @RequestPart("file") MultipartFile file,
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            HttpServletRequest request) {
        byte[] fileBytes;
        try {
            fileBytes = file.getBytes();
        } catch (Exception exception) {
            throw new CrmContractException(
                    CrmErrorCode.VALIDATION_ERROR,
                    "Unable to read uploaded file for fingerprinting.",
                    null,
                    exception);
        }
        Map<String, Object> fingerprintBody = new LinkedHashMap<>();
        fingerprintBody.put("entityType", entityType);
        fingerprintBody.put("mapping", mapping);
        fingerprintBody.put("fileSha256", sha256(fileBytes));
        String endpoint = "POST:/api/v2/crm/imports/upload";
        var guard = idempotency.begin(auth, endpoint, key, fingerprintBody, request);
        if (guard.isReplay()) return idempotency.replay(guard, ImportJobResponse.class);
        try {
            ImportJobResponse response = mapper.toImportJobResponse(
                    extended.uploadImport(auth, entityType, mapping, file));
            return idempotency.complete(
                    guard, response, "import", 0L, HttpStatus.CREATED);
        } catch (RuntimeException exception) {
            idempotency.fail(guard);
            throw exception;
        }
    }

    @RequireCapability("CRM.IMPORT.WRITE")
    @PostMapping("/imports/{jobId}/run")
    public ResponseEntity<SingleResponse<ImportRunResponse>> runImport(
            Authentication auth,
            @PathVariable UUID jobId,
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            HttpServletRequest request) {
        String endpoint = "POST:/api/v2/crm/imports/" + jobId + "/run";
        var guard = idempotency.begin(auth, endpoint, key, Map.of("jobId", jobId), request);
        if (guard.isReplay()) return idempotency.replay(guard, ImportRunResponse.class);
        try {
            ImportRunResponse response = mapImportRun(extended.runImport(auth, jobId));
            return idempotency.complete(
                    guard, response, "import-run", 0L, HttpStatus.OK);
        } catch (RuntimeException exception) {
            idempotency.fail(guard);
            throw exception;
        }
    }

    @RequireCapability("CRM.IMPORT.WRITE")
    @PostMapping("/imports/{jobId}/cancel")
    public SingleResponse<ImportJobResponse> cancelImport(
            Authentication auth, @PathVariable UUID jobId, HttpServletRequest request) {
        return SingleResponse.of(
                mapper.toImportJobResponse(extended.cancelImport(auth, jobId)),
                requestId(request));
    }

    @RequireCapability("CRM.IMPORT.READ")
    @GetMapping(value = "/imports/{jobId}/errors.csv", produces = "text/csv")
    public ResponseEntity<String> downloadImportErrors(
            Authentication auth, @PathVariable UUID jobId) {
        return ResponseEntity.ok()
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"crm-import-errors-" + jobId + ".csv\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(extended.importErrorsCsv(auth, jobId));
    }

    @RequireCapability("CRM.CUSTOM_FIELD.READ")
    @GetMapping("/custom-fields/search")
    public ListResponse<CustomFieldValuesResponse> searchCustomFieldValues(
            Authentication auth,
            @RequestParam String entityType,
            @RequestParam String fieldKey,
            @RequestParam String query,
            @RequestParam(required = false) Integer limit,
            HttpServletRequest request) {
        PageRequest page = new PageRequest(limit, null, "updatedAt", "desc");
        List<CustomFieldValuesResponse> data = extended
                .searchCustomFieldValues(auth, entityType, fieldKey, query, page.limit())
                .stream()
                .map(row -> mapper.toCustomFieldValuesResponse(entityType, null, row))
                .toList();
        return ListResponse.of(data, CrmEnvelopes.Page.empty(page.limit()), requestId(request));
    }

    @RequireCapability("CRM.CUSTOM_FIELD.WRITE")
    @PostMapping("/custom-fields/values/{entityType}/{entityId}")
    public ResponseEntity<SingleResponse<CustomFieldValuesResponse>> upsertCustomFieldValuesPost(
            Authentication auth,
            @PathVariable String entityType,
            @PathVariable UUID entityId,
            @Valid @RequestBody UpdateCustomFieldValuesRequest body,
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            HttpServletRequest request) {
        String endpoint = "POST:/api/v2/crm/custom-fields/values/" + entityType + "/" + entityId;
        var guard = idempotency.begin(auth, endpoint, key, body, request);
        if (guard.isReplay()) return idempotency.replay(guard, CustomFieldValuesResponse.class);
        try {
            CustomFieldValuesResponse response = mapper.toCustomFieldValuesResponse(
                    entityType,
                    entityId,
                    extended.upsertCustomFieldValues(auth, entityType, entityId, body));
            return idempotency.complete(
                    guard, response, "custom-field-values", 0L, HttpStatus.OK);
        } catch (RuntimeException exception) {
            idempotency.fail(guard);
            throw exception;
        }
    }

    private <T> ResponseEntity<SingleResponse<T>> withEtag(
            T body,
            String entityType,
            UUID id,
            long version,
            HttpServletRequest request) {
        HttpHeaders headers = new HttpHeaders();
        headers.setETag(etags.etag(entityType, id, version));
        return ResponseEntity.ok()
                .headers(headers)
                .body(SingleResponse.of(body, requestId(request)));
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

    private static long asLong(Object value) {
        if (value == null) return 0L;
        if (value instanceof Number number) return number.longValue();
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException exception) {
            return 0L;
        }
    }

    private static ImportRunResponse mapImportRun(Map<String, Object> row) {
        return new ImportRunResponse(
                (UUID) row.get("id"),
                String.valueOf(row.getOrDefault("status", "RUNNING")),
                asLong(row.get("processed_rows")),
                asLong(row.get("successful_rows")),
                asLong(row.get("failed_rows")),
                null);
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
