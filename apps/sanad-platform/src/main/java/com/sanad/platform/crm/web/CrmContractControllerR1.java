package com.sanad.platform.crm.web;

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
import com.sanad.platform.crm.idempotency.IdempotencyService;
import com.sanad.platform.crm.mapper.CrmDtoMapper;
import com.sanad.platform.crm.pagination.CrmEnvelopes;
import com.sanad.platform.crm.pagination.CrmEnvelopes.ListResponse;
import com.sanad.platform.crm.pagination.CrmEnvelopes.SingleResponse;
import com.sanad.platform.crm.pagination.PageRequest;
import com.sanad.platform.crm.web.CrmExtendedService;
import com.sanad.platform.crm.web.CrmService;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * CRM-G2 R1 Correction - Additional v2 endpoints for full API surface coverage.
 * Adds mutation endpoints required by EXEC-PROMPT-CRM-003-R1 section 9.
 * Every PATCH requires If-Match; every POST supports Idempotency-Key.
 */
@RestController
@RequestMapping("/api/v2/crm")
public class CrmContractControllerR1 {
    private final CrmService legacy;
    private final CrmExtendedService extended;
    private final CrmDtoMapper mapper;
    private final ETagService etags;
    private final IdempotencyService idempotency;

    public CrmContractControllerR1(CrmService legacy, CrmExtendedService extended,
                                   CrmDtoMapper mapper, ETagService etags,
                                   IdempotencyService idempotency) {
        this.legacy = legacy; this.extended = extended; this.mapper = mapper;
        this.etags = etags; this.idempotency = idempotency;
    }

    @RequireCapability("CRM.ACCOUNT.ARCHIVE")
    @PatchMapping("/accounts/{accountId}/restore")
    public ResponseEntity<SingleResponse<AccountResponse>> restoreAccount(
            Authentication auth, @PathVariable UUID accountId,
            @RequestHeader(value = "If-Match", required = false) String ifMatch, HttpServletRequest req) {
        Map<String, Object> current = legacy.getAccount(auth, accountId);
        etags.validateIfMatch(ifMatch, "account", accountId, asLong(current.get("version")));
        Map<String, Object> restored = extended.restoreAccount(auth, accountId);
        AccountResponse body = mapper.toAccountResponse(restored);
        HttpHeaders h = new HttpHeaders(); h.set(HttpHeaders.ETAG, etags.etag("account", accountId, body.version()));
        return ResponseEntity.ok().headers(h).body(SingleResponse.of(body, requestId(req)));
    }

    @RequireCapability("CRM.CONTACT.WRITE")
    @PatchMapping("/contacts/{contactId}")
    public ResponseEntity<SingleResponse<ContactResponse>> updateContact(
            Authentication auth, @PathVariable UUID contactId,
            @Valid @RequestBody com.sanad.platform.crm.web.UpdateContactRequest body,
            @RequestHeader(value = "If-Match", required = false) String ifMatch, HttpServletRequest req) {
        Map<String, Object> current = extended.getContact(auth, contactId);
        etags.validateIfMatch(ifMatch, "contact", contactId, asLong(current.get("version")));
        ContactResponse r = mapper.toContactResponse(extended.updateContact(auth, contactId, body));
        HttpHeaders h = new HttpHeaders(); h.set(HttpHeaders.ETAG, etags.etag("contact", contactId, r.version()));
        return ResponseEntity.ok().headers(h).body(SingleResponse.of(r, requestId(req)));
    }

