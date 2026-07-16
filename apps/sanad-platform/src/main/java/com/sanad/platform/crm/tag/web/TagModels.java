package com.sanad.platform.crm.tag.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Request DTOs for the Tag bounded context.
 * <p>
 * Branch: feature/crm-tags
 */
final class TagModels {

    private TagModels() {}

    record CreateTagRequest(
            @NotBlank @Size(max = 80) String name,
            @Size(max = 20) String color) {}

    record UpdateTagRequest(
            @Size(max = 80) String name,
            @Size(max = 20) String color) {}

    record AssignTagRequest(
            @NotNull @Pattern(regexp = "ACCOUNT|CONTACT|LEAD|OPPORTUNITY|ACTIVITY|TASK|NOTE", flags = Pattern.Flag.CASE_INSENSITIVE) String subjectType,
            @NotNull UUID subjectId) {}

    record UnassignTagRequest(
            @NotNull @Pattern(regexp = "ACCOUNT|CONTACT|LEAD|OPPORTUNITY|ACTIVITY|TASK|NOTE", flags = Pattern.Flag.CASE_INSENSITIVE) String subjectType,
            @NotNull UUID subjectId) {}
}
