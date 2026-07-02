package com.sanad.platform.crm.web;

import com.sanad.platform.security.authorization.RequireCapability;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/crm")
public class CrmController {
    private final CrmService crm;
    private final CrmExtendedService extended;

    public CrmController(CrmService crm, CrmExtendedService extended) {
        this.crm = crm;
        this.extended = extended;
    }

    @RequireCapability("CRM.ACCOUNT.READ")
    @GetMapping("/dashboard")
    public Map<String, Object> dashboard(Authentication authentication) {
        return extended.dashboard(authentication);
    }

    @RequireCapability("CRM.ACCOUNT.WRITE")
    @PostMapping("/accounts")
    public ResponseEntity<Map<String, Object>> createAccount(
            Authentication authentication,
            @Valid @RequestBody CreateAccountRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(crm.createAccount(authentication, request));
    }

    @RequireCapability("CRM.ACCOUNT.READ")
    @GetMapping("/accounts")
    public List<Map<String, Object>> listAccounts(
            Authentication authentication,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(required = false) String search) {
        return crm.listAccounts(authentication, limit, search);
    }

    @RequireCapability("CRM.ACCOUNT.READ")
    @GetMapping("/accounts/{accountId}")
    public Map<String, Object> getAccount(Authentication authentication, @PathVariable UUID accountId) {
        return crm.getAccount(authentication, accountId);
    }

    @RequireCapability("CRM.ACCOUNT.READ")
    @GetMapping("/accounts/{accountId}/customer-360")
    public Map<String, Object> customer360(Authentication authentication, @PathVariable UUID accountId) {
        return extended.customer360(authentication, accountId);
    }

    @RequireCapability("CRM.ACCOUNT.WRITE")
    @PatchMapping("/accounts/{accountId}")
    public Map<String, Object> updateAccount(
            Authentication authentication,
            @PathVariable UUID accountId,
            @Valid @RequestBody UpdateAccountRequest request) {
        return crm.updateAccount(authentication, accountId, request);
    }

    @RequireCapability("CRM.ACCOUNT.ARCHIVE")
    @PatchMapping("/accounts/{accountId}/archive")
    public Map<String, Object> archiveAccount(Authentication authentication, @PathVariable UUID accountId) {
        return crm.archiveAccount(authentication, accountId);
    }

    @RequireCapability("CRM.ACCOUNT.ARCHIVE")
    @PatchMapping("/accounts/{accountId}/restore")
    public Map<String, Object> restoreAccount(Authentication authentication, @PathVariable UUID accountId) {
        return extended.restoreAccount(authentication, accountId);
    }

    @RequireCapability("CRM.CONTACT.WRITE")
    @PostMapping("/contacts")
    public ResponseEntity<Map<String, Object>> createContact(
            Authentication authentication,
            @Valid @RequestBody CreateContactRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(crm.createContact(authentication, request));
    }

    @RequireCapability("CRM.CONTACT.READ")
    @GetMapping("/contacts")
    public List<Map<String, Object>> listContacts(
            Authentication authentication,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(required = false) UUID accountId,
            @RequestParam(required = false) String search) {
        return crm.listContacts(authentication, limit, accountId, search);
    }

    @RequireCapability("CRM.CONTACT.READ")
    @GetMapping("/contacts/{contactId}")
    public Map<String, Object> getContact(Authentication authentication, @PathVariable UUID contactId) {
        return extended.getContact(authentication, contactId);
    }

    @RequireCapability("CRM.CONTACT.WRITE")
    @PatchMapping("/contacts/{contactId}")
    public Map<String, Object> updateContact(
            Authentication authentication,
            @PathVariable UUID contactId,
            @Valid @RequestBody UpdateContactRequest request) {
        return extended.updateContact(authentication, contactId, request);
    }

    @RequireCapability("CRM.CONTACT.ARCHIVE")
    @PatchMapping("/contacts/{contactId}/archive")
    public Map<String, Object> archiveContact(Authentication authentication, @PathVariable UUID contactId) {
        return extended.archiveContact(authentication, contactId);
    }

    @RequireCapability("CRM.CONTACT.ARCHIVE")
    @PatchMapping("/contacts/{contactId}/restore")
    public Map<String, Object> restoreContact(Authentication authentication, @PathVariable UUID contactId) {
        return extended.restoreContact(authentication, contactId);
    }

    @RequireCapability("CRM.LEAD.WRITE")
    @PostMapping("/leads")
    public ResponseEntity<Map<String, Object>> createLead(
            Authentication authentication,
            @Valid @RequestBody CreateLeadRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(crm.createLead(authentication, request));
    }

    @RequireCapability("CRM.LEAD.READ")
    @GetMapping("/leads")
    public List<Map<String, Object>> listLeads(
            Authentication authentication,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(required = false) String status) {
        return crm.listLeads(authentication, limit, status);
    }

    @RequireCapability("CRM.LEAD.READ")
    @GetMapping("/leads/{leadId}")
    public Map<String, Object> getLead(Authentication authentication, @PathVariable UUID leadId) {
        return extended.getLead(authentication, leadId);
    }

    @RequireCapability("CRM.LEAD.WRITE")
    @PatchMapping("/leads/{leadId}/status")
    public Map<String, Object> changeLeadStatus(
            Authentication authentication,
            @PathVariable UUID leadId,
            @Valid @RequestBody UpdateLeadStatusRequest request) {
        return extended.changeLeadStatus(authentication, leadId, request);
    }

