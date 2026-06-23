package com.sanad.platform.access.api;

import com.sanad.platform.access.role.RoleAccessResponse;
import com.sanad.platform.security.authorization.RequireCapability;
import com.sanad.platform.access.role.RoleCapabilityService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/access/roles/{roleId}/access-items")
public class RoleAccessController {

    private final RoleCapabilityService mappingService;

    public RoleAccessController(RoleCapabilityService mappingService) {
        this.mappingService = mappingService;
    }

    @RequireCapability("ROLE.WRITE")
    @PostMapping("/{capabilityId}")
    ResponseEntity<RoleAccessResponse> attach(
            @RequestParam UUID tenantId,
            @PathVariable UUID roleId,
            @PathVariable UUID capabilityId) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(mappingService.attach(tenantId, roleId, capabilityId));
    }

    @RequireCapability("ROLE.WRITE")
    @DeleteMapping("/{capabilityId}")
    ResponseEntity<Void> detach(
            @RequestParam UUID tenantId,
            @PathVariable UUID roleId,
            @PathVariable UUID capabilityId) {
        mappingService.detach(tenantId, roleId, capabilityId);
        return ResponseEntity.noContent().build();
    }

    @RequireCapability("ROLE.READ")
    @GetMapping
    ResponseEntity<List<RoleAccessResponse>> list(
            @RequestParam UUID tenantId,
            @PathVariable UUID roleId) {
        return ResponseEntity.ok(mappingService.list(tenantId, roleId));
    }
}
