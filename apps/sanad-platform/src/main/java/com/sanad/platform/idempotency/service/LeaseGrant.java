package com.sanad.platform.idempotency.service;

import java.time.Instant;
import java.util.UUID;

/**
 * Stage 05A.2.6 §5 — Immutable lease grant returned directly from SQL
 * RETURNING clause. Never re-read from JPA after UPDATE/INSERT.
 */
public record LeaseGrant(
        UUID recordId,
        UUID tenantId,
        String leaseOwnerRequestId,
        long leaseVersion,
        String status,
        String requestFingerprint,
        Instant leaseExpiresAt
) {}