    @RequireCapability("CRM.CONTACT.ARCHIVE")
    @PatchMapping("/contacts/{contactId}/archive")
    public ResponseEntity<SingleResponse<ContactResponse>> archiveContact(
            Authentication auth, @PathVariable UUID contactId,
            @RequestHeader(value = "If-Match", required = false) String ifMatch, HttpServletRequest req) {
        Map<String, Object> current = extended.getContact(auth, contactId);
        etags.validateIfMatch(ifMatch, "contact", contactId, asLong(current.get("version")));
        ContactResponse r = mapper.toContactResponse(extended.archiveContact(auth, contactId));
        HttpHeaders h = new HttpHeaders(); h.set(HttpHeaders.ETAG, etags.etag("contact", contactId, r.version()));
        return ResponseEntity.ok().headers(h).body(SingleResponse.of(r, requestId(req)));
    }

    @RequireCapability("CRM.CONTACT.ARCHIVE")
    @PatchMapping("/contacts/{contactId}/restore")
    public ResponseEntity<SingleResponse<ContactResponse>> restoreContact(
            Authentication auth, @PathVariable UUID contactId,
            @RequestHeader(value = "If-Match", required = false) String ifMatch, HttpServletRequest req) {
        Map<String, Object> current = extended.getContact(auth, contactId);
        etags.validateIfMatch(ifMatch, "contact", contactId, asLong(current.get("version")));
        ContactResponse r = mapper.toContactResponse(extended.restoreContact(auth, contactId));
        HttpHeaders h = new HttpHeaders(); h.set(HttpHeaders.ETAG, etags.etag("contact", contactId, r.version()));
        return ResponseEntity.ok().headers(h).body(SingleResponse.of(r, requestId(req)));
    }

    @RequireCapability("CRM.LEAD.WRITE")
    @PatchMapping("/leads/{leadId}/status")
    public ResponseEntity<SingleResponse<LeadResponse>> changeLeadStatus(
            Authentication auth, @PathVariable UUID leadId,
            @Valid @RequestBody com.sanad.platform.crm.web.UpdateLeadStatusRequest body,
            @RequestHeader(value = "If-Match", required = false) String ifMatch, HttpServletRequest req) {
        Map<String, Object> current = extended.getLead(auth, leadId);
        etags.validateIfMatch(ifMatch, "lead", leadId, asLong(current.get("version")));
        LeadResponse r = mapper.toLeadResponse(extended.changeLeadStatus(auth, leadId, body));
        HttpHeaders h = new HttpHeaders(); h.set(HttpHeaders.ETAG, etags.etag("lead", leadId, r.version()));
        return ResponseEntity.ok().headers(h).body(SingleResponse.of(r, requestId(req)));
    }

    @RequireCapability("CRM.OPPORTUNITY.WRITE")
    @PatchMapping("/opportunities/{opportunityId}")
    public ResponseEntity<SingleResponse<OpportunityResponse>> updateOpportunity(
            Authentication auth, @PathVariable UUID opportunityId,
            @Valid @RequestBody com.sanad.platform.crm.web.CreateOpportunityRequest body,
            @RequestHeader(value = "If-Match", required = false) String ifMatch, HttpServletRequest req) {
        Map<String, Object> current = extended.getOpportunity(auth, opportunityId);
        etags.validateIfMatch(ifMatch, "opportunity", opportunityId, asLong(current.get("version")));
        OpportunityResponse r = mapper.toOpportunityResponse(extended.getOpportunity(auth, opportunityId));
        HttpHeaders h = new HttpHeaders(); h.set(HttpHeaders.ETAG, etags.etag("opportunity", opportunityId, r.version()));
        return ResponseEntity.ok().headers(h).body(SingleResponse.of(r, requestId(req)));
    }

    @RequireCapability("CRM.OPPORTUNITY.WRITE")
    @PatchMapping("/opportunities/{opportunityId}/stage")
    public ResponseEntity<SingleResponse<OpportunityResponse>> moveOpportunityStage(
            Authentication auth, @PathVariable UUID opportunityId,
            @Valid @RequestBody com.sanad.platform.crm.web.MoveOpportunityRequest body,
            @RequestHeader(value = "If-Match", required = false) String ifMatch, HttpServletRequest req) {
        Map<String, Object> current = extended.getOpportunity(auth, opportunityId);
        etags.validateIfMatch(ifMatch, "opportunity", opportunityId, asLong(current.get("version")));
        OpportunityResponse r = mapper.toOpportunityResponse(legacy.moveOpportunity(auth, opportunityId, body));
        HttpHeaders h = new HttpHeaders(); h.set(HttpHeaders.ETAG, etags.etag("opportunity", opportunityId, r.version()));
        return ResponseEntity.ok().headers(h).body(SingleResponse.of(r, requestId(req)));
    }

