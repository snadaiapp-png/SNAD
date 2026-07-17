package com.sanad.platform.crm.web;

import com.sanad.platform.crm.error.CrmContractException;
import com.sanad.platform.crm.error.CrmErrorCode;
import com.sanad.platform.crm.pagination.CrmEnvelopes.SingleResponse;
import com.sanad.platform.crm.party.application.ContactRelationshipImportService;
import com.sanad.platform.crm.party.application.ContactRelationshipImportService.ImportResult;
import com.sanad.platform.crm.party.application.ContactRelationshipImportService.ImportRow;
import com.sanad.platform.security.authorization.RequireCapability;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v2/crm/contact-relationship-imports")
public class CrmContactRelationshipImportController {

    private final ContactRelationshipImportService imports;

    public CrmContactRelationshipImportController(ContactRelationshipImportService imports) {
        this.imports = imports;
    }

    @RequireCapability("CRM.CONTACT.IMPORT")
    @PostMapping
    public ResponseEntity<SingleResponse<ImportResult>> importRows(
            Authentication authentication,
            @Valid @RequestBody ContactRelationshipImportRequest request,
            HttpServletRequest httpRequest) {
        if (request.rows() == null || request.rows().isEmpty()) {
            throw new CrmContractException(CrmErrorCode.VALIDATION_ERROR,
                    "At least one import row is required.");
        }
        ImportResult result = imports.importRows(
                contextId(authentication, "tenant_id"),
                contextId(authentication, "user_id"),
                request.importId(),
                request.rows());
        return ResponseEntity.status(result.failedRows() == 0 ? 201 : 207)
                .body(SingleResponse.of(result, requestId(httpRequest)));
    }

    private static UUID contextId(Authentication authentication, String key) {
        if (authentication == null || authentication.getDetails() == null) return null;
        Object details = authentication.getDetails();
        if (details instanceof Map<?, ?> map && map.get(key) != null) {
            try {
                return UUID.fromString(map.get(key).toString());
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
        return null;
    }

    private static UUID requestId(HttpServletRequest request) {
        String value = request == null ? null : request.getHeader("X-Request-ID");
        if (value != null && !value.isBlank()) {
            try {
                return UUID.fromString(value);
            } catch (IllegalArgumentException ignored) {
                // Fall through to a generated request identifier.
            }
        }
        return UUID.randomUUID();
    }

    public record ContactRelationshipImportRequest(
            UUID importId,
            List<ImportRow> rows) {}
}
