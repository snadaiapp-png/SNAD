package com.sanad.platform.access.api;

import com.sanad.platform.access.role.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/access/roles")
public class RoleController {

    private final RoleService roleService;

    public RoleController(RoleService roleService) {
        this.roleService = roleService;
    }

    @PostMapping
    ResponseEntity<RoleResponse> create(
            @RequestParam UUID tenantId,
            @Valid @RequestBody CreateRoleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(roleService.create(tenantId, request));
    }

    @GetMapping
    ResponseEntity<List<RoleResponse>> list(@RequestParam UUID tenantId) {
        return ResponseEntity.ok(roleService.list(tenantId));
    }

    @GetMapping("/{roleId}")
    ResponseEntity<RoleResponse> get(
            @RequestParam UUID tenantId, @PathVariable UUID roleId) {
        return ResponseEntity.ok(roleService.get(tenantId, roleId));
    }

    @PutMapping("/{roleId}")
    ResponseEntity<RoleResponse> update(
            @RequestParam UUID tenantId,
            @PathVariable UUID roleId,
            @Valid @RequestBody UpdateRoleRequest request) {
        return ResponseEntity.ok(roleService.update(tenantId, roleId, request));
    }

    @PatchMapping("/{roleId}/activate")
    ResponseEntity<RoleResponse> activate(
            @RequestParam UUID tenantId, @PathVariable UUID roleId) {
        return ResponseEntity.ok(roleService.changeStatus(
                tenantId, roleId, RoleStatus.ACTIVE));
    }

    @PatchMapping("/{roleId}/deactivate")
    ResponseEntity<RoleResponse> deactivate(
            @RequestParam UUID tenantId, @PathVariable UUID roleId) {
        return ResponseEntity.ok(roleService.changeStatus(
                tenantId, roleId, RoleStatus.INACTIVE));
    }

    @PatchMapping("/{roleId}/archive")
    ResponseEntity<RoleResponse> archive(
            @RequestParam UUID tenantId, @PathVariable UUID roleId) {
        return ResponseEntity.ok(roleService.changeStatus(
                tenantId, roleId, RoleStatus.ARCHIVED));
    }
}
