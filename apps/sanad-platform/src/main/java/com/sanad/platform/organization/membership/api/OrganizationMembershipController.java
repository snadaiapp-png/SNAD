package com.sanad.platform.organization.membership.api;

import com.sanad.platform.shared.api.ApiErrorResponse;
import com.sanad.platform.shared.api.PageRequestParams;
import com.sanad.platform.shared.api.PageResponse;
import com.sanad.platform.shared.api.PageResponseBuilder;
import com.sanad.platform.shared.api.SortAllowlist;
import com.sanad.platform.security.authorization.RequireCapability;
import com.sanad.platform.organization.membership.dto.InviteOrganizationMemberRequest;
import com.sanad.platform.organization.membership.dto.OrganizationMembershipResponse;
import com.sanad.platform.organization.membership.service.OrganizationMembershipService;
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
 * REST controller for Organization Memberships.
 *
 * <p>All endpoints are nested under a specific organization:
 * {@code /api/v1/organizations/{organizationId}/memberships}. The
 * {@code tenantId} is always required as a query parameter to enforce
 * tenant isolation at the transport layer.</p>
 */
@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/memberships")
@Tag(
        name = "Organization Memberships",
        description = "Manage memberships (invitations) for an Organization. " +
                "Every endpoint is tenant-scoped via the required tenantId query parameter."
)
public class OrganizationMembershipController {

    private final OrganizationMembershipService membershipService;
    private final com.sanad.platform.security.tenant.TenantResolver tenantResolver;

    public OrganizationMembershipController(OrganizationMembershipService membershipService,
                                             com.sanad.platform.security.tenant.TenantResolver tenantResolver) {
        this.membershipService = membershipService;
        this.tenantResolver = tenantResolver;
    }

    // ============================================================
    // POST / - invite member
    // ============================================================

    @Operation(
            summary = "Invite a member to an organization",
            description = "Creates a new membership with status INVITED. " +
                    "The (tenantId, organizationId, email) tuple must be unique."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Member invited successfully",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = OrganizationMembershipResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request - validation failed or missing tenantId",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Tenant or Organization not found",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Membership already exists for this email",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @RequireCapability("MEMBERSHIP.CREATE")
    @PostMapping
    public ResponseEntity<OrganizationMembershipResponse> inviteMember(
            @Parameter(description = "Organization UUID", required = true)
            @PathVariable UUID organizationId,
            @Parameter(description = "Tenant UUID (scope)", required = true)
            @RequestParam UUID tenantId,
            @Valid @RequestBody InviteOrganizationMemberRequest request) {

        // Stage 04A §9: validate client tenantId against verified TenantContext.
        java.util.UUID verifiedTenantId = tenantResolver.validateClientSelector(tenantId);
        OrganizationMembershipResponse created = membershipService.inviteMember(verifiedTenantId, request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{membershipId}")
                .buildAndExpand(created.getId())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    // ============================================================
    // GET / - list memberships
    // ============================================================

    @Operation(summary = "List all memberships for an organization")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Membership list (possibly empty)",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = OrganizationMembershipResponse[].class))),
            @ApiResponse(responseCode = "400", description = "Missing tenantId",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Organization not found",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @RequireCapability("MEMBERSHIP.READ")
    @GetMapping
    public ResponseEntity<PageResponse<OrganizationMembershipResponse>> listMemberships(
            @Parameter(description = "Organization UUID", required = true)
            @PathVariable UUID organizationId,
            @Parameter(description = "Tenant UUID (scope)", required = true)
            @RequestParam UUID tenantId,
            @Valid PageRequestParams params) {
        Set<String> allowedSortFields = Set.of(
                "id", "organizationId", "userId", "email", "status", "createdAt", "updatedAt");
        Pageable pageable = SortAllowlist.toPageable(params, allowedSortFields);
        Page<OrganizationMembershipResponse> page =
                membershipService.listMemberships(tenantId, organizationId, pageable);
        return ResponseEntity.ok(PageResponseBuilder.from(page, page.getContent()));
    }

    // ============================================================
    // GET /{membershipId} - get membership
    // ============================================================

    @Operation(summary = "Get a membership by ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Membership found",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = OrganizationMembershipResponse.class))),
            @ApiResponse(responseCode = "400", description = "Missing tenantId",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Membership not found",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @RequireCapability("MEMBERSHIP.READ")
    @GetMapping("/{membershipId}")
    public ResponseEntity<OrganizationMembershipResponse> getMembership(
            @Parameter(description = "Organization UUID", required = true)
            @PathVariable UUID organizationId,
            @Parameter(description = "Membership UUID", required = true)
            @PathVariable UUID membershipId,
            @Parameter(description = "Tenant UUID (scope)", required = true)
            @RequestParam UUID tenantId) {

        return ResponseEntity.ok(membershipService.getMembership(tenantId, organizationId, membershipId));
    }

    // ============================================================
    // PATCH /{membershipId}/activate
    // ============================================================

    @Operation(summary = "Activate a membership (status = ACTIVE)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Membership activated",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = OrganizationMembershipResponse.class))),
            @ApiResponse(responseCode = "400", description = "Missing tenantId",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Membership not found",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @RequireCapability("MEMBERSHIP.WRITE")
    @PatchMapping("/{membershipId}/activate")
    public ResponseEntity<OrganizationMembershipResponse> activateMembership(
            @PathVariable UUID organizationId,
            @PathVariable UUID membershipId,
            @RequestParam UUID tenantId) {

        return ResponseEntity.ok(membershipService.activateMembership(tenantId, organizationId, membershipId));
    }

    // ============================================================
    // PATCH /{membershipId}/deactivate
    // ============================================================

    @Operation(summary = "Deactivate a membership (status = INACTIVE)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Membership deactivated",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = OrganizationMembershipResponse.class))),
            @ApiResponse(responseCode = "400", description = "Missing tenantId",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Membership not found",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @RequireCapability("MEMBERSHIP.WRITE")
    @PatchMapping("/{membershipId}/deactivate")
    public ResponseEntity<OrganizationMembershipResponse> deactivateMembership(
            @PathVariable UUID organizationId,
            @PathVariable UUID membershipId,
            @RequestParam UUID tenantId) {

        return ResponseEntity.ok(membershipService.deactivateMembership(tenantId, organizationId, membershipId));
    }

    // ============================================================
    // PATCH /{membershipId}/remove (soft delete)
    // ============================================================

    @Operation(summary = "Remove a membership (soft delete, status = REMOVED)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Membership removed (soft delete)",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = OrganizationMembershipResponse.class))),
            @ApiResponse(responseCode = "400", description = "Missing tenantId",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Membership not found",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @RequireCapability("MEMBERSHIP.DELETE")
    @PatchMapping("/{membershipId}/remove")
    public ResponseEntity<OrganizationMembershipResponse> removeMembership(
            @PathVariable UUID organizationId,
            @PathVariable UUID membershipId,
            @RequestParam UUID tenantId) {

        return ResponseEntity.ok(membershipService.removeMembership(tenantId, organizationId, membershipId));
    }
}
