package com.sanad.platform.organization.api;

import com.sanad.platform.organization.dto.CreateOrganizationRequest;
import com.sanad.platform.organization.dto.OrganizationResponse;
import com.sanad.platform.organization.dto.UpdateOrganizationRequest;
import com.sanad.platform.organization.service.OrganizationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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
import java.util.UUID;

/**
 * REST controller for the Organization aggregate.
 *
 * <p>This is the transport-layer adapter that exposes the
 * {@link OrganizationService} use cases over HTTP. It is intentionally
 * thin: it accepts a validated {@link CreateOrganizationRequest},
 * delegates to the service, and returns an {@link OrganizationResponse}
 * with the appropriate HTTP status code.</p>
 *
 * <p>Stage 0 (this controller): only the create use case is exposed.
 * Future stages will add GET (single + list), PATCH (rename, status
 * transitions), and DELETE (archive) endpoints.</p>
 *
 * <h2>OpenAPI Contract</h2>
 * <p>All endpoints are auto-documented by springdoc-openapi at
 * {@code /v3/api-docs} and surfaced in Swagger UI at
 * {@code /swagger-ui.html}. The annotations on this class drive the
 * generated OpenAPI 3.0.1 document.</p>
 */
@RestController
@RequestMapping("/api/v1/organizations")
@Tag(
        name = "Organizations",
        description = "Create and manage Organization aggregates belonging to Tenants. " +
                "An Organization is the operational container for future ERP, CRM, HRM, " +
                "Accounting, and Commerce modules."
)
public class OrganizationController {

    private final OrganizationService organizationService;

    public OrganizationController(OrganizationService organizationService) {
        this.organizationService = organizationService;
    }

