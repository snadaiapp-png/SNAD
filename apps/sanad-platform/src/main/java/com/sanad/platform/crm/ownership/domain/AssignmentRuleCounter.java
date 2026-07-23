package com.sanad.platform.crm.ownership.domain;

import java.time.Instant;
import java.util.UUID;

/** Round-robin counter for an assignment rule (per tenant, per rule). */
public record AssignmentRuleCounter(
        UUID id,
        UUID tenantId,
        UUID ruleId,
        long counter,
        Instant updatedAt
) {
    public AssignmentRuleCounter {
        if (tenantId == null) throw new IllegalArgumentException("tenantId required");
        if (ruleId == null) throw new IllegalArgumentException("ruleId required");
        if (counter < 0) counter = 0;
    }

    public AssignmentRuleCounter increment() {
        return new AssignmentRuleCounter(id, tenantId, ruleId, counter + 1, Instant.now());
    }
}
