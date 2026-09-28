package com.sanad.platform.security.api;

import com.sanad.platform.security.authorization.RequireCapability;
import com.sanad.platform.security.dto.AdminReconcileCredentialRequest;
import com.sanad.platform.security.service.AdminCredentialReconciliationService;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.Valid;
import org.springframework.core.env.Environment;
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

/**
 * Hidden, fail-closed production-recovery surface. The route is unavailable
 * unless explicitly enabled with snad.security.g2-reconciliation-enabled=true.
 */
@Hidden
@RestController
@RequestMapping("/api/v1/auth")
public class AdminCredentialReconciliationController {

    private static final String ENABLED_PROPERTY = "snad.security.g2-reconciliation-enabled";

    private final AdminCredentialReconciliationService reconciliationService;
    private final Environment environment;

    public AdminCredentialReconciliationController(
            AdminCredentialReconciliationService reconciliationService,
            Environment environment
    ) {
        this.reconciliationService = reconciliationService;
        this.environment = environment;
    }

    @RequireCapability("USER.WRITE")
    @PostMapping("/admin-reconcile-credential/{userId}")
    public ResponseEntity<Void> reconcileCredential(
            Authentication authentication,
            @PathVariable UUID userId,
            @Valid @RequestBody AdminReconcileCredentialRequest request
    ) {
        if (!environment.getProperty(ENABLED_PROPERTY, Boolean.class, false)) {
            return ResponseEntity.notFound()
                    .header(HttpHeaders.CACHE_CONTROL, "no-store")
                    .build();
        }

        PrincipalIds principal = principal(authentication);
        if (principal == null) {
            return ResponseEntity.status(401)
                    .header(HttpHeaders.CACHE_CONTROL, "no-store")
                    .build();
        }

        reconciliationService.reconcileCredential(
                principal.tenantId(),
                userId,
                request.getCredential(),
                principal.userId());

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
