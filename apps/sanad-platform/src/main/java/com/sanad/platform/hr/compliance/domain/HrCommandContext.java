package com.sanad.platform.hr.compliance.domain;

import java.util.UUID;

/**
 * Command context for HRM compliance evaluation. Contains identifiers only —
 * no raw PII and no secrets.
 */
public record HrCommandContext(
        UUID tenantId,
        UUID employmentId,
        UUID actorUserId,
        UUID correlationId) {
}
