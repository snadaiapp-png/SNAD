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

public record CreateImportJobRequest(
@NotBlank @Pattern(regexp = "ACCOUNT|CONTACT|LEAD|OPPORTUNITY|ACTIVITY", flags = Pattern.Flag.CASE_INSENSITIVE) String entityType,
        @Min(0) Long totalRows
) { }