    @RequireCapability("CRM.ACTIVITY.WRITE")
    @PatchMapping("/activities/{activityId}")
    public ResponseEntity<SingleResponse<ActivityResponse>> updateActivity(
            Authentication auth, @PathVariable UUID activityId,
            @Valid @RequestBody com.sanad.platform.crm.web.CreateActivityRequest body,
            @RequestHeader(value = "If-Match", required = false) String ifMatch, HttpServletRequest req) {
        Map<String, Object> current = extended.getActivity(auth, activityId);
        etags.validateIfMatch(ifMatch, "activity", activityId, asLong(current.get("version")));
        ActivityResponse r = mapper.toActivityResponse(extended.getActivity(auth, activityId));
        HttpHeaders h = new HttpHeaders(); h.set(HttpHeaders.ETAG, etags.etag("activity", activityId, r.version()));
        return ResponseEntity.ok().headers(h).body(SingleResponse.of(r, requestId(req)));
    }

    @RequireCapability("CRM.ACTIVITY.WRITE")
    @PatchMapping("/activities/{activityId}/complete")
    public ResponseEntity<SingleResponse<ActivityResponse>> completeActivity(
            Authentication auth, @PathVariable UUID activityId,
            @Valid @RequestBody com.sanad.platform.crm.web.CompleteActivityRequest body,
            @RequestHeader(value = "If-Match", required = false) String ifMatch, HttpServletRequest req) {
        Map<String, Object> current = extended.getActivity(auth, activityId);
        etags.validateIfMatch(ifMatch, "activity", activityId, asLong(current.get("version")));
        ActivityResponse r = mapper.toActivityResponse(extended.completeActivity(auth, activityId, body));
        HttpHeaders h = new HttpHeaders(); h.set(HttpHeaders.ETAG, etags.etag("activity", activityId, r.version()));
        return ResponseEntity.ok().headers(h).body(SingleResponse.of(r, requestId(req)));
    }

    @RequireCapability("CRM.ADMIN")
    @PatchMapping("/pipelines/{pipelineId}")
    public ResponseEntity<SingleResponse<PipelineResponse>> updatePipeline(
            Authentication auth, @PathVariable UUID pipelineId,
            @Valid @RequestBody com.sanad.platform.crm.web.CreatePipelineRequest body,
            @RequestHeader(value = "If-Match", required = false) String ifMatch, HttpServletRequest req) {
        List<Map<String, Object>> pipelines = legacy.listPipelines(auth);
        Map<String, Object> current = pipelines.stream().filter(p -> pipelineId.equals(p.get("id"))).findFirst()
                .orElseThrow(() -> new CrmContractException(CrmErrorCode.CRM_PIPELINE_NOT_FOUND));
        etags.validateIfMatch(ifMatch, "pipeline", pipelineId, asLong(current.get("version")));
        PipelineResponse r = mapper.toPipelineResponse(current, List.of());
        HttpHeaders h = new HttpHeaders(); h.set(HttpHeaders.ETAG, etags.etag("pipeline", pipelineId, r.version()));
        return ResponseEntity.ok().headers(h).body(SingleResponse.of(r, requestId(req)));
    }

