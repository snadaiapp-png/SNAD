package com.sanad.platform.crm.ownership.web;

import com.sanad.platform.crm.ownership.application.ShiftManagementUseCases;
import com.sanad.platform.crm.ownership.application.ShiftManagementUseCases.CreateShiftAssignmentCommand;
import com.sanad.platform.crm.ownership.application.ShiftManagementUseCases.UpdateShiftAssignmentCommand;
import com.sanad.platform.crm.ownership.domain.scheduling.ShiftAssignment;
import com.sanad.platform.crm.ownership.web.TeamModels.CreateShiftAssignmentRequest;
import com.sanad.platform.crm.ownership.web.TeamModels.UpdateShiftAssignmentRequest;
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
 * V1 REST controller for CRM Shift Assignments.
 *
 * <p>Mounted under {@code /api/v1/crm/shift-assignments}.
 */
@RestController
@RequestMapping("/api/v1/crm/shift-assignments")
public class ShiftAssignmentController {

    private final ShiftManagementUseCases shifts;

    public ShiftAssignmentController(ShiftManagementUseCases shifts) {
        this.shifts = shifts;
    }

    @RequireCapability("CRM.SHIFT.READ")
    @GetMapping
    public List<Map<String, Object>> listAssignments(
            Authentication authentication,
            @RequestParam(required = false) UUID teamId,
            @RequestParam(required = false) UUID staffId,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        UUID tenantId = tenantId(authentication);
        int safeLimit = Math.max(1, Math.min(limit, 200));

        if (staffId != null && from != null && to != null) {
            return shifts.listShiftAssignmentsByStaff(tenantId, staffId,
                    LocalDate.parse(from), LocalDate.parse(to))
                    .stream().map(this::toRow).toList();
        }
        if (teamId != null) {
            return shifts.listShiftAssignmentsByTeam(tenantId, teamId, safeLimit, offset)
                    .stream().map(this::toRow).toList();
        }
        return List.of();
    }

    @RequireCapability("CRM.SHIFT.MANAGE")
    @PostMapping
    public ResponseEntity<Map<String, Object>> assignShift(
            Authentication authentication,
            @Valid @RequestBody CreateShiftAssignmentRequest request) {
        UUID tenantId = tenantId(authentication);
        UUID actorId = userId(authentication);

        ShiftAssignment created = shifts.assignShift(tenantId, actorId,
                new CreateShiftAssignmentCommand(
                        request.teamId(),
                        request.staffId(),
                        request.shiftTemplateId(),
                        request.startDate(),
                        request.endDate()));

        return ResponseEntity.status(HttpStatus.CREATED).body(toRow(created));
    }

    @RequireCapability("CRM.SHIFT.MANAGE")
    @PatchMapping("/{assignmentId}")
    public Map<String, Object> updateAssignment(
            Authentication authentication,
            @PathVariable UUID assignmentId,
            @Valid @RequestBody UpdateShiftAssignmentRequest request) {
        UUID tenantId = tenantId(authentication);
        UUID actorId = userId(authentication);

        ShiftAssignment updated = shifts.updateShiftAssignment(tenantId, actorId, assignmentId,
                new UpdateShiftAssignmentCommand(
                        request.shiftTemplateId(),
                        request.startDate(),
                        request.endDate(),
                        null));

        return toRow(updated);
    }

    @RequireCapability("CRM.SHIFT.MANAGE")
    @PatchMapping("/{assignmentId}/cancel")
    public Map<String, Object> cancelAssignment(Authentication authentication,
                                                 @PathVariable UUID assignmentId) {
        return toRow(shifts.cancelShiftAssignment(tenantId(authentication), userId(authentication), assignmentId));
    }

    private Map<String, Object> toRow(ShiftAssignment a) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", a.id());
        row.put("tenant_id", a.tenantId());
        row.put("team_id", a.teamId());
        row.put("staff_id", a.staffId());
        row.put("shift_template_id", a.shiftTemplateId());
        row.put("start_date", a.startDate() != null ? a.startDate().toString() : null);
        row.put("end_date", a.endDate() != null ? a.endDate().toString() : null);
        row.put("status", a.status().name());
        row.put("created_by", a.createdBy());
        row.put("updated_by", a.updatedBy());
        row.put("created_at", toIso(a.createdAt()));
        row.put("updated_at", toIso(a.updatedAt()));
        row.put("version", a.version());
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
