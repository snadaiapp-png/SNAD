package com.sanad.platform.crm.ownership.domain;

import java.time.Instant;
import java.util.UUID;

/** Immutable version of an assignment rule's definition. */
public record AssignmentRuleVersion(
        UUID id,
        UUID tenantId,
        UUID ruleId,
        int version,
        String displayName,
        String description,
        AssignmentRecordType recordType,
        int priority,
        String matchConditions,
        DistributionMethod distributionMethod,
        UUID targetOwnerId,
        UUID targetTeamId,
        UUID targetQueueId,
        UUID fallbackOwnerId,
        Instant effectiveFrom,
        Instant effectiveTo,
        RuleStatus status,
        UUID createdBy,
        Instant createdAt
) {
    public AssignmentRuleVersion {
        if (tenantId == null) throw new IllegalArgumentException("tenantId required");
        if (ruleId == null) throw new IllegalArgumentException("ruleId required");
        if (version < 1) throw new IllegalArgumentException("version must be >= 1");
        if (displayName == null || displayName.isBlank()) throw new IllegalArgumentException("displayName required");
        if (recordType == null) throw new IllegalArgumentException("recordType required");
        if (distributionMethod == null) throw new IllegalArgumentException("distributionMethod required");
        if (status == null) status = RuleStatus.ACTIVE;
        if (effectiveFrom == null) effectiveFrom = Instant.now();
        if (effectiveTo != null && effectiveTo.isBefore(effectiveFrom)) {
            throw new IllegalArgumentException("effectiveTo cannot be before effectiveFrom");
        }
    }

    public boolean isActive() { return status == RuleStatus.ACTIVE; }
}
