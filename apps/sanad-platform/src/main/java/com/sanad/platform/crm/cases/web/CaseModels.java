package com.sanad.platform.crm.cases.web;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Request DTOs for the Case bounded context.
 */
final class CaseModels {

    private CaseModels() {}

    record CreateCaseRequest(
            @NotBlank @Size(max = 240) String subject,
            @Size(max = 4000) String description,
            @Pattern(regexp = "BUG|FEATURE|QUESTION|SUPPORT", flags = Pattern.Flag.CASE_INSENSITIVE) String caseType,
            @Min(0) @Max(100) Integer priority,
            UUID customerId,
            UUID assigneeUserId,
            UUID relatedId,
            OffsetDateTime dueAt) {}

    record UpdateCaseRequest(
            @Size(max = 240) String subject,
            @Size(max = 4000) String description,
            @Pattern(regexp = "BUG|FEATURE|QUESTION|SUPPORT", flags = Pattern.Flag.CASE_INSENSITIVE) String caseType,
            @Min(0) @Max(100) Integer priority,
            UUID customerId,
            OffsetDateTime dueAt) {}

    record ResolveRequest(@Size(max = 4000) String resolution) {}

    record AssignRequest(UUID assigneeUserId) {}
}
