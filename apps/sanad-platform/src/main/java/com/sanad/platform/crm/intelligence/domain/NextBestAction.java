package com.sanad.platform.crm.intelligence.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * AI-generated next best action recommendation for a customer.
 */
public record NextBestAction(
        UUID actionId,
        UUID tenantId,
        UUID accountId,
        String actionCode,
        String description,
        double confidence,
        String reasoning,
        String status,
        Instant generatedAt,
        Instant expiresAt,
        boolean humanConfirmationRequired,
        Instant resolvedAt,
        UUID resolvedBy,
        long version
) {
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_ACCEPTED = "ACCEPTED";
    public static final String STATUS_REJECTED = "REJECTED";
    public static final String STATUS_EXPIRED = "EXPIRED";

    public NextBestAction {
        if (actionId == null) throw new IllegalArgumentException("actionId is required");
        if (tenantId == null) throw new IllegalArgumentException("tenantId is required");
        if (accountId == null) throw new IllegalArgumentException("accountId is required");
        if (actionCode == null || actionCode.isBlank())
            throw new IllegalArgumentException("actionCode is required");
        if (confidence < 0.0 || confidence > 1.0)
            throw new IllegalArgumentException("confidence must be 0.0–1.0");
        if (status == null) status = STATUS_PENDING;
        if (generatedAt == null)
            throw new IllegalArgumentException("generatedAt is required");
        if (expiresAt == null)
            throw new IllegalArgumentException("expiresAt is required");
    }
}
