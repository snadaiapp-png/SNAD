package com.sanad.platform.crm.web;

import com.sanad.platform.security.authorization.RequireCapability;
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

    public CrmController(CrmService crm) {
        this.crm = crm;
    }

    @RequireCapability("CRM.ACCOUNT.WRITE")
    @PostMapping("/accounts")
    public ResponseEntity<Map<String, Object>> createAccount(Authentication authentication, @RequestBody CreateAccountRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(crm.createAccount(authentication, request));
    }

    @RequireCapability("CRM.ACCOUNT.READ")
    @GetMapping("/accounts")
    public List<Map<String, Object>> listAccounts(Authentication authentication, @RequestParam(defaultValue = "50") int limit, @RequestParam(required = false) String search) {
        return crm.listAccounts(authentication, limit, search);
    }

    @RequireCapability("CRM.ACCOUNT.READ")
    @GetMapping("/accounts/{accountId}")
    public Map<String, Object> getAccount(Authentication authentication, @PathVariable UUID accountId) {
        return crm.getAccount(authentication, accountId);
    }

    @RequireCapability("CRM.ACCOUNT.WRITE")
    @PatchMapping("/accounts/{accountId}")
    public Map<String, Object> updateAccount(Authentication authentication, @PathVariable UUID accountId, @RequestBody UpdateAccountRequest request) {
        return crm.updateAccount(authentication, accountId, request);
    }

    @RequireCapability("CRM.ACCOUNT.ARCHIVE")
    @PatchMapping("/accounts/{accountId}/archive")
    public Map<String, Object> archiveAccount(Authentication authentication, @PathVariable UUID accountId) {
        return crm.archiveAccount(authentication, accountId);
    }

    @RequireCapability("CRM.CONTACT.WRITE")
    @PostMapping("/contacts")
    public ResponseEntity<Map<String, Object>> createContact(Authentication authentication, @RequestBody CreateContactRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(crm.createContact(authentication, request));
    }

    @RequireCapability("CRM.CONTACT.READ")
    @GetMapping("/contacts")
    public List<Map<String, Object>> listContacts(Authentication authentication, @RequestParam(defaultValue = "50") int limit, @RequestParam(required = false) UUID accountId, @RequestParam(required = false) String search) {
        return crm.listContacts(authentication, limit, accountId, search);
    }

    @RequireCapability("CRM.LEAD.WRITE")
    @PostMapping("/leads")
    public ResponseEntity<Map<String, Object>> createLead(Authentication authentication, @RequestBody CreateLeadRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(crm.createLead(authentication, request));
    }

    @RequireCapability("CRM.LEAD.READ")
    @GetMapping("/leads")
    public List<Map<String, Object>> listLeads(Authentication authentication, @RequestParam(defaultValue = "50") int limit, @RequestParam(required = false) String status) {
        return crm.listLeads(authentication, limit, status);
    }

    @RequireCapability("CRM.LEAD.CONVERT")
    @PostMapping("/leads/{leadId}/convert")
    public Map<String, Object> convertLead(Authentication authentication, @PathVariable UUID leadId, @RequestBody ConvertLeadRequest request) {
        return crm.convertLead(authentication, leadId, request);
    }

    @RequireCapability("CRM.ADMIN")
    @PostMapping("/pipelines")
    public ResponseEntity<Map<String, Object>> createPipeline(Authentication authentication, @RequestBody CreatePipelineRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(crm.createPipeline(authentication, request));
    }

    @RequireCapability("CRM.OPPORTUNITY.READ")
    @GetMapping("/pipelines")
    public List<Map<String, Object>> listPipelines(Authentication authentication) {
        return crm.listPipelines(authentication);
    }

    @RequireCapability("CRM.OPPORTUNITY.WRITE")
    @PostMapping("/opportunities")
    public ResponseEntity<Map<String, Object>> createOpportunity(Authentication authentication, @RequestBody CreateOpportunityRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(crm.createOpportunity(authentication, request));
    }

    @RequireCapability("CRM.OPPORTUNITY.READ")
    @GetMapping("/opportunities")
    public List<Map<String, Object>> listOpportunities(Authentication authentication, @RequestParam(defaultValue = "50") int limit, @RequestParam(required = false) UUID accountId) {
        return crm.listOpportunities(authentication, limit, accountId);
    }

    @RequireCapability("CRM.OPPORTUNITY.WRITE")
    @PatchMapping("/opportunities/{opportunityId}/stage")
    public Map<String, Object> moveOpportunity(Authentication authentication, @PathVariable UUID opportunityId, @RequestBody MoveOpportunityRequest request) {
        return crm.moveOpportunity(authentication, opportunityId, request);
    }

    @RequireCapability("CRM.ACTIVITY.WRITE")
    @PostMapping("/activities")
    public ResponseEntity<Map<String, Object>> createActivity(Authentication authentication, @RequestBody CreateActivityRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(crm.createActivity(authentication, request));
    }

    @RequireCapability("CRM.ACTIVITY.READ")
    @GetMapping("/timeline/{subjectType}/{subjectId}")
    public List<Map<String, Object>> timeline(Authentication authentication, @PathVariable String subjectType, @PathVariable UUID subjectId, @RequestParam(defaultValue = "50") int limit) {
        return crm.timeline(authentication, subjectType, subjectId, limit);
    }
}
