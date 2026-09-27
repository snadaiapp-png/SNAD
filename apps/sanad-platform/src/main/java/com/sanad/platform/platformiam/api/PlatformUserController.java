package com.sanad.platform.platformiam.api;

import com.sanad.platform.access.UserAccessResponse;
import com.sanad.platform.platformiam.dto.CreatePlatformUserRequest;
import com.sanad.platform.platformiam.dto.PlatformLifecycleRequest;
import com.sanad.platform.platformiam.dto.PlatformSessionSummaryResponse;
import com.sanad.platform.platformiam.dto.PlatformUserResponse;
import com.sanad.platform.platformiam.dto.ReplacePlatformRolesRequest;
import com.sanad.platform.platformiam.dto.UpdatePlatformUserRequest;
import com.sanad.platform.platformiam.service.PlatformRoleService;
import com.sanad.platform.platformiam.service.PlatformUserService;
import com.sanad.platform.security.authorization.PlatformMembershipGuard;
import com.sanad.platform.security.authorization.RequireCapability;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/executive/users")
public class PlatformUserController {

    private final PlatformUserService users;
    private final PlatformRoleService roles;
    private final PlatformMembershipGuard membershipGuard;

    public PlatformUserController(
            PlatformUserService users,
            PlatformRoleService roles,
            PlatformMembershipGuard membershipGuard) {
        this.users = users;
        this.roles = roles;
        this.membershipGuard = membershipGuard;
    }

    @GetMapping
    @RequireCapability("PLATFORM.USER.READ")
    public List<PlatformUserResponse> list(Authentication authentication) {
        membershipGuard.requireActive(authentication);
        return users.list(authentication);
    }

    @GetMapping("/{userId}")
    @RequireCapability("PLATFORM.USER.READ")
    public PlatformUserResponse get(Authentication authentication, @PathVariable UUID userId) {
        membershipGuard.requireActive(authentication);
        return users.get(authentication, userId);
    }

    @PostMapping
    @RequireCapability("PLATFORM.USER.CREATE")
    public ResponseEntity<PlatformUserResponse> create(
            Authentication authentication,
            @Valid @RequestBody CreatePlatformUserRequest request) {
        membershipGuard.requireActive(authentication);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(users.createPlatformUser(authentication, request));
    }

    @PatchMapping("/{userId}")
    @RequireCapability("PLATFORM.USER.UPDATE")
    public PlatformUserResponse update(
            Authentication authentication,
            @PathVariable UUID userId,
            @Valid @RequestBody UpdatePlatformUserRequest request) {
        membershipGuard.requireActive(authentication);
        return users.update(authentication, userId, request);
    }

    @PostMapping("/{userId}/activate")
    @RequireCapability("PLATFORM.USER.UPDATE")
    public PlatformUserResponse activate(
            Authentication authentication,
            @PathVariable UUID userId,
            @Valid @RequestBody PlatformLifecycleRequest request) {
        membershipGuard.requireActive(authentication);
        return users.activate(authentication, userId, request == null ? null : request.reason());
    }

    @PostMapping("/{userId}/suspend")
    @RequireCapability("PLATFORM.USER.SUSPEND")
    public PlatformUserResponse suspend(
            Authentication authentication,
            @PathVariable UUID userId,
            @Valid @RequestBody PlatformLifecycleRequest request) {
        membershipGuard.requireActive(authentication);
        return users.suspend(authentication, userId, request == null ? null : request.reason());
    }

    @PostMapping("/{userId}/lock")
    @RequireCapability("PLATFORM.SECURITY.MANAGE")
    public PlatformUserResponse lock(
            Authentication authentication,
            @PathVariable UUID userId,
            @Valid @RequestBody PlatformLifecycleRequest request) {
        membershipGuard.requireActive(authentication);
        return users.lock(authentication, userId, request == null ? null : request.reason());
    }

    @PostMapping("/{userId}/disable")
    @RequireCapability("PLATFORM.USER.DISABLE")
    public PlatformUserResponse disable(
            Authentication authentication,
            @PathVariable UUID userId,
            @Valid @RequestBody PlatformLifecycleRequest request) {
        membershipGuard.requireActive(authentication);
        return users.disable(authentication, userId, request == null ? null : request.reason());
    }

    @GetMapping("/{userId}/roles")
    @RequireCapability("PLATFORM.ROLE.READ")
    public List<UserAccessResponse> roles(Authentication authentication, @PathVariable UUID userId) {
        membershipGuard.requireActive(authentication);
        return roles.listUserRoles(authentication, userId);
    }

    @PutMapping("/{userId}/roles")
    @RequireCapability("PLATFORM.ROLE.ASSIGN")
    public List<UserAccessResponse> replaceRoles(
            Authentication authentication,
            @PathVariable UUID userId,
            @Valid @RequestBody ReplacePlatformRolesRequest request) {
        membershipGuard.requireActive(authentication);
        return roles.replaceUserRoles(authentication, userId, request);
    }

    @GetMapping("/{userId}/permissions")
    @RequireCapability("PLATFORM.PERMISSION.READ")
    public List<String> permissions(Authentication authentication, @PathVariable UUID userId) {
        membershipGuard.requireActive(authentication);
        return roles.effectivePermissions(authentication, userId);
    }

    @GetMapping("/{userId}/sessions")
    @RequireCapability("PLATFORM.SESSION.READ")
    public PlatformSessionSummaryResponse sessions(Authentication authentication, @PathVariable UUID userId) {
        membershipGuard.requireActive(authentication);
        return users.sessions(authentication, userId);
    }

    @PostMapping("/{userId}/sessions/revoke")
    @RequireCapability("PLATFORM.SESSION.REVOKE")
    public PlatformSessionSummaryResponse revokeSessions(
            Authentication authentication,
            @PathVariable UUID userId,
            @Valid @RequestBody PlatformLifecycleRequest request) {
        membershipGuard.requireActive(authentication);
        return users.revokeSessions(authentication, userId, request == null ? null : request.reason());
    }
}
