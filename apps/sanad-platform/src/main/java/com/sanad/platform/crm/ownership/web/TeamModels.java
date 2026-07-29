package com.sanad.platform.crm.ownership.web;

import com.sanad.platform.crm.ownership.domain.availability.AvailabilityType;
import com.sanad.platform.crm.ownership.domain.skills.SkillLevel;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/**
 * Request DTOs for the Team Management bounded context (CRM-008).
 *
 * <p>Package-private records with Bean Validation annotations.
 */
final class TeamModels {

    private TeamModels() {}

    // ── Team DTOs ────────────────────────────────────────────────────────

    record CreateTeamRequest(
            @NotBlank @Size(max = 50) String code,
            @NotBlank @Size(max = 200) String displayName,
            @Size(max = 2000) String description,
            UUID managerUserId,
            UUID defaultQueueId,
            UUID defaultTerritoryId) {}

    record UpdateTeamRequest(
            @Size(max = 200) String displayName,
            @Size(max = 2000) String description,
            String status,
            UUID managerUserId,
            UUID defaultQueueId,
            UUID defaultTerritoryId) {}

    // ── Shift Template DTOs ──────────────────────────────────────────────

    record CreateShiftTemplateRequest(
            @NotBlank @Size(max = 200) String name,
            @NotNull LocalTime startTime,
            @NotNull LocalTime endTime,
            @NotNull List<java.time.DayOfWeek> daysOfWeek) {}

    record UpdateShiftTemplateRequest(
            @Size(max = 200) String name,
            LocalTime startTime,
            LocalTime endTime,
            List<java.time.DayOfWeek> daysOfWeek) {}

    // ── Shift Assignment DTOs ────────────────────────────────────────────

    record CreateShiftAssignmentRequest(
            @NotNull UUID teamId,
            @NotNull UUID staffId,
            @NotNull UUID shiftTemplateId,
            @NotNull LocalDate startDate,
            @NotNull LocalDate endDate) {}

    record UpdateShiftAssignmentRequest(
            UUID shiftTemplateId,
            LocalDate startDate,
            LocalDate endDate) {}

    // ── Availability DTOs ────────────────────────────────────────────────

    record SubmitAvailabilityRequest(
            @NotNull UUID staffId,
            @NotNull AvailabilityType type,
            @NotNull LocalDate startDate,
            @NotNull LocalDate endDate,
            LocalTime startTime,
            LocalTime endTime,
            @Size(max = 500) String reason) {}

    // ── Skill DTOs ───────────────────────────────────────────────────────

    record RegisterSkillRequest(
            @NotNull UUID staffId,
            @NotBlank @Size(max = 200) String skillName,
            @NotNull SkillLevel level,
            @Min(1) @Max(100) int proficiency) {}

    record UpdateSkillRequest(
            SkillLevel level,
            @Min(1) @Max(100) int proficiency) {}

    // ── Capacity DTOs ────────────────────────────────────────────────────

    record CreateCapacityPlanRequest(
            @NotNull UUID teamId,
            @NotNull LocalDate periodStart,
            @NotNull LocalDate periodEnd,
            @Min(1) int maxCapacity) {}

    record AdjustCapacityRequest(
            @Min(1) Integer maxCapacity,
            @Min(0) Integer allocatedCapacity) {}

    // ── Workload DTOs ────────────────────────────────────────────────────

    record AssignWorkRequest(
            @NotNull UUID staffId,
            @NotNull UUID serviceId,
            UUID jobId,
            @Min(1) int estimatedHours,
            @NotNull LocalDate startDate,
            LocalDate endDate) {}

    // ── Service Assignment DTOs ──────────────────────────────────────────

    record AssignServiceRequest(
            @NotNull UUID teamId,
            @NotNull UUID serviceId) {}
}
