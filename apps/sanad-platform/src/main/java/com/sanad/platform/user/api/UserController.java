package com.sanad.platform.user.api;

import com.sanad.platform.shared.api.ApiErrorResponse;
import com.sanad.platform.shared.api.PageRequestParams;
import com.sanad.platform.shared.api.PageResponse;
import com.sanad.platform.shared.api.PageResponseBuilder;
import com.sanad.platform.shared.api.SortAllowlist;
import com.sanad.platform.security.authorization.RequireCapability;
import com.sanad.platform.user.dto.CreateUserRequest;
import com.sanad.platform.user.dto.UpdateUserRequest;
import com.sanad.platform.user.dto.UserResponse;
import com.sanad.platform.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * REST adapter for tenant-scoped User lifecycle operations.
 *
 * <p>The tenant scope is always supplied as a required query parameter and is
 * passed unchanged to {@link UserService}. No repository access or business
 * logic is permitted in this adapter.</p>
 */
@RestController
@RequestMapping("/api/v1/users")
@Tag(
        name = "Users",
        description = "Manage tenant-scoped platform users. Every operation requires tenantId."
)
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @Operation(summary = "Create a user", description = "Creates a tenant-scoped user. Default status is INVITED.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "User created",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = UserResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation failed",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Tenant not found",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Email already exists in tenant",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @RequireCapability("USER.CREATE")
    @PostMapping
    public ResponseEntity<UserResponse> createUser(
            @Parameter(description = "Tenant UUID (scope)", required = true)
            @RequestParam UUID tenantId,
            @Valid @RequestBody CreateUserRequest request) {

        UserResponse created = userService.createUser(tenantId, request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{userId}")
                .buildAndExpand(created.getId())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @Operation(summary = "List users", description = "Lists users belonging only to the requested tenant. Stage 03A: paginated.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "User list (paginated)",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = PageResponse.class))),
            @ApiResponse(responseCode = "400", description = "Missing or invalid tenantId / pagination",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @RequireCapability("USER.READ")
    @GetMapping
    public ResponseEntity<PageResponse<UserResponse>> listUsers(
            @Parameter(description = "Tenant UUID (scope)", required = true)
            @RequestParam UUID tenantId,
            @Valid PageRequestParams params) {
        // Sort allowlist for User
        Set<String> allowedSortFields = Set.of("id", "email", "displayName", "status", "createdAt", "updatedAt");
        Pageable pageable = SortAllowlist.toPageable(params, allowedSortFields);
        Page<UserResponse> page = userService.listUsers(tenantId, pageable);
        return ResponseEntity.ok(PageResponseBuilder.from(page, page.getContent()));
    }

    @Operation(summary = "Get a user", description = "Returns a user only when it belongs to the requested tenant.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "User found",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = UserResponse.class))),
            @ApiResponse(responseCode = "400", description = "Missing or invalid identifier",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "User not found",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @RequireCapability("USER.READ")
    @GetMapping("/{userId}")
    public ResponseEntity<UserResponse> getUser(
            @Parameter(description = "User UUID", required = true)
            @PathVariable UUID userId,
            @Parameter(description = "Tenant UUID (scope)", required = true)
            @RequestParam UUID tenantId) {

        return ResponseEntity.ok(userService.getUser(tenantId, userId));
    }

    @Operation(summary = "Update a user", description = "Updates email and display name within the tenant scope.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "User updated",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = UserResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation failed",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "User not found",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Email already exists in tenant",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @RequireCapability("USER.WRITE")
    @PutMapping("/{userId}")
    public ResponseEntity<UserResponse> updateUser(
            @Parameter(description = "User UUID", required = true)
            @PathVariable UUID userId,
            @Parameter(description = "Tenant UUID (scope)", required = true)
            @RequestParam UUID tenantId,
            @Valid @RequestBody UpdateUserRequest request) {

        return ResponseEntity.ok(userService.updateUser(tenantId, userId, request));
    }

    @Operation(summary = "Activate a user", description = "Sets the user status to ACTIVE.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "User activated",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = UserResponse.class))),
            @ApiResponse(responseCode = "404", description = "User not found",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @RequireCapability("USER.WRITE")
    @PatchMapping("/{userId}/activate")
    public ResponseEntity<UserResponse> activateUser(
            @PathVariable UUID userId,
            @RequestParam UUID tenantId) {

        return ResponseEntity.ok(userService.activateUser(tenantId, userId));
    }

    @Operation(summary = "Deactivate a user", description = "Sets the user status to INACTIVE.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "User deactivated",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = UserResponse.class))),
            @ApiResponse(responseCode = "404", description = "User not found",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @RequireCapability("USER.WRITE")
    @PatchMapping("/{userId}/deactivate")
    public ResponseEntity<UserResponse> deactivateUser(
            @PathVariable UUID userId,
            @RequestParam UUID tenantId) {

        return ResponseEntity.ok(userService.deactivateUser(tenantId, userId));
    }

    @Operation(summary = "Suspend a user", description = "Sets the user status to SUSPENDED.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "User suspended",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = UserResponse.class))),
            @ApiResponse(responseCode = "404", description = "User not found",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @RequireCapability("USER.WRITE")
    @PatchMapping("/{userId}/suspend")
    public ResponseEntity<UserResponse> suspendUser(
            @PathVariable UUID userId,
            @RequestParam UUID tenantId) {

        return ResponseEntity.ok(userService.suspendUser(tenantId, userId));
    }

    @Operation(summary = "Archive a user", description = "Sets the user status to ARCHIVED.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "User archived",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = UserResponse.class))),
            @ApiResponse(responseCode = "404", description = "User not found",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @RequireCapability("USER.DELETE")
    @PatchMapping("/{userId}/archive")
    public ResponseEntity<UserResponse> archiveUser(
            @PathVariable UUID userId,
            @RequestParam UUID tenantId) {

        return ResponseEntity.ok(userService.archiveUser(tenantId, userId));
    }
}
