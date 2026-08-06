package com.sanad.platform.crm.ownership.web;

import com.sanad.platform.crm.ownership.application.WorkloadManagementUseCases;
import com.sanad.platform.crm.ownership.application.WorkloadManagementUseCases.AssignWorkCommand;
import com.sanad.platform.crm.ownership.domain.workload.WorkloadAssignment;
import com.sanad.platform.crm.ownership.domain.workload.WorkloadStatus;
import com.sanad.platform.crm.ownership.web.TeamModels.AssignWorkRequest;
import com.sanad.platform.security.authorization.RequireCapability;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * V1 REST controller for CRM Workload Assignments.
 *
 * <p>Mounted under {@code /api/v1/crm/workload}.
 */
@RestController
@RequestMapping("/api/v1/crm/workload")
public class WorkloadController {

    private final WorkloadManagementUseCases workload;

    public WorkloadController(WorkloadManagementUseCases workload) {
        this.workload = workload;
    }

    @RequireCapability("CRM.WORKLOAD.READ")
    @GetMapping
    public List<Map<String, Object>> listWorkload(
            Authentication authentication,
            @RequestParam(required = false) UUID staffId,
            @RequestParam(required = false) UUID serviceId,
            @RequestParam(required = false) String status) {
        UUID tenantId = tenantId(authentication);
        if (staffId != null && status != null) {
            WorkloadStatus ws = WorkloadStatus.valueOf(status.toUpperCase());
            return workload.listByStaff(tenantId, staffId, ws)
                    .stream().map(this::toRow).toList();
        }
        if (serviceId != null) {
            return workload.listByService(tenantId, serviceId)
                    .stream().map(this::toRow).toList();
        }
        return List.of();
    }

    @RequireCapability("CRM.WORKLOAD.READ")
    @GetMapping("/hours")
    public Map<String, Object> getHours(
            Authentication authentication,
            @RequestParam UUID staffId,
            @RequestParam String from,
            @RequestParam String to) {
        UUID tenantId = tenantId(authentication);
        LocalDate fromDate = LocalDate.parse(from);
        LocalDate toDate = LocalDate.parse(to);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("staff_id", staffId);
        row.put("estimated_hours", workload.getEstimatedHours(tenantId, staffId, fromDate, toDate));
        row.put("actual_hours", workload.getActualHours(tenantId, staffId, fromDate, toDate));
        return row;
    }

    @RequireCapability("CRM.WORKLOAD.MANAGE")
    @PostMapping
    public ResponseEntity<Map<String, Object>> assignWork(
            Authentication authentication,
            @Valid @RequestBody AssignWorkRequest request) {
        UUID tenantId = tenantId(authentication);
        UUID actorId = userId(authentication);

        WorkloadAssignment created = workload.assignWork(tenantId, actorId,
                new AssignWorkCommand(
                        request.staffId(),
                        request.serviceId(),
                        request.jobId(),
                        request.estimatedHours(),
                        request.startDate(),
                        request.endDate()));

        return ResponseEntity.status(HttpStatus.CREATED).body(toRow(created));
    }

    @RequireCapability("CRM.WORKLOAD.MANAGE")
    @PatchMapping("/{workloadId}/reassign")
    public Map<String, Object> reassignWork(
            Authentication authentication,
            @PathVariable UUID workloadId,
            @RequestBody Map<String, UUID> body) {
        UUID tenantId = tenantId(authentication);
        UUID actorId = userId(authentication);
        UUID newStaffId = body.get("new_staff_id");
        return toRow(workload.reassignWork(tenantId, actorId, workloadId, newStaffId));
    }

    @RequireCapability("CRM.WORKLOAD.MANAGE")
    @PatchMapping("/{workloadId}/release")
    public Map<String, Object> releaseAssignment(Authentication authentication,
                                                   @PathVariable UUID workloadId) {
        return toRow(workload.releaseAssignment(tenantId(authentication), userId(authentication), workloadId));
    }

    private Map<String, Object> toRow(WorkloadAssignment w) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", w.id());
        row.put("tenant_id", w.tenantId());
        row.put("staff_id", w.staffId());
        row.put("service_id", w.serviceId());
        row.put("job_id", w.jobId());
        row.put("estimated_hours", w.estimatedHours());
        row.put("actual_hours", w.actualHours());
        row.put("status", w.status().name());
        row.put("start_date", w.startDate() != null ? w.startDate().toString() : null);
        row.put("end_date", w.endDate() != null ? w.endDate().toString() : null);
        row.put("created_by", w.createdBy());
        row.put("updated_by", w.updatedBy());
        row.put("created_at", toIso(w.createdAt()));
        row.put("updated_at", toIso(w.updatedAt()));
        row.put("version", w.version());
        return row;
    }

    private static String toIso(Instant v) {
        return v == null ? null : v.toString();
    }

    private static UUID tenantId(Authentication authentication) {
        return context(authentication, "tenant_id");
    }

    private static UUID userId(Authentication authentication) {
        return context(authentication, "user_id");
    }

    private static UUID context(Authentication authentication, String key) {
        if (authentication == null
                || !authentication.isAuthenticated()
                || !(authentication.getDetails() instanceof Map<?, ?> details)
                || details.get(key) == null) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "Authenticated CRM context is required");
        }
        try {
            return UUID.fromString(details.get(key).toString());
        } catch (IllegalArgumentException exception) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "Invalid authenticated CRM context", exception);
        }
    }
}
