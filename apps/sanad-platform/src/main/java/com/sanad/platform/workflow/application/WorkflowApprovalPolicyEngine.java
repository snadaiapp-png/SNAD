package com.sanad.platform.workflow.application;

import com.sanad.platform.workflow.domain.WorkflowApprovalRequest;
import com.sanad.platform.workflow.domain.WorkflowApprovalPolicy;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Deterministic approval aggregation engine (design decisions E3/Q2/F3).
 *
 * <p>ANY_ONE: the first valid APPROVE completes the step through onApprove
 * and closes remaining pending requests. One REJECT closes only that
 * actor's request; onReject fires only when approval becomes impossible,
 * i.e. every effective candidate has rejected or is unavailable.</p>
 *
 * <p>ALL: every candidate must approve; the first valid REJECT makes
 * unanimous approval impossible and routes through onReject immediately.
 * Remaining pending requests are cancelled.</p>
 *
 * <p>QUORUM/N_OF_M is deferred by scope and must not be implemented here.</p>
 */
@Service
public class WorkflowApprovalPolicyEngine {

    public record ApprovalResolution(
            String outcome,
            boolean stepComplete,
            List<UUID> requestsToCancel) {

        public static ApprovalResolution open() {
            return new ApprovalResolution(null, false, List.of());
        }
    }

    public ApprovalResolution resolve(WorkflowApprovalPolicy policy, List<WorkflowApprovalRequest> requests) {
        return policy.aggregation() == WorkflowApprovalPolicy.Aggregation.ANY_ONE
                ? resolveAnyOne(requests)
                : resolveAll(requests);
    }

    public ApprovalResolution resolveAnyOne(List<WorkflowApprovalRequest> requests) {
        UUID approvedId = requests.stream()
                .filter(r -> r.status() == WorkflowApprovalRequest.Status.APPROVED)
                .map(WorkflowApprovalRequest::id)
                .findFirst().orElse(null);
        if (approvedId != null) {
            List<UUID> cancel = requests.stream()
                    .filter(r -> r.status() == WorkflowApprovalRequest.Status.PENDING)
                    .map(WorkflowApprovalRequest::id)
                    .toList();
            return new ApprovalResolution("APPROVE", true, cancel);
        }
        long rejected = requests.stream()
                .filter(r -> r.status() == WorkflowApprovalRequest.Status.REJECTED).count();
        long pending = requests.stream()
                .filter(r -> r.status() == WorkflowApprovalRequest.Status.PENDING).count();
        if (pending == 0 && rejected > 0) {
            // Approval became impossible: all effective candidates rejected/unavailable.
            return new ApprovalResolution("REJECT", true, List.of());
        }
        return ApprovalResolution.open();
    }

    public ApprovalResolution resolveAll(List<WorkflowApprovalRequest> requests) {
        boolean anyRejected = requests.stream()
                .anyMatch(r -> r.status() == WorkflowApprovalRequest.Status.REJECTED);
        if (anyRejected) {
            List<UUID> cancel = requests.stream()
                    .filter(r -> r.status() == WorkflowApprovalRequest.Status.PENDING)
                    .map(WorkflowApprovalRequest::id)
                    .toList();
            return new ApprovalResolution("REJECT", true, cancel);
        }
        boolean allApproved = !requests.isEmpty() && requests.stream()
                .allMatch(r -> r.status() == WorkflowApprovalRequest.Status.APPROVED);
        if (allApproved) {
            return new ApprovalResolution("APPROVE", true, List.of());
        }
        return ApprovalResolution.open();
    }
}
