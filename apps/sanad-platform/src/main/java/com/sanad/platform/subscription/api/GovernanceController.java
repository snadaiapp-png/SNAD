package com.sanad.platform.subscription.api;

import com.sanad.platform.security.authorization.ControlPlaneAccessGuard;
import com.sanad.platform.security.authorization.RequireCapability;
import com.sanad.platform.subscription.audit.AuditQueryService;
import com.sanad.platform.subscription.rbac.ControlPlaneAccessService;
import com.sanad.platform.subscription.read.PageResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * Governance read endpoints: granular access check (v2) and the paginated SCP audit trail (v2).
 * Both are additive to the existing executive namespace and preserve legacy routes.
 */
@RestController
@RequestMapping("/api/v1/executive")
public class GovernanceController {

    private final ControlPlaneAccessGuard accessGuard;
    private final ControlPlaneAccessService accessService;
    private final AuditQueryService auditQueryService;

    public GovernanceController(ControlPlaneAccessGuard accessGuard,
                                ControlPlaneAccessService accessService,
                                AuditQueryService auditQueryService) {
        this.accessGuard = accessGuard;
        this.accessService = accessService;
        this.auditQueryService = auditQueryService;
    }

    @GetMapping("/access-check/v2")
    @RequireCapability("EXECUTIVE_VIEW")
    public ResponseEntity<ControlPlaneAccessService.AccessCheckV2> accessCheckV2(
            Authentication authentication) {
        accessGuard.require(authentication);
        return ResponseEntity.ok(accessService.accessCheck(authentication));
    }

    @GetMapping("/audit/v2")
    @RequireCapability("audit.read")
    public ResponseEntity<PageResponse<Map<String, Object>>> audit(
            @RequestParam(name = "tenantId", required = false) UUID tenantId,
            @RequestParam(name = "action", required = false) String action,
            @RequestParam(name = "resourceType", required = false) String resourceType,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size,
            @RequestParam(name = "sort", defaultValue = "created_at") String sort,
            @RequestParam(name = "direction", defaultValue = "DESC") String direction,
            Authentication authentication) {
        accessGuard.require(authentication);
        return ResponseEntity.ok(
                auditQueryService.query(tenantId, action, resourceType, page, size, sort, direction));
    }

}
