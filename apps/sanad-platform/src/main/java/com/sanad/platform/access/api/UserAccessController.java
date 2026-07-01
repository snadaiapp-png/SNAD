package com.sanad.platform.access.api;

import com.sanad.platform.access.UserAccessResponse;
import com.sanad.platform.security.tenant.TenantResolver;
import com.sanad.platform.security.authorization.RequireCapability;
import com.sanad.platform.access.grant.UserRoleGrantService;
import com.sanad.platform.shared.api.PageRequestParams;
import com.sanad.platform.shared.api.PageResponse;
import com.sanad.platform.shared.api.PageResponseBuilder;
import com.sanad.platform.shared.api.SortAllowlist;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/access/users")
public class UserAccessController {

    private final UserRoleGrantService grantService;
    private final com.sanad.platform.security.tenant.TenantResolver tenantResolver;

    public UserAccessController(UserRoleGrantService grantService,
            com.sanad.platform.security.tenant.TenantResolver tenantResolver) {
        this.grantService = grantService;
        this.tenantResolver = tenantResolver;
    }

    @RequireCapability("USER.GRANT_ROLE")
    @PostMapping("/{userId}/role-links/{roleId}")
    ResponseEntity<UserAccessResponse> grant(
            @RequestParam UUID tenantId,
            @PathVariable UUID userId,
            @PathVariable UUID roleId,
            @RequestParam(required = false) UUID organizationId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                grantService.grant(tenantResolver.validateClientSelector(tenantId), userId, roleId, organizationId));
    }

    @RequireCapability("ROLE.READ")
    @GetMapping("/{userId}/role-links")
    ResponseEntity<PageResponse<UserAccessResponse>> list(
            @RequestParam UUID tenantId,
            @PathVariable UUID userId,
            @Valid PageRequestParams params) {
        Set<String> allowedSortFields = Set.of("id", "userId", "roleId", "organizationId", "status", "createdAt");
        Pageable pageable = SortAllowlist.toPageable(params, allowedSortFields);
        Page<UserAccessResponse> page = grantService.list(tenantResolver.validateClientSelector(tenantId), userId, pageable);
        return ResponseEntity.ok(PageResponseBuilder.from(page, page.getContent()));
    }

    @RequireCapability("USER.REVOKE_ROLE")
    @PatchMapping("/role-links/{grantId}/revoke")
    ResponseEntity<UserAccessResponse> revoke(
            @RequestParam UUID tenantId,
            @PathVariable UUID grantId) {
        return ResponseEntity.ok(grantService.revoke(tenantResolver.validateClientSelector(tenantId), grantId));
    }
}