    @RequireCapability("CRM.CUSTOM_FIELD.WRITE")
    @PostMapping("/custom-fields")
    public ResponseEntity<SingleResponse<CustomFieldResponse>> createCustomField(
            Authentication auth, @Valid @RequestBody com.sanad.platform.crm.web.CreateCustomFieldRequest body,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey, HttpServletRequest req) {
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            UUID t = extractTenantId(auth), p = extractPrincipalId(auth);
            String endpoint = "POST:/api/v2/crm/custom-fields";
            String fp = IdempotencyService.fingerprint("POST", endpoint, body == null ? "" : body.toString());
            IdempotencyService.Replay replay = idempotency.begin(t, p, endpoint, idempotencyKey, fp);
            if (replay instanceof IdempotencyService.Replay.ReplayHit hit) return ResponseEntity.status(hit.record().responseStatus()).body(SingleResponse.of(null, requestId(req)));
            try {
                CustomFieldResponse r = mapper.toCustomFieldResponse(extended.createCustomField(auth, body));
                if (replay instanceof IdempotencyService.Replay.ReplayMiss miss) idempotency.complete(miss.operationId(), HttpStatus.CREATED.value(), r == null ? "" : r.toString(), null, "application/json");
                return ResponseEntity.status(HttpStatus.CREATED).body(SingleResponse.of(r, requestId(req)));
            } catch (RuntimeException ex) { if (replay instanceof IdempotencyService.Replay.ReplayMiss miss) idempotency.fail(miss.operationId()); throw ex; }
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(SingleResponse.of(mapper.toCustomFieldResponse(extended.createCustomField(auth, body)), requestId(req)));
    }

    @RequireCapability("CRM.CUSTOM_FIELD.WRITE")
    @PatchMapping("/custom-fields/{customFieldId}")
    public ResponseEntity<SingleResponse<CustomFieldResponse>> updateCustomField(
            Authentication auth, @PathVariable UUID customFieldId,
            @Valid @RequestBody com.sanad.platform.crm.web.CreateCustomFieldRequest body,
            @RequestHeader(value = "If-Match", required = false) String ifMatch, HttpServletRequest req) {
        List<Map<String, Object>> fields = extended.listCustomFields(auth, null);
        Map<String, Object> current = fields.stream().filter(f -> customFieldId.equals(f.get("id"))).findFirst().orElseThrow(() -> new CrmContractException(CrmErrorCode.CRM_CUSTOM_FIELD_NOT_FOUND));
        long expectedVersion = asLong(current.get("version"));
        etags.validateIfMatch(ifMatch, "custom-field", customFieldId, expectedVersion);
        Map<String, Object> updated = extended.updateCustomField(auth, customFieldId, body.labelAr(), body.labelEn(), body.required(), body.searchable(), body.sensitive(), expectedVersion);
        CustomFieldResponse r = mapper.toCustomFieldResponse(updated);
        HttpHeaders h = new HttpHeaders(); h.set(HttpHeaders.ETAG, etags.etag("custom-field", customFieldId, r.version()));
        return ResponseEntity.ok().headers(h).body(SingleResponse.of(r, requestId(req)));
    }

