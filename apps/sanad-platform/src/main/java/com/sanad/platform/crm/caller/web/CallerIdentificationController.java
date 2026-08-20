package com.sanad.platform.crm.caller.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanad.platform.crm.caller.application.CallerIdentificationService;
import com.sanad.platform.crm.caller.application.CallerLookupResult;
import com.sanad.platform.crm.caller.domain.CallerLookupSource;
import com.sanad.platform.crm.caller.domain.CallerMatchStatus;
import com.sanad.platform.crm.error.CrmContractException;
import com.sanad.platform.crm.error.CrmErrorCode;
import com.sanad.platform.security.authorization.RequireCapability;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * G8 caller-identification lookup endpoint (G8-02 §21–§28, G8-ADR-006).
 *
 * <p>POST — not {@code GET ?phone=} — so the number never appears in URLs or
 * access logs. {@code tenantId} is NEVER accepted from the client; the tenant
 * is always the authenticated context. The response is data-minimized and the
 * endpoint is READ-ONLY (audit + metrics are the only writes).
 */
@RestController
@RequestMapping("/api/v2/crm")
public class CallerIdentificationController {

    static final String CAPABILITY_READ = "CRM.CALLER_ID.READ";
    static final String CAPABILITY_READ_RESTRICTED = "CRM.CALLER_ID.READ_RESTRICTED";

    private final CallerIdentificationService service;
    private final ObjectMapper mapper;

    public CallerIdentificationController(CallerIdentificationService service, ObjectMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    @PostMapping("/caller-identification/lookup")
    @RequireCapability(CAPABILITY_READ)
    public CallerLookupResponse lookup(@RequestBody(required = false) JsonNode body, Authentication authentication) {
        UUID tenantId = tenantId(authentication);
        UUID userId = userId(authentication);
        if (body == null || body.isNull()) {
            throw new CrmContractException(CrmErrorCode.VALIDATION_ERROR, "Lookup payload is required.");
        }
        if (body.has("tenantId")) {
            throw new CrmContractException(CrmErrorCode.VALIDATION_ERROR,
                    "tenantId is not accepted in the request body; tenant comes from the authenticated context.");
        }
        Request request = parse(body);
        boolean allowRestricted = hasCapability(authentication, CAPABILITY_READ_RESTRICTED);
        CallerLookupResult result = service.lookup(
                tenantId, userId, request.phone(), request.countryHint(), request.source(), allowRestricted);
        if (result.matchStatus() == CallerMatchStatus.INVALID_NUMBER) {
            // G8 baseline §12.1: invalid input is a 422 structured error, never a 500.
            throw new CrmContractException(CrmErrorCode.CALLER_PHONE_INVALID,
                    "Phone number must be E.164 or include an explicit supported country hint.");
        }
        return CallerLookupResponse.from(result);
    }

    private Request parse(JsonNode body) {
        String phone = text(body, "phone");
        String countryHint = text(body, "countryHint");
        String source = text(body, "source");
        if (phone == null || phone.isBlank()) {
            throw new CrmContractException(CrmErrorCode.VALIDATION_ERROR, "phone is required.");
        }
        if (source == null || source.isBlank()) {
            throw new CrmContractException(CrmErrorCode.VALIDATION_ERROR, "source is required.");
        }
        CallerLookupSource parsedSource;
        try {
            parsedSource = CallerLookupSource.valueOf(source.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new CrmContractException(CrmErrorCode.VALIDATION_ERROR, "source is invalid.");
        }
        if (countryHint != null && !countryHint.matches("[A-Za-z]{2}")) {
            throw new CrmContractException(CrmErrorCode.VALIDATION_ERROR,
                    "countryHint must be an ISO 3166-1 alpha-2 code.");
        }
        String deviceId = text(body, "deviceId");
        if (deviceId != null && deviceId.length() > 128) {
            throw new CrmContractException(CrmErrorCode.VALIDATION_ERROR, "deviceId is too long.");
        }
        return new Request(phone.trim(), countryHint == null ? null : countryHint.toUpperCase(Locale.ROOT), parsedSource);
    }

    private static String text(JsonNode body, String field) {
        JsonNode node = body.get(field);
        if (node == null || node.isNull()) return null;
        if (!node.isTextual()) throw new CrmContractException(CrmErrorCode.VALIDATION_ERROR, field + " must be a string.");
        return node.asText();
    }

    private static UUID tenantId(Authentication authentication) {
        return contextId(authentication, "tenant_id");
    }

    private static UUID userId(Authentication authentication) {
        return contextId(authentication, "user_id");
    }

    private static UUID contextId(Authentication authentication, String key) {
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getDetails() instanceof Map<?, ?> details) || details.get(key) == null) {
            throw new CrmContractException(CrmErrorCode.UNAUTHORIZED, "Authenticated CRM context is required.");
        }
        try {
            return UUID.fromString(details.get(key).toString());
        } catch (IllegalArgumentException exception) {
            throw new CrmContractException(CrmErrorCode.UNAUTHORIZED, "Invalid authenticated CRM context.");
        }
    }

    private static boolean hasCapability(Authentication authentication, String capability) {
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> capability.equalsIgnoreCase(authority.getAuthority())
                        || ("CAPABILITY_" + capability).equalsIgnoreCase(authority.getAuthority()));
    }

    private record Request(String phone, String countryHint, CallerLookupSource source) {
    }

    /** Minimal, data-minimized caller card (G8-02 §25–§28). */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CallerLookupResponse(
            String matchStatus,
            String entityType,
            UUID entityId,
            String displayName,
            UUID accountId,
            String accountName,
            String phoneLabel,
            Boolean verified,
            Boolean preferred,
            String lifecycleStatus,
            String privacyLevel,
            String matchSource,
            Integer candidateCount) {

        static CallerLookupResponse from(CallerLookupResult result) {
            return new CallerLookupResponse(
                    result.matchStatus().name(),
                    result.entityType(),
                    result.entityId(),
                    result.displayName(),
                    result.accountId(),
                    result.accountName(),
                    result.phoneLabel(),
                    result.verified(),
                    result.preferred(),
                    result.lifecycleStatus(),
                    result.privacyLevel(),
                    result.matchSource(),
                    result.candidateCount());
        }
    }
}
