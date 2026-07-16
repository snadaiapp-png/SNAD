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

public record CreateCustomFieldRequest(
@NotBlank @Pattern(regexp = "ACCOUNT|CONTACT|LEAD|OPPORTUNITY|ACTIVITY", flags = Pattern.Flag.CASE_INSENSITIVE) String entityType,
        @NotBlank @Pattern(regexp = "[A-Za-z][A-Za-z0-9_]{1,119}") String fieldKey,
        @NotBlank @Size(max = 240) String labelAr,
        @NotBlank @Size(max = 240) String labelEn,
        @NotBlank @Pattern(regexp = "TEXT|NUMBER|BOOLEAN|DATE|DATETIME|EMAIL|URL", flags = Pattern.Flag.CASE_INSENSITIVE) String dataType,
        Boolean sensitive,
        Boolean searchable,
        Boolean required
) { }
