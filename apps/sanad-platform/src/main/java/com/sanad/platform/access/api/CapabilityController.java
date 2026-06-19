package com.sanad.platform.access.api;

import com.sanad.platform.access.capability.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/access/capabilities")
public class CapabilityController {

    private final AccessCapabilityService capabilityService;

    public CapabilityController(AccessCapabilityService capabilityService) {
        this.capabilityService = capabilityService;
    }

    @PostMapping
    ResponseEntity<CapabilityResponse> create(@RequestBody Map<String, String> body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(capabilityService.create(
                body.get("code"), body.get("name"), body.get("description")));
    }

    @GetMapping
    ResponseEntity<List<CapabilityResponse>> list() {
        return ResponseEntity.ok(capabilityService.list());
    }

    @GetMapping("/{capabilityId}")
    ResponseEntity<CapabilityResponse> get(@PathVariable UUID capabilityId) {
        return ResponseEntity.ok(capabilityService.get(capabilityId));
    }

    @PutMapping("/{capabilityId}")
    ResponseEntity<CapabilityResponse> update(
            @PathVariable UUID capabilityId, @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(capabilityService.update(
                capabilityId, body.get("name"), body.get("description")));
    }

    @PatchMapping("/{capabilityId}/activate")
    ResponseEntity<CapabilityResponse> activate(@PathVariable UUID capabilityId) {
        return ResponseEntity.ok(capabilityService.changeStatus(
                capabilityId, CapabilityStatus.ACTIVE));
    }

    @PatchMapping("/{capabilityId}/deactivate")
    ResponseEntity<CapabilityResponse> deactivate(@PathVariable UUID capabilityId) {
        return ResponseEntity.ok(capabilityService.changeStatus(
                capabilityId, CapabilityStatus.INACTIVE));
    }
}
