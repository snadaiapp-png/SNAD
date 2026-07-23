package com.sanad.platform.crm.ownership.domain;

import java.util.List;
import java.util.UUID;

/** Deterministic, explainable result of evaluating one assignment-rule version. */
public record AssignmentDecision(
        boolean matched,
        UUID ruleId,
        int ruleVersion,
        DistributionMethod distributionMethod,
        OwnerType ownerType,
        UUID ownerId,
        boolean fallbackUsed,
        List<String> trace
) {
    public AssignmentDecision {
        trace = trace == null ? List.of() : List.copyOf(trace);
    }

    public static AssignmentDecision noMatch(List<String> trace) {
        return new AssignmentDecision(false, null, 0, null, null, null, false, trace);
    }
}
