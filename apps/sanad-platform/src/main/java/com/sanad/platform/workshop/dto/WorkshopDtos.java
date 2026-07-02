package com.sanad.platform.workshop.dto;

import com.sanad.platform.workshop.domain.Workshop;
import com.sanad.platform.workshop.domain.WorkshopActivity;
import com.sanad.platform.workshop.domain.WorkshopAssignment;
import com.sanad.platform.workshop.domain.WorkshopDependency;
import com.sanad.platform.workshop.domain.WorkshopWorkItem;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class WorkshopDtos {
    private WorkshopDtos() {}

    public record CreateWorkshopRequest(
            @NotNull UUID organizationId,
            @NotBlank @Size(max = 80) String code,
            @NotBlank @Size(max = 200) String name,
            @Size(max = 2000) String description,
            Instant plannedStart,
            Instant plannedEnd) {}

    public record TransitionWorkshopRequest(@NotNull Workshop.Status status) {}

    public record CreateWorkItemRequest(
            UUID parentItemId,
            @NotBlank @Size(max = 80) String code,
            @NotBlank @Size(max = 240) String title,
            @Size(max = 4000) String description,
            WorkshopWorkItem.Priority priority,
            Instant dueAt,
            @Min(0) @Max(10000000) Integer estimatedMinutes,
            @Min(0) Integer sequenceNo) {}

    public record TransitionWorkItemRequest(
            @NotNull WorkshopWorkItem.Status status,
            @Size(max = 1000) String blockedReason) {}

    public record CreateAssignmentRequest(
            @NotNull UUID userId,
            @NotNull WorkshopAssignment.Role role) {}

    public record CreateDependencyRequest(
            @NotNull UUID predecessorItemId,
            WorkshopDependency.Type type) {}

    public record CreateActivityRequest(
            @NotNull WorkshopActivity.Type type,
            @Size(max = 4000) String body,
            @Min(1) Integer minutes,
            @Size(max = 1000) String externalUri,
            Instant startedAt,
            Instant endedAt) {}

    public record WorkshopResponse(
            UUID id, UUID organizationId, String code, String name, String description,
            Workshop.Status status, Instant plannedStart, Instant plannedEnd,
            Instant actualStart, Instant actualEnd, UUID createdBy,
            Instant createdAt, Instant updatedAt, long version) {}

    public record WorkItemResponse(
            UUID id, UUID workshopId, UUID parentItemId, String code, String title,
            String description, WorkshopWorkItem.Status status,
            WorkshopWorkItem.Priority priority, UUID primaryAssigneeUserId,
            Instant dueAt, Integer estimatedMinutes, int actualMinutes,
            int sequenceNo, String blockedReason, UUID createdBy,
            Instant createdAt, Instant updatedAt, long version) {}

    public record AssignmentResponse(
            UUID id, UUID workshopId, UUID workItemId, UUID userId,
            WorkshopAssignment.Role role, UUID createdBy, Instant createdAt) {}

    public record DependencyResponse(
            UUID id, UUID workshopId, UUID predecessorItemId, UUID successorItemId,
            WorkshopDependency.Type type, UUID createdBy, Instant createdAt) {}

    public record ActivityResponse(
            UUID id, UUID workshopId, UUID workItemId, WorkshopActivity.Type type,
            String body, Integer minutes, String externalUri, Instant startedAt,
            Instant endedAt, boolean completed, UUID completedBy, Instant completedAt,
            UUID createdBy, Instant createdAt) {}

    public record BoardResponse(
            WorkshopResponse workshop,
            List<WorkItemResponse> workItems,
            List<AssignmentResponse> assignments,
            List<DependencyResponse> dependencies,
            List<ActivityResponse> activities) {}
}
