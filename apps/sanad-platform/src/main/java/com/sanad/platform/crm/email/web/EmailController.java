package com.sanad.platform.crm.email.web;

import com.sanad.platform.crm.email.application.EmailUseCases;
import com.sanad.platform.crm.email.domain.EmailAddress;
import com.sanad.platform.crm.email.domain.EmailLogPort.EmailLogEntry;
import com.sanad.platform.crm.email.domain.EmailMessage;
import com.sanad.platform.crm.email.domain.EmailSendResult;
import com.sanad.platform.crm.email.domain.TemplateVariables;
import com.sanad.platform.crm.pagination.CrmEnvelopes;
import com.sanad.platform.security.authorization.RequireCapability;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * V2 REST controller for CRM email operations.
 * <p>
 * Mounted under {@code /api/v2/crm/email}.
 * Returns {@link CrmEnvelopes.SingleResponse} / {@link CrmEnvelopes.ListResponse}.
 * <p>
 * Capabilities enforced via {@link RequireCapability}:
 *   - {@code CRM.EMAIL.READ} for GET endpoints
 *   - {@code CRM.EMAIL.WRITE} for POST endpoints
 */
@RestController
@RequestMapping("/api/v2/crm/email")
public class EmailController {

    private final EmailUseCases emailUseCases;

    public EmailController(EmailUseCases emailUseCases) {
        this.emailUseCases = emailUseCases;
    }

    @RequireCapability("CRM.EMAIL.WRITE")
    @PostMapping("/send")
    public ResponseEntity<CrmEnvelopes.SingleResponse<Map<String, Object>>> sendEmail(
            Authentication authentication,
            @Valid @RequestBody EmailModels.SendEmailRequest request) {
        UUID tenantId = tenantId(authentication);
        UUID actorId = userId(authentication);

        EmailMessage message = EmailMessage.builder()
                .from(new EmailAddress(request.from()))
                .to(request.to().stream().map(EmailAddress::new).toArray(EmailAddress[]::new))
                .cc(request.cc() != null ? request.cc().stream().map(EmailAddress::new).toArray(EmailAddress[]::new) : new EmailAddress[0])
                .bcc(request.bcc() != null ? request.bcc().stream().map(EmailAddress::new).toArray(EmailAddress[]::new) : new EmailAddress[0])
                .subject(request.subject())
                .textBody(request.textBody())
                .htmlBody(request.htmlBody())
                .templateName(request.templateName())
                .templateVariables(request.templateVariables() != null ? TemplateVariables.of(request.templateVariables()) : TemplateVariables.EMPTY)
                .tenantId(tenantId.toString())
                .relatedEntityType(request.relatedEntityType())
                .relatedEntityId(request.relatedEntityId() != null ? request.relatedEntityId().toString() : null)
                .metadata(request.metadata() != null ? request.metadata() : Map.of())
                .build();

        EmailSendResult result = emailUseCases.send(tenantId, actorId, message);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("emailLogId", result.emailLogId());
        data.put("status", result.status());
        data.put("provider", result.provider());
        data.put("sentAt", result.sentAt() != null ? result.sentAt().toString() : null);

        UUID requestId = UUID.randomUUID();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(CrmEnvelopes.SingleResponse.of(data, requestId));
    }

    @RequireCapability("CRM.EMAIL.READ")
    @GetMapping("/logs")
    public CrmEnvelopes.ListResponse<Map<String, Object>> listLogs(
            Authentication authentication,
            @RequestParam(defaultValue = "50") int limit) {
        UUID tenantId = tenantId(authentication);
        int safeLimit = Math.max(1, Math.min(limit, 200));
        List<EmailLogEntry> logs = emailUseCases.listLogs(tenantId, safeLimit);
        UUID requestId = UUID.randomUUID();
        return CrmEnvelopes.ListResponse.of(
                logs.stream().map(this::toLogRow).toList(),
                CrmEnvelopes.Page.empty(safeLimit),
                requestId);
    }

    @RequireCapability("CRM.EMAIL.READ")
    @GetMapping("/logs/{logId}")
    public CrmEnvelopes.SingleResponse<Map<String, Object>> getLog(
            Authentication authentication,
            @PathVariable UUID logId) {
        UUID tenantId = tenantId(authentication);
        EmailLogEntry entry = emailUseCases.findById(tenantId, logId);
        UUID requestId = UUID.randomUUID();
        return CrmEnvelopes.SingleResponse.of(toLogRow(entry), requestId);
    }

    @RequireCapability("CRM.EMAIL.READ")
    @GetMapping("/logs/entity/{entityType}/{entityId}")
    public CrmEnvelopes.ListResponse<Map<String, Object>> listLogsByEntity(
            Authentication authentication,
            @PathVariable String entityType,
            @PathVariable UUID entityId) {
        UUID tenantId = tenantId(authentication);
        List<EmailLogEntry> logs = emailUseCases.findByRelatedEntity(
                tenantId, entityType, entityId.toString());
        UUID requestId = UUID.randomUUID();
        return CrmEnvelopes.ListResponse.of(
                logs.stream().map(this::toLogRow).toList(),
                CrmEnvelopes.Page.empty(logs.size()),
                requestId);
    }

    @RequireCapability("CRM.EMAIL.READ")
    @GetMapping("/status")
    public CrmEnvelopes.SingleResponse<Map<String, Object>> getProviderStatus(
            Authentication authentication) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("provider", emailUseCases.getProviderName());
        data.put("available", emailUseCases.isProviderAvailable());
        UUID requestId = UUID.randomUUID();
        return CrmEnvelopes.SingleResponse.of(data, requestId);
    }

    private Map<String, Object> toLogRow(EmailLogEntry entry) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", entry.id());
        row.put("from", entry.fromAddress());
        row.put("to", entry.toAddress());
        row.put("subject", entry.subject());
        row.put("status", entry.status());
        row.put("provider", entry.provider());
        row.put("relatedEntityType", entry.relatedEntityType());
        row.put("relatedEntityId", entry.relatedEntityId());
        row.put("templateName", entry.templateName());
        row.put("sentAt", entry.sentAt() != null ? entry.sentAt().toString() : null);
        row.put("openedAt", entry.openedAt() != null ? entry.openedAt().toString() : null);
        row.put("clickedAt", entry.clickedAt() != null ? entry.clickedAt().toString() : null);
        row.put("createdAt", entry.createdAt() != null ? entry.createdAt().toString() : null);
        return row;
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
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "Authenticated CRM context is required");
        }
        try {
            return UUID.fromString(details.get(key).toString());
        } catch (IllegalArgumentException exception) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "Invalid authenticated CRM context", exception);
        }
    }
}
