package com.sanad.platform.hr.employment;

import java.time.Instant;
import java.util.UUID;

/**
 * Audit record for {@link MigrationTenantState} transitions.
 */
public record MigrationTenantStateRecord(
        UUID tenantId,
        MigrationTenantState state,
        Instant updatedAt,
        UUID updatedBy
) {}
