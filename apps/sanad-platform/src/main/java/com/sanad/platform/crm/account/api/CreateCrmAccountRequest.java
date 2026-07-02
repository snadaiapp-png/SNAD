package com.sanad.platform.crm.account.api;

import com.sanad.platform.crm.account.domain.CrmAccountType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateCrmAccountRequest(
        @NotBlank @Size(max = 240) String displayName,
        @NotNull CrmAccountType accountType,
        UUID ownerUserId,
        @Pattern(regexp = "^[A-Za-z]{3}$") String primaryCurrencyCode,
        @Size(max = 35) String preferredLocale,
        @Size(max = 64) String timeZone,
        @Size(max = 64) String source
) { }
