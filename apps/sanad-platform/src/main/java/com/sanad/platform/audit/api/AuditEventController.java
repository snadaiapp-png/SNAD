package com.sanad.platform.audit.api;

import com.sanad.platform.audit.domain.AuditOutcome;
import com.sanad.platform.audit.dto.AuditEventResponse;
import com.sanad.platform.audit.service.AuditIntegrityVerificationService;
import com.sanad.platform.audit.service.AuditQueryService;
import com.sanad.platform.security.authorization.RequireCapability;
import com.sanad.platform.security.tenant.TenantResolver;
import com.sanad.platform.shared.api.PageRequestParams;
import com.sanad.platform.shared.api.PageResponse;
import com.sanad.platform.shared.api.PageResponseBuilder;
import com.sanad.platform.shared.api.SortAllowlist;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Stage 05 §22 — Tenant-scoped audit query API.
 *
 * <p>Endpoints:</p>
 * <ul>
 *   <li>{@code GET /api/v1/audit-events} — paginated, filtered list</li>
 *   <li>{@code GET /api/v1/audit-events/{id}} — single event by ID</li>
 *   <li>{@code GET /api/v1/audit-events/integrity} — hash-chain verification</li>
 * </ul>
 *
 * <p>All endpoints require the {@code AUDIT.READ} capability
 * (integrity verification requires {@code AUDIT.INTEGRITY_VERIFY}).
 * The tenant scope is taken from the verified TenantContext —
 * client-supplied {@code tenantId} parameters are ignored.</p>
 */
@RestController
@RequestMapping("/api/v1/audit-events")
@Tag(name = "Audit Events", description = "Tenant-scoped, immutable audit log query and integrity verification")
public class AuditEventController {

    private final AuditQueryService queryService;
    private final TenantResolver tenantResolver;

    public AuditEventController(AuditQueryService queryService,
                                 TenantResolver tenantResolver) {
        this.queryService = queryService;
        this.tenantResolver = tenantResolver;
    }

    @Operation(summary = "List audit events", description =
            "Returns a paginated, filtered list of audit events for the current tenant. "
                    + "All filters are optional. Results are ordered by occurredAt DESC.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Paginated audit events"),
            @ApiResponse(responseCode = "401", description = "Authentication required"),
            @ApiResponse(responseCode = "403", description = "Access denied — AUDIT.READ capability required")
    })
    @RequireCapability("AUDIT.READ")
    @GetMapping
    public ResponseEntity<PageResponse<AuditEventResponse>> listAuditEvents(
            @Parameter(description = "Filter by actor user ID") @RequestParam(required = false) UUID actorUserId,
            @Parameter(description = "Filter by action") @RequestParam(required = false) String action,
            @Parameter(description = "Filter by resource type") @RequestParam(required = false) String resourceType,
            @Parameter(description = "Filter by resource ID") @RequestParam(required = false) String resourceId,
            @Parameter(description = "Filter by outcome") @RequestParam(required = false) AuditOutcome outcome,
            @Parameter(description = "Filter by correlation ID") @RequestParam(required = false) String correlationId,
            @Parameter(description = "Filter: occurredAt >= from (ISO-8601)") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @Parameter(description = "Filter: occurredAt < to (ISO-8601)") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @Valid PageRequestParams params) {
        Set<String> allowedSortFields = Set.of("occurredAt", "action", "resourceType", "operation", "outcome");
        Pageable pageable = SortAllowlist.toPageable(params, allowedSortFields);
        Page<AuditEventResponse> page = queryService.list(
                actorUserId, action, resourceType, resourceId, outcome,
                correlationId, from, to, pageable);
        return ResponseEntity.ok(PageResponseBuilder.from(page, page.getContent()));
    }

    @Operation(summary = "Get a single audit event by ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Audit event found"),
            @ApiResponse(responseCode = "404", description = "Audit event not found (or belongs to another tenant)")
    })
    @RequireCapability("AUDIT.READ")
    @GetMapping("/{id}")
    public ResponseEntity<AuditEventResponse> getAuditEvent(
            @Parameter(description = "Audit event UUID", required = true) @PathVariable UUID id) {
        return ResponseEntity.ok(queryService.getById(id));
    }

    @Operation(summary = "Verify audit hash-chain integrity",
            description = "Recomputes the hash chain for the current tenant and returns the verification result. "
                    + "Requires AUDIT.INTEGRITY_VERIFY capability.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Verification completed (check result.valid for pass/fail)"),
            @ApiResponse(responseCode = "403", description = "AUDIT.INTEGRITY_VERIFY capability required")
    })
    @RequireCapability("AUDIT.INTEGRITY_VERIFY")
    @GetMapping("/integrity")
    public ResponseEntity<AuditIntegrityVerificationService.VerificationResult> verifyIntegrity() {
        return ResponseEntity.ok(queryService.verifyIntegrity());
    }
}
