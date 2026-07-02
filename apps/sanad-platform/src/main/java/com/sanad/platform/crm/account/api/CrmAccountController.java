package com.sanad.platform.crm.account.api;

import com.sanad.platform.crm.account.service.CrmAccountService;
import com.sanad.platform.security.authorization.RequireCapability;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/crm/accounts")
public class CrmAccountController {
    private final CrmAccountService service;

    public CrmAccountController(CrmAccountService service) {
        this.service = service;
    }

    @RequireCapability("CRM.ACCOUNT.CREATE")
    @PostMapping
    public ResponseEntity<CrmAccountResponse> create(
            Authentication authentication,
            @Valid @RequestBody CreateCrmAccountRequest request) {
        CrmAccountResponse created = service.create(
                tenantId(authentication), userId(authentication), request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @RequireCapability("CRM.ACCOUNT.READ")
    @GetMapping
    public List<CrmAccountResponse> list(Authentication authentication) {
        return service.list(tenantId(authentication));
    }

    @RequireCapability("CRM.ACCOUNT.READ")
    @GetMapping("/{accountId}")
    public CrmAccountResponse get(
            Authentication authentication,
            @PathVariable UUID accountId) {
        return service.get(tenantId(authentication), accountId);
    }

    @RequireCapability("CRM.ACCOUNT.ARCHIVE")
    @PatchMapping("/{accountId}/archive")
    public CrmAccountResponse archive(
            Authentication authentication,
            @PathVariable UUID accountId) {
        return service.archive(tenantId(authentication), userId(authentication), accountId);
    }

    private UUID tenantId(Authentication authentication) {
        return contextId(authentication, "tenant_id");
    }

    private UUID userId(Authentication authentication) {
        return contextId(authentication, "user_id");
    }

    private UUID contextId(Authentication authentication, String key) {
        if (authentication == null || !(authentication.getDetails() instanceof Map<?, ?> details)) {
            throw new IllegalStateException("Authenticated context is required");
        }
        Object value = details.get(key);
        if (value == null) {
            throw new IllegalStateException("Missing authenticated context: " + key);
        }
        return UUID.fromString(value.toString());
    }
}
