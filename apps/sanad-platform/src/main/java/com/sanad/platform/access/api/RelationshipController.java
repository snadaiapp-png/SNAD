package com.sanad.platform.access.api;

import com.sanad.platform.access.relationship.AccessRelationshipService;
import com.sanad.platform.security.authorization.RequireCapability;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/access/relationships")
public class RelationshipController {
    private final AccessRelationshipService service;

    public RelationshipController(AccessRelationshipService service) {
        this.service = service;
    }

    @RequireCapability("AUTHORIZATION.RELATIONSHIP.MANAGE")
    @PostMapping
    public ResponseEntity<AccessRelationshipService.RelationshipResponse> create(
            Authentication authentication,
            @RequestBody AccessRelationshipService.CreateRelationshipRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(
                AccessPrincipalContext.requireTenantId(authentication),
                AccessPrincipalContext.requireUserId(authentication), request));
    }

    @RequireCapability("AUTHORIZATION.RELATIONSHIP.MANAGE")
    @GetMapping
    public ResponseEntity<List<AccessRelationshipService.RelationshipResponse>> list(
            Authentication authentication, @RequestParam UUID userId) {
        return ResponseEntity.ok(service.list(
                AccessPrincipalContext.requireTenantId(authentication), userId));
    }

    @RequireCapability("AUTHORIZATION.RELATIONSHIP.MANAGE")
    @PatchMapping("/{relationshipId}/revoke")
    public ResponseEntity<Void> revoke(
            Authentication authentication, @PathVariable UUID relationshipId,
            @RequestParam(required = false) String reason) {
        service.revoke(AccessPrincipalContext.requireTenantId(authentication),
                AccessPrincipalContext.requireUserId(authentication), relationshipId, reason);
        return ResponseEntity.noContent().build();
    }
}
