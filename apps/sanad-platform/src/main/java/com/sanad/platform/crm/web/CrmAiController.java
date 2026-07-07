package com.sanad.platform.crm.web;

import com.sanad.platform.crm.ai.CrmAiPolicyService;
import com.sanad.platform.crm.ai.DeterministicLeadScoringService;
import com.sanad.platform.security.authorization.RequireCapability;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Advisory-only CRM intelligence endpoints.
 *
 * <p>The tenant identity is derived exclusively from the authenticated server
 * context. The endpoint does not accept a tenant identifier and does not mutate
 * the lead record.</p>
 */
@RestController
@RequestMapping("/api/v1/crm/ai")
public class CrmAiController {
    private final CrmExtendedService crm;
    private final CrmAiPolicyService policy;
    private final DeterministicLeadScoringService scoring;

    public CrmAiController(
            CrmExtendedService crm,
            CrmAiPolicyService policy,
            DeterministicLeadScoringService scoring) {
        this.crm = crm;
        this.policy = policy;
        this.scoring = scoring;
    }

    @RequireCapability("CRM.LEAD.READ")
    @PostMapping("/leads/{leadId}/score")
    public Map<String, Object> scoreLead(
            Authentication authentication,
            @PathVariable UUID leadId,
            @Valid @RequestBody(required = false) LeadScoringRequest request,
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationHeader) {
        UUID tenantId = authenticatedContext(authentication, "tenant_id");
        Map<String, Object> lead = crm.getLead(authentication, leadId);
        LeadScoringRequest safeRequest = request == null ? new LeadScoringRequest(null, null) : request;

        Map<String, Object> context = new LinkedHashMap<>();
        context.put("companyName", lead.get("company_name"));
        context.put("source", lead.get("source"));
        context.put("annualRevenue", safeRequest.annualRevenue());
        context.put("employeeCount", safeRequest.employeeCount());

        CrmAiPolicyService.PolicyDecision decision = policy.sanitizeLeadContext(context);
        if (!decision.allowed()) {
            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "CRM AI request rejected by policy: " + decision.reasonCode());
        }

        String correlationId = normalizeCorrelationId(correlationHeader);
        DeterministicLeadScoringService.LeadScore result = scoring.score(
                new DeterministicLeadScoringService.LeadScoringInput(
                        tenantId.toString(),
                        leadId.toString(),
                        stringValue(lead.get("email")),
                        stringValue(lead.get("phone")),
                        stringValue(decision.sanitizedContext().get("companyName")),
                        numberValue(decision.sanitizedContext().get("annualRevenue")),
                        integerValue(decision.sanitizedContext().get("employeeCount")),
                        stringValue(decision.sanitizedContext().get("source"))));

        LinkedHashMap<String, Object> response = new LinkedHashMap<>();
        response.put("correlationId", correlationId);
        response.put("leadId", result.leadId());
        response.put("score", result.score());
        response.put("grade", result.grade());
        response.put("confidence", result.confidence());
        response.put("reasonCodes", result.reasonCodes());
        response.put("advisoryOnly", result.advisoryOnly());
        response.put("modelReference", result.modelReference());
        response.put("sensitiveDataRedacted", decision.sensitiveDataRedacted());
        response.put("humanConfirmationRequired", true);
        return response;
    }

    private UUID authenticatedContext(Authentication authentication, String key) {
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

    private String normalizeCorrelationId(String value) {
        if (value == null || value.isBlank()) {
            return UUID.randomUUID().toString();
        }
        String normalized = value.trim();
        if (normalized.length() > 128 || !normalized.matches("[A-Za-z0-9._:-]+")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid X-Correlation-ID");
        }
        return normalized;
    }

    private static String stringValue(Object value) {
        return value == null ? null : value.toString();
    }

    private static BigDecimal numberValue(Object value) {
        if (value == null) return null;
        if (value instanceof BigDecimal decimal) return decimal;
        if (value instanceof Number number) return new BigDecimal(number.toString());
        return new BigDecimal(value.toString());
    }

    private static Integer integerValue(Object value) {
        if (value == null) return null;
        if (value instanceof Number number) return number.intValue();
        return Integer.valueOf(value.toString());
    }

    public record LeadScoringRequest(
            @PositiveOrZero BigDecimal annualRevenue,
            @Min(0) Integer employeeCount) {
    }
}
