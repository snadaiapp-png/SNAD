package com.sanad.platform.workflow.domain;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Frozen approval policy for one approval step/request (design decisions
 * E3/M3/Q2). V1 aggregation is {@code ANY_ONE} or {@code ALL} only — QUORUM
 * is explicitly deferred. Self-approval is denied by default; an explicit
 * ALLOW override additionally requires the
 * {@code WORKFLOW.SELF_APPROVAL_OVERRIDE} capability server-side.
 */
public record WorkflowApprovalPolicy(
        Aggregation aggregation,
        SelfApproval selfApproval
) {
    public enum Aggregation { ANY_ONE, ALL }
    public enum SelfApproval { DENY, ALLOW }

    public static WorkflowApprovalPolicy defaultPolicy() {
        return new WorkflowApprovalPolicy(Aggregation.ANY_ONE, SelfApproval.DENY);
    }

    public static WorkflowApprovalPolicy of(String aggregation, String selfApproval) {
        return new WorkflowApprovalPolicy(Aggregation.valueOf(aggregation),
                SelfApproval.valueOf(selfApproval));
    }

    public String snapshotJson() {
        try {
            return new ObjectMapper().writeValueAsString(java.util.Map.of(
                    "aggregation", aggregation.name(),
                    "selfApproval", selfApproval.name()));
        } catch (Exception e) {
            throw new IllegalStateException("Policy snapshot serialization failed", e);
        }
    }
}
