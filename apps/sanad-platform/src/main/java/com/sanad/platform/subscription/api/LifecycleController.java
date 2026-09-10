package com.sanad.platform.subscription.api;

import com.sanad.platform.security.authorization.ControlPlaneAccessGuard;
import com.sanad.platform.security.authorization.RequireCapability;
import com.sanad.platform.subscription.change.SubscriptionChangeService;
import com.sanad.platform.subscription.lifecycle.SubscriptionCommandService;
import com.sanad.platform.subscription.provisioning.ProvisioningJobResponse;
import com.sanad.platform.subscription.provisioning.ProvisioningJobRunner;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Executive API for subscription lifecycle commands, item-aware change
 * previews, and provisioning jobs.
 *
 * <p>Subscription status is NEVER written directly by callers — the frontend
 * invokes commands and the backend enforces transition legality.
 */
@RestController
@RequestMapping("/api/v1/executive")
public class LifecycleController {

    public record LifecycleCommandRequest(@NotBlank String reason) {
    }

    public record ChangePreviewRequest(UUID targetPlanVersionId, String countryCode) {
    }

    public record ExecuteChangeRequest(
            @NotNull UUID targetPlanVersionId,
            String countryCode,
            String reason) {
    }

    private final ControlPlaneAccessGuard accessGuard;
    private final SubscriptionCommandService commandService;
    private final SubscriptionChangeService changeService;
    private final ProvisioningJobRunner provisioningRunner;
    private final JdbcTemplate jdbc;

    public LifecycleController(ControlPlaneAccessGuard accessGuard,
                               SubscriptionCommandService commandService,
                               SubscriptionChangeService changeService,
                               ProvisioningJobRunner provisioningRunner,
                               JdbcTemplate jdbc) {
        this.accessGuard = accessGuard;
        this.commandService = commandService;
        this.changeService = changeService;
        this.provisioningRunner = provisioningRunner;
        this.jdbc = jdbc;
    }

    @PostMapping("/subscriptions/{id}/lifecycle/{command}")
    @RequireCapability("EXECUTIVE_MANAGE")
    public ResponseEntity<SubscriptionCommandService.CommandResult> executeCommand(
            @PathVariable UUID id,
            @PathVariable String command,
            @Valid @RequestBody LifecycleCommandRequest request,
            Authentication authentication) {
        accessGuard.require(authentication);
        return ResponseEntity.ok(commandService.execute(id, command.toUpperCase(), request.reason()));
    }

    @PostMapping("/subscriptions/{id}/change-preview")
    @RequireCapability("EXECUTIVE_VIEW")
    public ResponseEntity<SubscriptionChangeService.ChangePreview> previewChange(
            @PathVariable UUID id,
            @Valid @RequestBody ChangePreviewRequest request,
            Authentication authentication) {
        accessGuard.require(authentication);
        return ResponseEntity.ok(changeService.preview(
                id, request.targetPlanVersionId(),
                request.countryCode() == null ? "GLOBAL" : request.countryCode(),
                java.time.Instant.now()));
    }

    @PostMapping("/subscriptions/{id}/changes")
    @RequireCapability("EXECUTIVE_MANAGE")
    public ResponseEntity<SubscriptionChangeService.ChangeResult> executeChange(
            @PathVariable UUID id,
            @Valid @RequestBody ExecuteChangeRequest request,
            Authentication authentication) {
        accessGuard.require(authentication);
        return ResponseEntity.ok(changeService.execute(
                id, request.targetPlanVersionId(),
                request.countryCode() == null ? "GLOBAL" : request.countryCode(),
                request.reason(), null, null));
    }

    @PostMapping("/subscriptions/{id}/provision")
    @RequireCapability("EXECUTIVE_MANAGE")
    public ResponseEntity<ProvisioningJobRunner.JobOutcome> provision(
            @PathVariable UUID id,
            Authentication authentication) {
        accessGuard.require(authentication);
        UUID jobId = enqueueJob(id, "PROVISION_SUBSCRIPTION");
        return ResponseEntity.ok(provisioningRunner.run(jobId));
    }

    @GetMapping("/provisioning/jobs")
    @RequireCapability("EXECUTIVE_VIEW")
    public ResponseEntity<List<ProvisioningJobResponse>> listJobs(
            @RequestParam(name = "tenantId", required = false) UUID tenantId,
            @RequestParam(name = "status", required = false) String status,
            Authentication authentication) {
        accessGuard.require(authentication);
        // R0C-12 Blocker B-class: typed RowMapper instead of a raw JDBC
        // snake_case Map — the console contract is camelCase (ProvisioningJobResponse).
        List<ProvisioningJobResponse> jobs = jdbc.query(
                "SELECT id, tenant_id, subscription_id, action, status, attempts, "
                        + "started_at, completed_at, error_code, created_at "
                        + "FROM provisioning_jobs "
                        + "WHERE (?::uuid IS NULL OR tenant_id = ?::uuid) "
                        + "AND (?::varchar IS NULL OR status = ?::varchar) "
                        + "ORDER BY created_at DESC LIMIT 200",
                (rs, rowNum) -> new ProvisioningJobResponse(
                        rs.getObject("id", UUID.class),
                        rs.getObject("tenant_id", UUID.class),
                        rs.getObject("subscription_id", UUID.class),
                        rs.getString("action"),
                        rs.getString("status"),
                        rs.getInt("attempts"),
                        toInstant(rs.getTimestamp("started_at")),
                        toInstant(rs.getTimestamp("completed_at")),
                        rs.getString("error_code"),
                        toInstant(rs.getTimestamp("created_at"))),
                tenantId, tenantId, status, status);
        return ResponseEntity.ok(jobs);
    }

    private static java.time.Instant toInstant(java.sql.Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    @PostMapping("/provisioning/jobs/{jobId}/retry")
    @RequireCapability("EXECUTIVE_MANAGE")
    public ResponseEntity<ProvisioningJobRunner.JobOutcome> retryJob(
            @PathVariable UUID jobId,
            Authentication authentication) {
        accessGuard.require(authentication);
        return ResponseEntity.ok(provisioningRunner.run(jobId));
    }

    private UUID enqueueJob(UUID subscriptionId, String action) {
        UUID tenantId = jdbc.queryForObject(
                "SELECT tenant_id FROM tenant_subscriptions WHERE id = ?", UUID.class, subscriptionId);
        UUID jobId = UUID.randomUUID();
        jdbc.update("""
                        INSERT INTO provisioning_jobs (
                            id, tenant_id, subscription_id, action, status, attempts, created_at, updated_at
                        ) VALUES (?, ?, ?, ?, 'PENDING', 0, NOW(), NOW())
                        """,
                jobId, tenantId, subscriptionId, action);
        return jobId;
    }
}
