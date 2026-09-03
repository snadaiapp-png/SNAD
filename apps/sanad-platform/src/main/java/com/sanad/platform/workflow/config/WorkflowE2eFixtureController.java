package com.sanad.platform.workflow.config;

import com.sanad.platform.security.authorization.RequireCapability;
import com.sanad.platform.workflow.application.WorkflowExecutionService;
import com.sanad.platform.workflow.application.WorkflowIncidentService;
import com.sanad.platform.workflow.domain.WorkflowIncident;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

import static com.sanad.platform.security.SecurityContextUtils.tenantId;

/**
 * Setup-only endpoints for the Workflow browser release gate.
 *
 * <p>This controller is impossible to load outside the {@code workflow-e2e}
 * profile. It creates prerequisites that do not have a convenient public
 * business command in the Workflow surface. The release spec must still use
 * the normal production endpoints for claim/complete/reassign/incident actions.
 */
@RestController
@Profile("workflow-e2e")
@RequestMapping("/api/v1/workflows/e2e-fixtures")
public class WorkflowE2eFixtureController {

    private final WorkflowExecutionService executionService;
    private final WorkflowIncidentService incidentService;
    private final JdbcTemplate jdbc;

    public WorkflowE2eFixtureController(
            WorkflowExecutionService executionService,
            WorkflowIncidentService incidentService,
            JdbcTemplate jdbc) {
        this.executionService = executionService;
        this.incidentService = incidentService;
        this.jdbc = jdbc;
    }

    public record IncidentFixtureRequest(UUID workflowInstanceId, UUID workflowStepInstanceId) {}
    public record UserStatusFixtureRequest(UUID userId, String status) {}

    @PostMapping("/incidents")
    @RequireCapability("WORKFLOW.INCIDENT_MANAGE")
    public ResponseEntity<Map<String, Object>> createIncident(
            Authentication auth, @RequestBody IncidentFixtureRequest request) {
        UUID tenant = tenantId(auth);
        executionService.findById(tenant, request.workflowInstanceId())
                .orElseThrow(() -> new IllegalArgumentException("WorkflowInstance not found"));
        executionService.findStepInstances(tenant, request.workflowInstanceId()).stream()
                .filter(step -> step.id().equals(request.workflowStepInstanceId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("WorkflowStepInstance not found"));

        WorkflowIncident incident = incidentService.open(
                tenant,
                request.workflowInstanceId(),
                request.workflowStepInstanceId(),
                "WORKFLOW_E2E",
                WorkflowIncident.Severity.HIGH,
                "E2E_RELEASE_GATE");

        return ResponseEntity.ok(Map.of(
                "id", incident.id(),
                "status", incident.status().name(),
                "workflowInstanceId", incident.workflowInstanceId(),
                "workflowStepInstanceId", incident.workflowStepInstanceId()));
    }

    @PostMapping("/user-status")
    @RequireCapability("WORKFLOW.REASSIGN")
    public ResponseEntity<Map<String, Object>> setUserStatus(
            Authentication auth, @RequestBody UserStatusFixtureRequest request) {
        UUID tenant = tenantId(auth);
        String status = request.status() != null ? request.status().trim().toUpperCase() : "";
        if (!status.equals("ACTIVE") && !status.equals("INACTIVE") && !status.equals("SUSPENDED")) {
            throw new IllegalArgumentException("Unsupported E2E user status: " + request.status());
        }
        int changed = jdbc.update("""
                UPDATE users SET status = ?, updated_at = NOW()
                WHERE tenant_id = ? AND id = ?
                """, status, tenant, request.userId());
        if (changed != 1) {
            throw new IllegalArgumentException("E2E user not found in tenant");
        }
        String employeeStatus = jdbc.queryForObject("""
                SELECT status FROM hr_employees WHERE tenant_id = ? AND user_id = ?
                """, String.class, tenant, request.userId());
        return ResponseEntity.ok(Map.of(
                "userId", request.userId(),
                "userStatus", status,
                "employeeStatus", employeeStatus));
    }
}
