package com.sanad.platform.tenant.api;

import com.sanad.platform.organization.api.ApiErrorResponse;
import com.sanad.platform.tenant.dto.CreateTenantRequest;
import com.sanad.platform.tenant.dto.TenantResponse;
import com.sanad.platform.tenant.dto.UpdateTenantRequest;
import com.sanad.platform.tenant.service.TenantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
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
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/**
 * REST controller for the Tenant aggregate.
 *
 * <p>Unlike {@code OrganizationController}, the Tenant endpoints are NOT
 * tenant-scoped (there is no {@code tenantId} query parameter) because
 * a Tenant IS the top-level isolation boundary — it has no parent
 * aggregate to be scoped by.</p>
 */
@RestController
@RequestMapping("/api/v1/tenants")
@Tag(
        name = "Tenants",
        description = "Create and manage Tenant aggregates. A Tenant is the top-level " +
                "isolation boundary in the SANAD platform; every other aggregate " +
                "(Organization, future ERP/CRM/HRM modules) belongs to exactly one Tenant."
)
public class TenantController {

    private final TenantService tenantService;

    public TenantController(TenantService tenantService) {
        this.tenantService = tenantService;
    }

    @Operation(
            summary = "Create a new Tenant",
            description = "Registers a new Tenant with status ACTIVE. The subdomain must " +
                    "be globally unique; otherwise a 409 Conflict is returned."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Tenant created successfully",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = TenantResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request - validation failed",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Tenant Already Exists - subdomain in use",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PostMapping
    public ResponseEntity<TenantResponse> createTenant(@Valid @RequestBody CreateTenantRequest request) {
        TenantResponse created = tenantService.createTenant(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(created.getId()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @Operation(summary = "List all Tenants")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Tenant list (possibly empty)",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = TenantResponse[].class)))
    })
    @GetMapping
    public ResponseEntity<List<TenantResponse>> listTenants() {
        return ResponseEntity.ok(tenantService.listTenants());
    }

    @Operation(summary = "Get a Tenant by ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Tenant found",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = TenantResponse.class))),
            @ApiResponse(responseCode = "404", description = "Tenant Not Found",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @GetMapping("/{id}")
    public ResponseEntity<TenantResponse> getTenant(
            @Parameter(description = "Tenant UUID", required = true) @PathVariable UUID id) {
        return ResponseEntity.ok(tenantService.getTenant(id));
    }

    @Operation(summary = "Update a Tenant's name (subdomain is immutable)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Tenant updated",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = TenantResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Tenant Not Found",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PutMapping("/{id}")
    public ResponseEntity<TenantResponse> updateTenant(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateTenantRequest request) {
        return ResponseEntity.ok(tenantService.updateTenant(id, request));
    }

    @Operation(summary = "Activate a Tenant (status = ACTIVE). Idempotent.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Tenant activated",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = TenantResponse.class))),
            @ApiResponse(responseCode = "404", description = "Tenant Not Found",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PatchMapping("/{id}/activate")
    public ResponseEntity<TenantResponse> activateTenant(@PathVariable UUID id) {
        return ResponseEntity.ok(tenantService.activateTenant(id));
    }

    @Operation(summary = "Deactivate a Tenant (status = INACTIVE). Idempotent.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Tenant deactivated",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = TenantResponse.class))),
            @ApiResponse(responseCode = "404", description = "Tenant Not Found",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PatchMapping("/{id}/deactivate")
    public ResponseEntity<TenantResponse> deactivateTenant(@PathVariable UUID id) {
        return ResponseEntity.ok(tenantService.deactivateTenant(id));
    }

    @Operation(summary = "Archive a Tenant (soft delete via status = ARCHIVED). Idempotent.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Tenant archived",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = TenantResponse.class))),
            @ApiResponse(responseCode = "404", description = "Tenant Not Found",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PatchMapping("/{id}/archive")
    public ResponseEntity<TenantResponse> archiveTenant(@PathVariable UUID id) {
        return ResponseEntity.ok(tenantService.archiveTenant(id));
    }
}