    @RequireCapability("CRM.LEAD.CONVERT")
    @PostMapping("/leads/{leadId}/convert")
    public Map<String, Object> convertLead(
            Authentication authentication,
            @PathVariable UUID leadId,
            @Valid @RequestBody ConvertLeadRequest request) {
        return crm.convertLead(authentication, leadId, request);
    }

    @RequireCapability("CRM.ADMIN")
    @PostMapping("/pipelines")
    public ResponseEntity<Map<String, Object>> createPipeline(
            Authentication authentication,
            @Valid @RequestBody CreatePipelineRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(crm.createPipeline(authentication, request));
    }

    @RequireCapability("CRM.OPPORTUNITY.READ")
    @GetMapping("/pipelines")
    public List<Map<String, Object>> listPipelines(Authentication authentication) {
        return crm.listPipelines(authentication);
    }

    @RequireCapability("CRM.OPPORTUNITY.READ")
    @GetMapping("/pipelines/{pipelineId}/stages")
    public List<Map<String, Object>> listPipelineStages(Authentication authentication, @PathVariable UUID pipelineId) {
        return extended.listPipelineStages(authentication, pipelineId);
    }

    @RequireCapability("CRM.OPPORTUNITY.WRITE")
    @PostMapping("/opportunities")
    public ResponseEntity<Map<String, Object>> createOpportunity(
            Authentication authentication,
            @Valid @RequestBody CreateOpportunityRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(crm.createOpportunity(authentication, request));
    }

    @RequireCapability("CRM.OPPORTUNITY.READ")
    @GetMapping("/opportunities")
    public List<Map<String, Object>> listOpportunities(
            Authentication authentication,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(required = false) UUID accountId) {
        return crm.listOpportunities(authentication, limit, accountId);
    }

    @RequireCapability("CRM.OPPORTUNITY.READ")
    @GetMapping("/opportunities/{opportunityId}")
    public Map<String, Object> getOpportunity(Authentication authentication, @PathVariable UUID opportunityId) {
        return extended.getOpportunity(authentication, opportunityId);
    }

    @RequireCapability("CRM.OPPORTUNITY.WRITE")
    @PatchMapping("/opportunities/{opportunityId}/stage")
    public Map<String, Object> moveOpportunity(
            Authentication authentication,
            @PathVariable UUID opportunityId,
            @Valid @RequestBody MoveOpportunityRequest request) {
        return crm.moveOpportunity(authentication, opportunityId, request);
    }

    @RequireCapability("CRM.ACTIVITY.WRITE")
    @PostMapping("/activities")
    public ResponseEntity<Map<String, Object>> createActivity(
            Authentication authentication,
            @Valid @RequestBody CreateActivityRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(crm.createActivity(authentication, request));
    }

    @RequireCapability("CRM.ACTIVITY.READ")
    @GetMapping("/activities")
    public List<Map<String, Object>> listActivities(
            Authentication authentication,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(required = false) String relatedType,
            @RequestParam(required = false) UUID relatedId,
            @RequestParam(required = false) String status) {
        return extended.listActivities(authentication, limit, relatedType, relatedId, status);
    }

    @RequireCapability("CRM.ACTIVITY.READ")
    @GetMapping("/activities/{activityId}")
    public Map<String, Object> getActivity(Authentication authentication, @PathVariable UUID activityId) {
        return extended.getActivity(authentication, activityId);
    }

    @RequireCapability("CRM.ACTIVITY.WRITE")
    @PatchMapping("/activities/{activityId}/complete")
    public Map<String, Object> completeActivity(
            Authentication authentication,
            @PathVariable UUID activityId,
            @Valid @RequestBody CompleteActivityRequest request) {
        return extended.completeActivity(authentication, activityId, request);
    }

    @RequireCapability("CRM.ACTIVITY.READ")
    @GetMapping("/timeline/{subjectType}/{subjectId}")
    public List<Map<String, Object>> timeline(
            Authentication authentication,
            @PathVariable String subjectType,
            @PathVariable UUID subjectId,
            @RequestParam(defaultValue = "50") int limit) {
        return crm.timeline(authentication, subjectType, subjectId, limit);
    }

    @RequireCapability("CRM.ADMIN")
    @PostMapping("/imports")
    public ResponseEntity<Map<String, Object>> createImportJob(
            Authentication authentication,
            @Valid @RequestBody CreateImportJobRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(extended.createImportJob(authentication, request));
    }

    @RequireCapability("CRM.ADMIN")
    @GetMapping("/imports")
    public List<Map<String, Object>> listImportJobs(
            Authentication authentication,
            @RequestParam(defaultValue = "50") int limit) {
        return extended.listImportJobs(authentication, limit);
    }

    @RequireCapability("CRM.ADMIN")
    @PostMapping("/custom-fields")
    public ResponseEntity<Map<String, Object>> createCustomField(
            Authentication authentication,
            @Valid @RequestBody CreateCustomFieldRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(extended.createCustomField(authentication, request));
    }

    @RequireCapability("CRM.ACCOUNT.READ")
    @GetMapping("/custom-fields")
    public List<Map<String, Object>> listCustomFields(
            Authentication authentication,
            @RequestParam(required = false) String entityType) {
        return extended.listCustomFields(authentication, entityType);
    }
}
