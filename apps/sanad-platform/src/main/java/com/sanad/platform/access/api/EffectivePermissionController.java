package com.sanad.platform.access.api;

import com.sanad.platform.access.evaluation.EffectivePermissionProjectionService;
import com.sanad.platform.admin.service.PlatformAuditWriter;
import com.sanad.platform.security.authorization.RequireCapability;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/access/effective-permissions")
public class EffectivePermissionController {
    private final EffectivePermissionProjectionService service;
    private final PlatformAuditWriter auditWriter;

    public EffectivePermissionController(
            EffectivePermissionProjectionService service, PlatformAuditWriter auditWriter) {
        this.service = service;
        this.auditWriter = auditWriter;
    }

    @RequireCapability("ROLE.READ")
    @GetMapping
    public ResponseEntity<List<EffectivePermissionProjectionService.EffectivePermissionRow>> list(
            Authentication authentication, @RequestParam UUID userId) {
        return ResponseEntity.ok(service.list(
                AccessPrincipalContext.requireTenantId(authentication), userId));
    }

    @RequireCapability("AUTHORIZATION.RESYNC")
    @PostMapping("/resync")
    public ResponseEntity<List<EffectivePermissionProjectionService.EffectivePermissionRow>> resync(
            Authentication authentication, @RequestParam UUID userId) {
        UUID tenantId = AccessPrincipalContext.requireTenantId(authentication);
        UUID actorUserId = AccessPrincipalContext.requireUserId(authentication);
        List<EffectivePermissionProjectionService.EffectivePermissionRow> rows =
                service.rebuild(tenantId, userId);
        auditWriter.writeSuccess(tenantId, actorUserId, tenantId,
                "AUTHORIZATION_RESYNC", "EFFECTIVE_PERMISSION_PROJECTION",
                userId.toString(), "RECOVERY_RESYNC", null,
                java.util.Map.of("rowCount", rows.size()), null, Instant.now());
        return ResponseEntity.ok(rows);
    }
}