    /**
     * Create a new Organization under an existing Tenant.
     *
     * <p>The request body is validated via Bean Validation annotations
     * on {@link CreateOrganizationRequest} before reaching the service
     * layer. If validation fails, a {@code 400 Bad Request} is returned
     * with a structured error body describing the failing fields.</p>
     *
     * @param request the validated create request
     * @return {@code 201 Created} with the persisted Organization in the body
     *         and a {@code Location} header pointing to the new resource
     */
    @Operation(
            summary = "Create a new Organization",
            description = "Registers a new Organization under an existing Tenant. " +
                    "The Organization is created with status ACTIVE. " +
                    "The (tenantId, name) pair must be unique; otherwise a 409 is returned."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "201",
                    description = "Organization created successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = OrganizationResponse.class)
                    ),
                    headers = @Header(
                            name = "Location",
                            description = "URI of the newly created Organization resource",
                            schema = @Schema(type = "string", format = "uri")
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Bad Request - request body failed validation " +
                            "(missing tenantId, blank name, name or description too long)",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = com.sanad.platform.organization.api.ApiErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Tenant Not Found - the referenced tenantId does not exist",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = com.sanad.platform.organization.api.ApiErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "Organization Already Exists - an Organization with the same " +
                            "name already exists under the same Tenant",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = com.sanad.platform.organization.api.ApiErrorResponse.class)
                    )
            )
    })
    @PostMapping
    public ResponseEntity<OrganizationResponse> createOrganization(
            @Valid @RequestBody CreateOrganizationRequest request) {

        OrganizationResponse created = organizationService.createOrganization(request);

        // Build the Location header for the 201 response
        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.getId())
                .toUri();

        return ResponseEntity
                .created(location)
                .body(created);
    }

    /**
     * Fetch a single Organization by ID, scoped to a Tenant.
     *
     * <p>The {@code tenantId} query parameter is REQUIRED. If omitted, the
     * request fails with {@code 400 Bad Request}. If the (tenantId, id) pair
     * does not match any Organization, the request fails with
     * {@code 404 Not Found}.</p>
     *
     * @param id       the organization id (path variable)
     * @param tenantId the tenant scope (required query parameter)
     * @return the matching Organization
     */
    @Operation(
            summary = "Get an Organization by ID",
            description = "Fetch a single Organization scoped to a specific Tenant. " +
                    "The tenantId query parameter is required. " +
                    "If the (tenantId, id) pair does not match any Organization, " +
                    "a 404 is returned."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Organization found",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = OrganizationResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Bad Request - missing required tenantId query parameter",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Organization Not Found - no Organization with the given id " +
                            "exists under the given tenant",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiErrorResponse.class)
                    )
            )
    })
    @GetMapping("/{id}")
    public ResponseEntity<OrganizationResponse> getOrganization(
            @Parameter(description = "Organization UUID", required = true)
            @PathVariable UUID id,
            @Parameter(description = "Tenant UUID (scope)", required = true)
            @RequestParam UUID tenantId) {

        return ResponseEntity.ok(organizationService.getOrganization(tenantId, id));
    }

    /**
     * List all Organizations belonging to a specific Tenant.
     *
     * <p>The {@code tenantId} query parameter is REQUIRED. If omitted, the
     * request fails with {@code 400 Bad Request}. If the tenant has no
     * organizations, an empty array is returned with {@code 200 OK}.</p>
     *
     * @param tenantId the tenant scope (required query parameter)
     * @return a list of Organizations (possibly empty)
     */
    @Operation(
            summary = "List Organizations for a Tenant",
            description = "Returns all Organizations belonging to the specified Tenant. " +
                    "The tenantId query parameter is required. " +
                    "If the tenant has no organizations, an empty array is returned."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Organization list (possibly empty)",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = OrganizationResponse[].class)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Bad Request - missing required tenantId query parameter",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiErrorResponse.class)
                    )
            )
    })
    @GetMapping
    public ResponseEntity<List<OrganizationResponse>> listOrganizations(
            @Parameter(description = "Tenant UUID (scope)", required = true)
            @RequestParam UUID tenantId) {

        return ResponseEntity.ok(organizationService.listOrganizations(tenantId));
    }

    // ============================================================
    // EXEC-PROMPT-009 — Update + Status Management
    // ============================================================

    /**
     * PUT /api/v1/organizations/{id}?tenantId=... — update name + description.
     */
    @Operation(
            summary = "Update an Organization's name and description",
            description = "Updates the mutable fields of an Organization. " +
                    "The tenant relationship and status are NOT changed. " +
                    "If the new name conflicts with another organization under the same tenant, " +
                    "a 409 Conflict is returned."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Organization updated successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = OrganizationResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Bad Request - request body failed validation or tenantId missing",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Organization Not Found - no Organization with the given id exists under this tenant",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "Organization Already Exists - another organization under the same tenant already uses the new name",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiErrorResponse.class)
                    )
            )
    })
    @PutMapping("/{id}")
    public ResponseEntity<OrganizationResponse> updateOrganization(
            @Parameter(description = "Organization UUID", required = true)
            @PathVariable UUID id,
            @Parameter(description = "Tenant UUID (scope)", required = true)
            @RequestParam UUID tenantId,
            @Valid @RequestBody UpdateOrganizationRequest request) {

        return ResponseEntity.ok(organizationService.updateOrganization(tenantId, id, request));
    }

    /**
     * PATCH /api/v1/organizations/{id}/activate?tenantId=... — set status to ACTIVE.
     */
    @Operation(
            summary = "Activate an Organization",
            description = "Sets the Organization status to ACTIVE. Idempotent: activating an already-ACTIVE " +
                    "organization is a no-op that returns the current state."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Organization activated successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = OrganizationResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Bad Request - missing required tenantId query parameter",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Organization Not Found",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiErrorResponse.class)
                    )
            )
    })
    @PatchMapping("/{id}/activate")
    public ResponseEntity<OrganizationResponse> activateOrganization(
            @Parameter(description = "Organization UUID", required = true)
            @PathVariable UUID id,
            @Parameter(description = "Tenant UUID (scope)", required = true)
            @RequestParam UUID tenantId) {

        return ResponseEntity.ok(organizationService.activateOrganization(tenantId, id));
    }

    /**
     * PATCH /api/v1/organizations/{id}/deactivate?tenantId=... — set status to INACTIVE.
     */
    @Operation(
            summary = "Deactivate an Organization",
            description = "Sets the Organization status to INACTIVE. Idempotent: deactivating an already-INACTIVE " +
                    "organization is a no-op that returns the current state."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Organization deactivated successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = OrganizationResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Bad Request - missing required tenantId query parameter",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Organization Not Found",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiErrorResponse.class)
                    )
            )
    })
    @PatchMapping("/{id}/deactivate")
    public ResponseEntity<OrganizationResponse> deactivateOrganization(
            @Parameter(description = "Organization UUID", required = true)
            @PathVariable UUID id,
            @Parameter(description = "Tenant UUID (scope)", required = true)
            @RequestParam UUID tenantId) {

        return ResponseEntity.ok(organizationService.deactivateOrganization(tenantId, id));
    }
}
