package com.sanad.platform.crm.caller.web;

import com.sanad.platform.crm.caller.application.CallerDatasetService;
import com.sanad.platform.crm.error.CrmContractException;
import com.sanad.platform.crm.error.CrmErrorCode;
import com.sanad.platform.security.authorization.RequireCapability;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Base64;
import java.util.Map;
import java.util.UUID;

/**
 * Offline caller dataset delta endpoint (G8-03 §37–§38).
 *
 * <p>Tenant comes ONLY from the authenticated context (never query/body).
 * Mirror of the G7 pull pattern: {@code X-Device-Id} is parsed for logging;
 * registry enforcement follows the platform's device lifecycle track.
 */
@RestController
@RequestMapping("/api/v2/crm")
public class CallerDatasetController {

    private final CallerDatasetService service;

    public CallerDatasetController(CallerDatasetService service) {
        this.service = service;
    }

    @GetMapping("/caller-identification/delta")
    @RequireCapability(CallerIdentificationController.CAPABILITY_READ)
    public CallerDatasetService.CallerDatasetDelta delta(
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "500") int limit,
            @RequestParam(defaultValue = "false") boolean keyMissing,
            @RequestHeader(value = "X-Device-Id", required = false) String deviceId,
            Authentication authentication) {
        UUID tenantId = tenantId(authentication);
        long cursorMs = 0;
        UUID cursorId = null;
        if (cursor != null && !cursor.isBlank()) {
            String decoded = new String(Base64.getUrlDecoder().decode(cursor));
            String[] parts = decoded.split(":", 2);
            try {
                cursorMs = Long.parseLong(parts[0]);
                cursorId = parts.length > 1 ? UUID.fromString(parts[1]) : null;
            } catch (RuntimeException exception) {
                throw new CrmContractException(CrmErrorCode.VALIDATION_ERROR, "cursor is invalid.");
            }
        }
        return service.delta(tenantId, cursorMs, cursorId, limit, keyMissing);
    }

    private static UUID tenantId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getDetails() instanceof Map<?, ?> details) || details.get("tenant_id") == null) {
            throw new CrmContractException(CrmErrorCode.UNAUTHORIZED, "Authenticated CRM context is required.");
        }
        try {
            return UUID.fromString(details.get("tenant_id").toString());
        } catch (IllegalArgumentException exception) {
            throw new CrmContractException(CrmErrorCode.UNAUTHORIZED, "Invalid authenticated CRM context.");
        }
    }
}
