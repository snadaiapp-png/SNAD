package com.sanad.platform.crm.search.web;

import com.sanad.platform.crm.search.application.SearchUseCases;
import com.sanad.platform.crm.search.domain.SearchRepository.SearchResultRecord;
import com.sanad.platform.security.authorization.RequireCapability;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * V1 REST controller for CRM cross-entity search.
 * <p>
 * Mounted under {@code /api/v1/crm/search}. Requires {@code CRM.ACCOUNT.READ}
 * (search covers accounts, contacts, leads — all gated by their respective
 * READ capabilities; we reuse ACCOUNT.READ as the gating capability since
 * it is the broadest).
 * <p>
 * Branch: feature/crm-search-export
 */
@RestController
@RequestMapping("/api/v1/crm/search")
public class SearchController {

    private final SearchUseCases search;

    public SearchController(SearchUseCases search) {
        this.search = search;
    }

    @RequireCapability("CRM.ACCOUNT.READ")
    @GetMapping
    public List<Map<String, Object>> search(
            Authentication authentication,
            @RequestParam String q,
            @RequestParam(defaultValue = "20") int limit) {
        UUID tenantId = tenantId(authentication);
        List<SearchResultRecord> results = search.search(tenantId, q, limit);
        return results.stream().map(this::toRow).toList();
    }

    private Map<String, Object> toRow(SearchResultRecord r) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("entity_type", r.entityType());
        row.put("entity_id", r.entityId());
        row.put("display_name", r.displayName());
        row.put("secondary_info", r.secondaryInfo());
        row.put("matched_field", r.matchedField());
        return row;
    }

    private static UUID tenantId(Authentication authentication) {
        return context(authentication, "tenant_id");
    }

    private static UUID context(Authentication authentication, String key) {
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
}
