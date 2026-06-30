package com.sanad.platform.access.api;

import com.sanad.platform.access.role.RoleAccessResponse;
import com.sanad.platform.security.authorization.RequireCapability;
import com.sanad.platform.access.role.RoleCapabilityService;
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
    ResponseEntity<PageResponse<RoleAccessResponse>> list(
            @RequestParam UUID tenantId,
            @PathVariable UUID roleId,
            @Valid PageRequestParams params) {
        Set<String> allowedSortFields = Set.of("id", "roleId", "capabilityId", "createdAt");
        Pageable pageable = SortAllowlist.toPageable(params, allowedSortFields);
        Page<RoleAccessResponse> page = mappingService.list(tenantId, roleId, pageable);
        return ResponseEntity.ok(PageResponseBuilder.from(page, page.getContent()));
    }
}
