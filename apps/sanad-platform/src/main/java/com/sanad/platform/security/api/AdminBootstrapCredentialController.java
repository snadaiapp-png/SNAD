package com.sanad.platform.security.api;

import com.sanad.platform.security.authorization.RequireCapability;
import com.sanad.platform.security.dto.AdminBootstrapCredentialRequest;
import com.sanad.platform.security.service.AdminBootstrapCredentialService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/** Administrator-only one-time initial credential bootstrap endpoint. */
@RestController
@RequestMapping("/api/v1/auth")
public class AdminBootstrapCredentialController {

    private final AdminBootstrapCredentialService bootstrapService;

    public AdminBootstrapCredentialController(AdminBootstrapCredentialService bootstrapService) {
        this.bootstrapService = bootstrapService;
    }

    @RequireCapability("USER.WRITE")
    @PostMapping("/admin-bootstrap-credential/{userId}")
    public ResponseEntity<Void> bootstrapCredential(
            Authentication authentication,
            @PathVariable UUID userId,
            @Valid @RequestBody AdminBootstrapCredentialRequest request
    ) {
        PrincipalIds principal = principal(authentication);
        if (principal == null) {
            return ResponseEntity.status(401)
                    .header(HttpHeaders.CACHE_CONTROL, "no-store")
                    .build();
        }

        bootstrapService.bootstrap(principal.tenantId(), principal.userId(), userId, request);
        return ResponseEntity.noContent()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .build();
    }

    @SuppressWarnings("unchecked")
    private PrincipalIds principal(Authentication authentication) {
        if (authentication == null || !(authentication.getDetails() instanceof Map<?, ?>)) {
            return null;
        }
        Map<String, Object> claims = (Map<String, Object>) authentication.getDetails();
        Object tenantId = claims.get("tenant_id");
        Object userId = claims.get("user_id");
        if (!(tenantId instanceof String) || !(userId instanceof String)) {
            return null;
        }
        return new PrincipalIds(UUID.fromString((String) tenantId), UUID.fromString((String) userId));
    }

    private record PrincipalIds(UUID tenantId, UUID userId) {
    }
}
