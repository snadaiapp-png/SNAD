package com.sanad.platform.crm.web;

import com.sanad.platform.crm.error.CrmContractException;
import com.sanad.platform.crm.error.CrmErrorCode;
import com.sanad.platform.crm.pagination.CrmEnvelopes.SingleResponse;
import com.sanad.platform.crm.party.application.CommunicationPolicyService;
import com.sanad.platform.crm.party.application.CommunicationPolicyService.CommunicationPolicy;
import com.sanad.platform.security.authorization.RequireCapability;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v2/crm/communication-policy")
public class CrmCommunicationPolicyController {
    private final CommunicationPolicyService service;

    public CrmCommunicationPolicyController(CommunicationPolicyService service) {
        this.service = service;
    }

    @RequireCapability("CRM.COMMUNICATION.ADMIN")
    @GetMapping
    public SingleResponse<CommunicationPolicy> policy(
            Authentication authentication,
            HttpServletRequest request) {
        return SingleResponse.of(service.policy(tenantId(authentication)), requestId(request));
    }

    @RequireCapability("CRM.COMMUNICATION.ADMIN")
    @PatchMapping
    public SingleResponse<CommunicationPolicy> update(
            Authentication authentication,
            @Valid @RequestBody CommunicationPolicy requestBody,
            HttpServletRequest request) {
        return SingleResponse.of(service.update(
                tenantId(authentication), userId(authentication), requestBody), requestId(request));
    }

    private static UUID requestId(HttpServletRequest request) {
        String value = request == null ? null : request.getHeader("X-Request-ID");
        if (value != null && !value.isBlank()) {
            try { return UUID.fromString(value); } catch (IllegalArgumentException ignored) { }
        }
        return UUID.randomUUID();
    }

    private static UUID tenantId(Authentication authentication) { return contextId(authentication, "tenant_id"); }
    private static UUID userId(Authentication authentication) { return contextId(authentication, "user_id"); }

    private static UUID contextId(Authentication authentication, String key) {
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getDetails() instanceof Map<?, ?> details) || details.get(key) == null) {
            throw new CrmContractException(CrmErrorCode.UNAUTHORIZED, "Authenticated CRM context is required.");
        }
        try { return UUID.fromString(details.get(key).toString()); }
        catch (IllegalArgumentException exception) {
            throw new CrmContractException(CrmErrorCode.UNAUTHORIZED, "Invalid authenticated CRM context.");
        }
    }
}
