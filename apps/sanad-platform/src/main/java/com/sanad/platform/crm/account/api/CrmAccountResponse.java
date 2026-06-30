package com.sanad.platform.crm.account.api;

import com.sanad.platform.crm.account.domain.CrmAccountStatus;
import com.sanad.platform.crm.account.domain.CrmAccountType;

import java.time.Instant;
import java.util.UUID;

public record CrmAccountResponse(
        UUID id,
        long version,
        String displayName,
        CrmAccountType accountType,
        CrmAccountStatus lifecycleStatus,
        UUID ownerUserId,
        String primaryCurrencyCode,
        String preferredLocale,
        String timeZone,
        String source,
        Instant createdAt,
        Instant updatedAt
) { }
