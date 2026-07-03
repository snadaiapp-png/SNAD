package com.sanad.platform.access.api;

import com.sanad.platform.access.UserAccessResponse;
import com.sanad.platform.security.authorization.RequireCapability;
import com.sanad.platform.access.grant.UserRoleGrantService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/access/users")
public class UserAccessController {

    private final UserRoleGrantService grantService;

    public UserAccessController(UserRoleGrantService grantService) {
        this.grantService = grantService;
    }

    @RequireCapability("USER.GRANT_ROLE")
    @PostMapping("/{userId}/role-links/{roleId}")
    ResponseEntity<UserAccessResponse> grant(
            @RequestParam UUID tenantId,
            @PathVariable UUID userId,
            @PathVariable UUID roleId,
            @RequestParam(required = false) UUID organizationId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                grantService.grant(tenantId, userId, roleId, organizationId));
    }

    @RequireCapability("ROLE.READ")
    @GetMapping("/{userId}/role-links")
    ResponseEntity<List<UserAccessResponse>> list(
            @RequestParam UUID tenantId,
            @PathVariable UUID userId) {
        return ResponseEntity.ok(grantService.list(tenantId, userId));
    }

    @RequireCapability("USER.REVOKE_ROLE")
    @PatchMapping("/role-links/{grantId}/revoke")
    ResponseEntity<UserAccessResponse> revoke(
            @RequestParam UUID tenantId,
            @PathVariable UUID grantId) {
        return ResponseEntity.ok(grantService.revoke(tenantId, grantId));
    }
}
