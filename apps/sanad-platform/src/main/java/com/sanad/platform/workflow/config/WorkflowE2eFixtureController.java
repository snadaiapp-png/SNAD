package com.sanad.platform.workflow.config;

import com.sanad.platform.security.authorization.RequireCapability;
import com.sanad.platform.workflow.application.WorkflowExecutionService;
import com.sanad.platform.workflow.application.WorkflowIncidentService;
import com.sanad.platform.workflow.domain.WorkflowIncident;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
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
 * profile. It only creates prerequisites that do not have a public business
 * command (currently an incident). The release spec must still exercise the
 * normal production acknowledge/resolve endpoints for the business actions.
 */
@RestController
@Profile("workflow-e2e")
@RequestMapping("/api/v1/workflows/e2e-fixtures")
public class WorkflowE2eFixtureController {

    private final WorkflowExecutionService executionService;
    private final WorkflowIncidentService incidentService;

    public WorkflowE2eFixtureController(
            WorkflowExecutionService executionService,
            WorkflowIncidentService incidentService) {
        this.executionService = executionService;
        this.incidentService = incidentService;
    }

    public record IncidentFixtureRequest(UUID workflowInstanceId, UUID workflowStepInstanceId) {}

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
}
