package com.sanad.platform.crm.ownership.domain;

import java.time.Instant;
import java.util.UUID;

/** CRM territory for geographic/segment-based assignment. */
public record Territory(
        UUID id,
        UUID tenantId,
        String code,
        String displayName,
        UUID parentId,
        String description,
        TerritoryStatus status,
        TerritoryRuleType ruleType,
        String ruleDefinition,
        int priority,
        Instant createdAt,
        Instant updatedAt,
        UUID createdBy,
        UUID updatedBy
) {
    public Territory {
        if (tenantId == null) throw new IllegalArgumentException("tenantId required");
        if (code == null || code.isBlank()) throw new IllegalArgumentException("code required");
        if (displayName == null || displayName.isBlank()) throw new IllegalArgumentException("displayName required");
        if (status == null) status = TerritoryStatus.ACTIVE;
        if (ruleType == null) throw new IllegalArgumentException("ruleType required");
        if (parentId != null && parentId.equals(id)) {
            throw new IllegalArgumentException("Territory cannot be its own parent");
        }
    }

    public boolean isRoot() { return parentId == null; }
    public boolean isActive() { return status == TerritoryStatus.ACTIVE; }
}
