package com.sanad.platform.platformiam.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Persistence model for the explicit Platform IAM membership boundary.
 */
public record PlatformMembership(
        UUID id,
        UUID controlTenantId,
        UUID userId,
        PlatformMembershipStatus status,
        Instant invitedAt,
        Instant activatedAt,
        Instant suspendedAt,
        Instant lockedAt,
        Instant disabledAt,
        UUID createdBy,
        UUID updatedBy,
        String statusReason,
        Instant createdAt,
        Instant updatedAt) {
}
