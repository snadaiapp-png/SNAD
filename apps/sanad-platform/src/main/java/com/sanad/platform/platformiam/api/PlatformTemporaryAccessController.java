package com.sanad.platform.platformiam.api;

import com.sanad.platform.platformiam.dto.CreatePlatformTemporaryAccessRequest;
import com.sanad.platform.platformiam.dto.PlatformLifecycleRequest;
import com.sanad.platform.platformiam.dto.PlatformTemporaryAccessResponse;
import com.sanad.platform.platformiam.service.PlatformTemporaryAccessService;
import com.sanad.platform.security.authorization.RequireCapability;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/executive/users/{userId}/temporary-access")
public class PlatformTemporaryAccessController {
    private final PlatformTemporaryAccessService access;

    public PlatformTemporaryAccessController(PlatformTemporaryAccessService access) {
        this.access = access;
    }

    @GetMapping
    @RequireCapability("PLATFORM.PERMISSION.READ")
    public List<PlatformTemporaryAccessResponse> listTemporaryAccess(Authentication actor, @PathVariable UUID userId) {
        return access.list(actor, userId);
    }

    @PostMapping
    @RequireCapability("PLATFORM.PERMISSION.MANAGE")
    public ResponseEntity<PlatformTemporaryAccessResponse> grant(Authentication actor,
            @PathVariable UUID userId, @Valid @RequestBody CreatePlatformTemporaryAccessRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(access.grant(actor, userId, request));
    }

    @PostMapping("/{grantId}/revoke")
    @RequireCapability("PLATFORM.PERMISSION.MANAGE")
    public ResponseEntity<Void> revoke(Authentication actor, @PathVariable UUID userId,
            @PathVariable UUID grantId, @Valid @RequestBody PlatformLifecycleRequest request) {
        access.revoke(actor, userId, grantId, request.reason());
        return ResponseEntity.noContent().build();
    }
}
