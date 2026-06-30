package com.sanad.platform.access.api;

import com.sanad.platform.access.capability.*;
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
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/access/capabilities")
public class CapabilityController {

    private final AccessCapabilityService capabilityService;

    public CapabilityController(AccessCapabilityService capabilityService) {
        this.capabilityService = capabilityService;
    }

    @RequireCapability("CAPABILITY.MANAGE")
    @PostMapping
    ResponseEntity<CapabilityResponse> create(@RequestBody Map<String, String> body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(capabilityService.create(
                body.get("code"), body.get("name"), body.get("description")));
    }

    @RequireCapability("CAPABILITY.READ")
    @GetMapping
    ResponseEntity<PageResponse<CapabilityResponse>> list(@Valid PageRequestParams params) {
        // Capabilities are global; sort allowlist is the full set of fields.
        Set<String> allowedSortFields = Set.of("id", "code", "name", "status", "createdAt", "updatedAt");
        Pageable pageable = SortAllowlist.toPageable(params, allowedSortFields);
        Page<CapabilityResponse> page = capabilityService.list(pageable);
        return ResponseEntity.ok(PageResponseBuilder.from(page, page.getContent()));
    }

    @RequireCapability("CAPABILITY.READ")
    @GetMapping("/{capabilityId}")
    ResponseEntity<CapabilityResponse> get(@PathVariable UUID capabilityId) {
        return ResponseEntity.ok(capabilityService.get(capabilityId));
    }

    @RequireCapability("CAPABILITY.MANAGE")
    @PutMapping("/{capabilityId}")
    ResponseEntity<CapabilityResponse> update(
            @PathVariable UUID capabilityId, @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(capabilityService.update(
                capabilityId, body.get("name"), body.get("description")));
    }

    @RequireCapability("CAPABILITY.MANAGE")
    @PatchMapping("/{capabilityId}/activate")
    ResponseEntity<CapabilityResponse> activate(@PathVariable UUID capabilityId) {
        return ResponseEntity.ok(capabilityService.changeStatus(
                capabilityId, CapabilityStatus.ACTIVE));
    }

    @RequireCapability("CAPABILITY.MANAGE")
    @PatchMapping("/{capabilityId}/deactivate")
    ResponseEntity<CapabilityResponse> deactivate(@PathVariable UUID capabilityId) {
        return ResponseEntity.ok(capabilityService.changeStatus(
                capabilityId, CapabilityStatus.INACTIVE));
    }
}
