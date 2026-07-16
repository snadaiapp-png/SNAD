package com.sanad.platform.crm.web;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record CreateActivityRequest(
@NotBlank @Pattern(regexp = "TASK|CALL|MEETING|EMAIL|NOTE|MESSAGE|OTHER", flags = Pattern.Flag.CASE_INSENSITIVE) String activityType,
        @NotBlank @Size(max = 240) String subject,
        @Size(max = 4000) String body,
        @Pattern(regexp = "ACCOUNT|CONTACT|LEAD|OPPORTUNITY", flags = Pattern.Flag.CASE_INSENSITIVE) String relatedType,
        UUID relatedId,
        UUID ownerUserId,
        @Min(0) @Max(100) Integer priority,
        OffsetDateTime startAt,
        OffsetDateTime dueAt
) { }
