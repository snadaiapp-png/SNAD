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

public record CreateLeadRequest(
@NotBlank @Size(max = 240) String displayName,
        @Size(max = 240) String companyName,
        @Email @Size(max = 255) String email,
        @Size(max = 64) String phone,
        @Size(max = 120) String source,
        UUID ownerUserId,
        UUID queueId,
        @DecimalMin("0.0") @DecimalMax("100.0") BigDecimal score
) { }
