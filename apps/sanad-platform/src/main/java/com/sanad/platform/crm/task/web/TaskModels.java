package com.sanad.platform.crm.task.web;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Request DTOs for the Task bounded context.
 * <p>
 * Kept in this package (rather than com.sanad.platform.crm.web) so the
 * Task module is self-contained and does not depend on package-private
 * types of the legacy CRM web package.
 * <p>
 * Branch: feature/crm-tasks
 */
final class TaskModels {

    private TaskModels() {}

    record CreateTaskRequest(
            @NotBlank @Size(max = 240) String title,
            @Size(max = 4000) String description,
            @Pattern(regexp = "ACCOUNT|CONTACT|LEAD|OPPORTUNITY|ACTIVITY", flags = Pattern.Flag.CASE_INSENSITIVE) String relatedType,
            UUID relatedId,
            UUID assigneeUserId,
            UUID ownerUserId,
            @Min(0) @Max(100) Integer priority,
            OffsetDateTime startAt,
            OffsetDateTime dueAt) {}

    record UpdateTaskRequest(
            @Size(max = 240) String title,
            @Size(max = 4000) String description,
            UUID assigneeUserId,
            @Min(0) @Max(100) Integer priority,
            OffsetDateTime startAt,
            OffsetDateTime dueAt) {}

    record CompleteTaskRequest(@Size(max = 4000) String result) {}

    record CancelTaskRequest(@Size(max = 4000) String reason) {}
}
