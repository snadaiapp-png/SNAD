package com.sanad.platform.subscription.provisioning;

import java.time.Instant;
import java.util.UUID;

/**
 * Typed provisioning-job response row for the executive console
 * ({@code GET /api/v1/executive/provisioning/jobs}).
 *
 * <p>R0C-12 Blocker B-class correction: the endpoint previously serialized
 * the raw JDBC row {@code Map<String, Object>} (physical snake_case column
 * names: {@code tenant_id}, {@code subscription_id}, {@code started_at},
 * {@code error_code}, …). The web console's {@code ProvisioningJob} contract
 * is camelCase, so those fields were silently lost ({@code undefined})
 * in the jobs table and deep links. This record pins the JSON contract;
 * nullable columns map to {@code null} JSON members.
 */
public record ProvisioningJobResponse(
        UUID id,
        UUID tenantId,
        UUID subscriptionId,
        String action,
        String status,
        int attempts,
        Instant startedAt,
        Instant completedAt,
        String errorCode,
        Instant createdAt) {
}
