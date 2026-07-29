package com.sanad.platform.crm.ownership.web;

import com.sanad.platform.crm.ownership.application.ShiftManagementUseCases;
import com.sanad.platform.crm.ownership.application.ShiftManagementUseCases.CreateShiftTemplateCommand;
import com.sanad.platform.crm.ownership.application.ShiftManagementUseCases.UpdateShiftTemplateCommand;
import com.sanad.platform.crm.ownership.domain.scheduling.ShiftTemplate;
import com.sanad.platform.crm.ownership.web.TeamModels.CreateShiftTemplateRequest;
import com.sanad.platform.crm.ownership.web.TeamModels.UpdateShiftTemplateRequest;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * V1 REST controller for CRM Shift Templates.
 *
 * <p>Mounted under {@code /api/v1/crm/shift-templates}.
 */
@RestController
@RequestMapping("/api/v1/crm/shift-templates")
public class ShiftTemplateController {

    private final ShiftManagementUseCases shifts;

    public ShiftTemplateController(ShiftManagementUseCases shifts) {
        this.shifts = shifts;
    }

    @RequireCapability("CRM.SHIFT.READ")
    @GetMapping
    public List<Map<String, Object>> listTemplates(
            Authentication authentication,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        UUID tenantId = tenantId(authentication);
        int safeLimit = Math.max(1, Math.min(limit, 200));
        return shifts.listShiftTemplates(tenantId, safeLimit, offset)
                .stream().map(this::toRow).toList();
    }

    @RequireCapability("CRM.SHIFT.READ")
    @GetMapping("/{templateId}")
    public Map<String, Object> getTemplate(Authentication authentication,
                                            @PathVariable UUID templateId) {
        return toRow(shifts.getShiftTemplate(tenantId(authentication), templateId));
    }

    @RequireCapability("CRM.SHIFT.MANAGE")
    @PostMapping
    public ResponseEntity<Map<String, Object>> createTemplate(
            Authentication authentication,
            @Valid @RequestBody CreateShiftTemplateRequest request) {
        UUID tenantId = tenantId(authentication);
        UUID actorId = userId(authentication);

        ShiftTemplate created = shifts.createShiftTemplate(tenantId, actorId,
                new CreateShiftTemplateCommand(
                        request.name(),
                        request.startTime(),
                        request.endTime(),
                        request.daysOfWeek()));

        return ResponseEntity.status(HttpStatus.CREATED).body(toRow(created));
    }

    @RequireCapability("CRM.SHIFT.MANAGE")
    @PatchMapping("/{templateId}")
    public Map<String, Object> updateTemplate(
            Authentication authentication,
            @PathVariable UUID templateId,
            @Valid @RequestBody UpdateShiftTemplateRequest request) {
        UUID tenantId = tenantId(authentication);
        UUID actorId = userId(authentication);

        ShiftTemplate updated = shifts.updateShiftTemplate(tenantId, actorId, templateId,
                new UpdateShiftTemplateCommand(
                        request.name(),
                        request.startTime(),
                        request.endTime(),
                        request.daysOfWeek(),
                        null));

        return toRow(updated);
    }

    @RequireCapability("CRM.SHIFT.MANAGE")
    @PatchMapping("/{templateId}/publish")
    public Map<String, Object> publishTemplate(Authentication authentication,
                                                @PathVariable UUID templateId) {
        return toRow(shifts.publishShiftTemplate(tenantId(authentication), userId(authentication), templateId));
    }

    @RequireCapability("CRM.SHIFT.MANAGE")
    @PatchMapping("/{templateId}/cancel")
    public Map<String, Object> cancelTemplate(Authentication authentication,
                                               @PathVariable UUID templateId) {
        return toRow(shifts.cancelShiftTemplate(tenantId(authentication), userId(authentication), templateId));
    }

    private Map<String, Object> toRow(ShiftTemplate t) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", t.id());
        row.put("tenant_id", t.tenantId());
        row.put("name", t.name());
        row.put("start_time", t.startTime() != null ? t.startTime().toString() : null);
        row.put("end_time", t.endTime() != null ? t.endTime().toString() : null);
        row.put("days_of_week", t.daysOfWeek());
        row.put("status", t.status().name());
        row.put("created_by", t.createdBy());
        row.put("updated_by", t.updatedBy());
        row.put("created_at", toIso(t.createdAt()));
        row.put("updated_at", toIso(t.updatedAt()));
        row.put("version", t.version());
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
