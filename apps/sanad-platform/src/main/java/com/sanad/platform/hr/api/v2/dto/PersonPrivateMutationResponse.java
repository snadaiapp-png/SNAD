package com.sanad.platform.hr.api.v2.dto;

import java.util.UUID;

/**
 * HRM-G0 / WS5 Task 3 slice 2 — metadata-only result of a private-profile
 * mutation. PII values are deliberately absent: mutations return version
 * metadata only, and clients read the audited private view explicitly.
 */
public record PersonPrivateMutationResponse(
        UUID personId,
        long version
) {
}
