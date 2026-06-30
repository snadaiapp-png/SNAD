package com.sanad.platform.access.api;

import com.sanad.platform.access.role.*;
import com.sanad.platform.security.authorization.RequireCapability;
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
@RequestMapping("/api/v1/access/roles")
public class RoleController {

    private final RoleService roleService;

    public RoleController(RoleService roleService) {
        this.roleService = roleService;
    }

    @RequireCapability("ROLE.WRITE")
    @PostMapping
    ResponseEntity<RoleResponse> create(
            @RequestParam UUID tenantId,
            @Valid @RequestBody CreateRoleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(roleService.create(tenantId, request));
    }

    @RequireCapability("ROLE.READ")
    @GetMapping
    ResponseEntity<PageResponse<RoleResponse>> list(
            @RequestParam UUID tenantId,
            @Valid PageRequestParams params) {
        Set<String> allowedSortFields = Set.of("id", "code", "name", "status", "createdAt", "updatedAt");
        Pageable pageable = SortAllowlist.toPageable(params, allowedSortFields);
        Page<RoleResponse> page = roleService.list(tenantId, pageable);
        return ResponseEntity.ok(PageResponseBuilder.from(page, page.getContent()));
    }

    @RequireCapability("ROLE.READ")
    @GetMapping("/{roleId}")
    ResponseEntity<RoleResponse> get(
            @RequestParam UUID tenantId, @PathVariable UUID roleId) {
        return ResponseEntity.ok(roleService.get(tenantId, roleId));
    }

    @RequireCapability("ROLE.WRITE")
    @PutMapping("/{roleId}")
    ResponseEntity<RoleResponse> update(
            @RequestParam UUID tenantId,
            @PathVariable UUID roleId,
            @Valid @RequestBody UpdateRoleRequest request) {
        return ResponseEntity.ok(roleService.update(tenantId, roleId, request));
    }

    @RequireCapability("ROLE.WRITE")
    @PatchMapping("/{roleId}/activate")
    ResponseEntity<RoleResponse> activate(
            @RequestParam UUID tenantId, @PathVariable UUID roleId) {
        return ResponseEntity.ok(roleService.changeStatus(
                tenantId, roleId, RoleStatus.ACTIVE));
    }

    @RequireCapability("ROLE.WRITE")
    @PatchMapping("/{roleId}/deactivate")
    ResponseEntity<RoleResponse> deactivate(
            @RequestParam UUID tenantId, @PathVariable UUID roleId) {
        return ResponseEntity.ok(roleService.changeStatus(
                tenantId, roleId, RoleStatus.INACTIVE));
    }

    @RequireCapability("ROLE.WRITE")
    @PatchMapping("/{roleId}/archive")
    ResponseEntity<RoleResponse> archive(
            @RequestParam UUID tenantId, @PathVariable UUID roleId) {
        return ResponseEntity.ok(roleService.changeStatus(
                tenantId, roleId, RoleStatus.ARCHIVED));
    }
}
