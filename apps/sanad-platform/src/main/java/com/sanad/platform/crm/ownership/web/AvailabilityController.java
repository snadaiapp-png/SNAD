package com.sanad.platform.crm.ownership.web;

import com.sanad.platform.crm.ownership.application.AvailabilityManagementUseCases;
import com.sanad.platform.crm.ownership.application.AvailabilityManagementUseCases.SubmitAvailabilityCommand;
import com.sanad.platform.crm.ownership.domain.availability.StaffAvailability;
import com.sanad.platform.crm.ownership.web.TeamModels.SubmitAvailabilityRequest;
import com.sanad.platform.security.authorization.RequireCapability;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
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
 * V1 REST controller for CRM Staff Availability.
 *
 * <p>Mounted under {@code /api/v1/crm/availability}.
 */
@RestController
@RequestMapping("/api/v1/crm/availability")
public class AvailabilityController {

    private final AvailabilityManagementUseCases availability;

    public AvailabilityController(AvailabilityManagementUseCases availability) {
        this.availability = availability;
    }

    @RequireCapability("CRM.AVAILABILITY.READ")
    @GetMapping
    public List<Map<String, Object>> calendarQuery(
            Authentication authentication,
            @RequestParam UUID staffId,
            @RequestParam String from,
            @RequestParam String to) {
        UUID tenantId = tenantId(authentication);
        return availability.calendarQuery(tenantId, staffId,
                LocalDate.parse(from), LocalDate.parse(to))
                .stream().map(this::toRow).toList();
    }

    @RequireCapability("CRM.AVAILABILITY.MANAGE")
    @PostMapping
    public ResponseEntity<Map<String, Object>> submitAvailability(
            Authentication authentication,
            @Valid @RequestBody SubmitAvailabilityRequest request) {
        UUID tenantId = tenantId(authentication);
        UUID actorId = userId(authentication);

        StaffAvailability created = availability.submitAvailability(tenantId, actorId,
                new SubmitAvailabilityCommand(
                        request.staffId(),
                        request.type(),
                        request.startDate(),
                        request.endDate(),
                        request.startTime(),
                        request.endTime(),
                        request.reason()));

        return ResponseEntity.status(HttpStatus.CREATED).body(toRow(created));
    }

    @RequireCapability("CRM.AVAILABILITY.MANAGE")
    @PatchMapping("/{availabilityId}/approve")
    public Map<String, Object> approveAvailability(Authentication authentication,
                                                    @PathVariable UUID availabilityId) {
        return toRow(availability.approveAvailability(tenantId(authentication), userId(authentication), availabilityId));
    }

    @RequireCapability("CRM.AVAILABILITY.MANAGE")
    @PatchMapping("/{availabilityId}/reject")
    public Map<String, Object> rejectAvailability(
            Authentication authentication,
            @PathVariable UUID availabilityId,
            @RequestBody(required = false) Map<String, String> body) {
        String reason = body != null ? body.get("reason") : null;
        return toRow(availability.rejectAvailability(tenantId(authentication), userId(authentication), availabilityId, reason));
    }

    @RequireCapability("CRM.AVAILABILITY.MANAGE")
    @DeleteMapping("/{availabilityId}")
    public ResponseEntity<Void> deleteAvailability(Authentication authentication,
                                                    @PathVariable UUID availabilityId) {
        availability.deleteAvailability(tenantId(authentication), userId(authentication), availabilityId);
        return ResponseEntity.noContent().build();
    }

    private Map<String, Object> toRow(StaffAvailability a) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", a.id());
        row.put("tenant_id", a.tenantId());
        row.put("staff_id", a.staffId());
        row.put("type", a.type().name());
        row.put("start_date", a.startDate() != null ? a.startDate().toString() : null);
        row.put("end_date", a.endDate() != null ? a.endDate().toString() : null);
        row.put("start_time", a.startTime() != null ? a.startTime().toString() : null);
        row.put("end_time", a.endTime() != null ? a.endTime().toString() : null);
        row.put("reason", a.reason());
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
