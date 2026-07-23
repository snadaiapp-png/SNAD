package com.sanad.platform.crm.ownership.domain;

import java.time.Instant;
import java.util.UUID;

/** Assignment rule with versioned definitions. */
public record AssignmentRule(
        UUID id,
        UUID tenantId,
        String code,
        int currentVersion,
        RuleStatus status,
        Instant createdAt,
        Instant updatedAt,
        UUID createdBy,
        UUID updatedBy
) {
    public AssignmentRule {
        if (tenantId == null) throw new IllegalArgumentException("tenantId required");
        if (code == null || code.isBlank()) throw new IllegalArgumentException("code required");
        if (status == null) status = RuleStatus.ACTIVE;
        if (currentVersion < 1) currentVersion = 1;
    }

    public boolean isActive() { return status == RuleStatus.ACTIVE; }
}