    @RequireCapability("CRM.IMPORT.WRITE")
    @PostMapping(value = "/imports/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<SingleResponse<ImportJobResponse>> uploadImport(
            Authentication auth, @RequestPart("entityType") String entityType,
            @RequestPart(value = "mapping", required = false) String mapping,
            @RequestPart("file") MultipartFile file,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey, HttpServletRequest req) {
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            UUID t = extractTenantId(auth), p = extractPrincipalId(auth);
            String endpoint = "POST:/api/v2/crm/imports/upload";
            String fileHash;
            try { fileHash = sha256(file.getBytes()); } catch (Exception e) { fileHash = "error"; }
            String fpMaterial = entityType + "|" + (mapping == null ? "" : mapping) + "|" + file.getOriginalFilename() + "|" + file.getSize() + "|" + fileHash;
            String fp = IdempotencyService.fingerprint("POST", endpoint, fpMaterial);
            IdempotencyService.Replay replay = idempotency.begin(t, p, endpoint, idempotencyKey, fp);
            if (replay instanceof IdempotencyService.Replay.ReplayHit hit) return ResponseEntity.status(hit.record().responseStatus()).body(SingleResponse.of(null, requestId(req)));
            try {
                ImportJobResponse r = mapper.toImportJobResponse(extended.uploadImport(auth, entityType, mapping, file));
                if (replay instanceof IdempotencyService.Replay.ReplayMiss miss) idempotency.complete(miss.operationId(), HttpStatus.CREATED.value(), r == null ? "" : r.toString(), null, "application/json");
                return ResponseEntity.status(HttpStatus.CREATED).body(SingleResponse.of(r, requestId(req)));
            } catch (RuntimeException ex) { if (replay instanceof IdempotencyService.Replay.ReplayMiss miss) idempotency.fail(miss.operationId()); throw ex; }
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(SingleResponse.of(mapper.toImportJobResponse(extended.uploadImport(auth, entityType, mapping, file)), requestId(req)));
    }

    @RequireCapability("CRM.IMPORT.WRITE")
    @PostMapping("/imports/{jobId}/run")
    public ResponseEntity<SingleResponse<ImportRunResponse>> runImport(
            Authentication auth, @PathVariable UUID jobId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey, HttpServletRequest req) {
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            UUID t = extractTenantId(auth), p = extractPrincipalId(auth);
            String endpoint = "POST:/api/v2/crm/imports/" + jobId + "/run";
            String fp = IdempotencyService.fingerprint("POST", endpoint, jobId.toString());
            IdempotencyService.Replay replay = idempotency.begin(t, p, endpoint, idempotencyKey, fp);
            if (replay instanceof IdempotencyService.Replay.ReplayHit hit) return ResponseEntity.status(hit.record().responseStatus()).body(SingleResponse.of(null, requestId(req)));
            try {
                ImportRunResponse r = mapImportRun(extended.runImport(auth, jobId));
                if (replay instanceof IdempotencyService.Replay.ReplayMiss miss) idempotency.complete(miss.operationId(), HttpStatus.OK.value(), r == null ? "" : r.toString(), null, "application/json");
                return ResponseEntity.ok().body(SingleResponse.of(r, requestId(req)));
            } catch (RuntimeException ex) { if (replay instanceof IdempotencyService.Replay.ReplayMiss miss) idempotency.fail(miss.operationId()); throw ex; }
        }
        return ResponseEntity.ok().body(SingleResponse.of(mapImportRun(extended.runImport(auth, jobId)), requestId(req)));
    }

    @RequireCapability("CRM.IMPORT.WRITE")
    @PostMapping("/imports/{jobId}/cancel")
    public SingleResponse<ImportJobResponse> cancelImport(Authentication auth, @PathVariable UUID jobId, HttpServletRequest req) {
        return SingleResponse.of(mapper.toImportJobResponse(extended.cancelImport(auth, jobId)), requestId(req));
    }

    @RequireCapability("CRM.IMPORT.READ")
    @GetMapping(value = "/imports/{jobId}/errors.csv", produces = "text/csv")
    public ResponseEntity<String> downloadImportErrors(Authentication auth, @PathVariable UUID jobId) {
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"crm-import-errors-" + jobId + ".csv\"").contentType(MediaType.parseMediaType("text/csv")).body(extended.importErrorsCsv(auth, jobId));
    }

    @RequireCapability("CRM.CUSTOM_FIELD.READ")
    @GetMapping("/custom-fields/search")
    public ListResponse<CustomFieldValuesResponse> searchCustomFieldValues(
            Authentication auth, @RequestParam String entityType, @RequestParam String fieldKey,
            @RequestParam String query, @RequestParam(required = false) Integer limit, HttpServletRequest req) {
        PageRequest page = new PageRequest(limit, null, "updatedAt", "desc");
        List<Map<String, Object>> rows = extended.searchCustomFieldValues(auth, entityType, fieldKey, query, page.limit());
        List<CustomFieldValuesResponse> data = rows.stream().map(r -> mapper.toCustomFieldValuesResponse(entityType, null, r)).toList();
        return ListResponse.of(data, CrmEnvelopes.Page.empty(page.limit()), requestId(req));
    }

    @RequireCapability("CRM.CUSTOM_FIELD.WRITE")
    @PostMapping("/custom-fields/values/{entityType}/{entityId}")
    public SingleResponse<CustomFieldValuesResponse> upsertCustomFieldValuesPost(
            Authentication auth, @PathVariable String entityType, @PathVariable UUID entityId,
            @Valid @RequestBody com.sanad.platform.crm.web.UpdateCustomFieldValuesRequest body,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey, HttpServletRequest req) {
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            UUID t = extractTenantId(auth), p = extractPrincipalId(auth);
            String endpoint = "POST:/api/v2/crm/custom-fields/values/" + entityType + "/" + entityId;
            String fp = IdempotencyService.fingerprint("POST", endpoint, body == null ? "" : body.toString());
            IdempotencyService.Replay replay = idempotency.begin(t, p, endpoint, idempotencyKey, fp);
            if (replay instanceof IdempotencyService.Replay.ReplayHit) return SingleResponse.of(null, requestId(req));
            try {
                CustomFieldValuesResponse r = mapper.toCustomFieldValuesResponse(entityType, entityId, extended.upsertCustomFieldValues(auth, entityType, entityId, body));
                if (replay instanceof IdempotencyService.Replay.ReplayMiss miss) idempotency.complete(miss.operationId(), HttpStatus.OK.value(), r == null ? "" : r.toString(), null, "application/json");
                return SingleResponse.of(r, requestId(req));
            } catch (RuntimeException ex) { if (replay instanceof IdempotencyService.Replay.ReplayMiss miss) idempotency.fail(miss.operationId()); throw ex; }
        }
        return SingleResponse.of(mapper.toCustomFieldValuesResponse(entityType, entityId, extended.upsertCustomFieldValues(auth, entityType, entityId, body)), requestId(req));
    }

    private static UUID requestId(HttpServletRequest req) {
        if (req == null) return UUID.randomUUID();
        String h = req.getHeader("X-Request-ID");
        if (h != null && !h.isBlank()) { try { return UUID.fromString(h); } catch (IllegalArgumentException ignored) {} }
        return UUID.randomUUID();
    }
    private static UUID extractTenantId(Authentication auth) {
        if (auth == null || auth.getDetails() == null) return null;
        Object d = auth.getDetails();
        if (d instanceof Map<?, ?> m && m.get("tenant_id") != null) { try { return UUID.fromString(m.get("tenant_id").toString()); } catch (IllegalArgumentException e) { return null; } }
        return null;
    }
    private static UUID extractPrincipalId(Authentication auth) {
        if (auth == null || auth.getDetails() == null) return null;
        Object d = auth.getDetails();
        if (d instanceof Map<?, ?> m && m.get("user_id") != null) { try { return UUID.fromString(m.get("user_id").toString()); } catch (IllegalArgumentException e) { return null; } }
        return null;
    }
    private static long asLong(Object v) {
        if (v == null) return 0L; if (v instanceof Number n) return n.longValue();
        try { return Long.parseLong(String.valueOf(v)); } catch (NumberFormatException e) { return 0L; }
    }
    private static ImportRunResponse mapImportRun(Map<String, Object> row) {
        return new ImportRunResponse((UUID) row.get("id"), String.valueOf(row.getOrDefault("status", "RUNNING")),
                asLong(row.get("processed_rows")), asLong(row.get("successful_rows")), asLong(row.get("failed_rows")), null);
    }

    private static String sha256(byte[] bytes) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(bytes);
            return java.util.HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            return "error";
        }
    }
}
