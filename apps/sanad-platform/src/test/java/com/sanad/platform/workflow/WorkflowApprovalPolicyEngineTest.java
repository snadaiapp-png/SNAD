package com.sanad.platform.workflow;

import com.sanad.platform.workflow.application.WorkflowApprovalPolicyEngine;
import com.sanad.platform.workflow.domain.WorkflowApprovalPolicy;
import com.sanad.platform.workflow.domain.WorkflowApprovalRequest;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Wave 1 / Task 9 — Y2 approval policy engine (E3/Q2/F3/M3).
 *
 * <p>Pure domain/engine invariants, no database: ANY_ONE first-approval
 * wins and closes remaining requests; an ANY_ONE rejection closes only that
 * actor's request and keeps the step open while another candidate can still
 * approve; ALL routes through onReject at the first rejection. Rejection
 * reason is mandatory and self-approval is denied by default.</p>
 */
class WorkflowApprovalPolicyEngineTest {

    private final WorkflowApprovalPolicyEngine engine = new WorkflowApprovalPolicyEngine();

    private WorkflowApprovalRequest pending(UUID requester, UUID approver) {
        return WorkflowApprovalRequest.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                approver, "MANAGER", Instant.now().plusSeconds(3600), requester);
    }

    private WorkflowApprovalRequest approved(UUID requester, UUID approver) {
        return pending(requester, approver).approve(approver, "ok");
    }

    private WorkflowApprovalRequest rejected(UUID requester, UUID approver, String reason) {
        return pending(requester, approver).reject(approver, reason);
    }

    @Test
    void anyOneRejectKeepsStepOpenWhileAnotherCandidateCanApprove() {
        UUID requester = UUID.randomUUID();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        var resolution = engine.resolveAnyOne(List.of(
                rejected(requester, a, "not my queue"),
                pending(requester, b)));
        assertThat(resolution.stepComplete()).isFalse();
        assertThat(resolution.outcome()).isNull();
    }

    @Test
    void anyOneFirstApprovalCompletesAndCancelsRemainingRequests() {
        UUID requester = UUID.randomUUID();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();
        var approved = approved(requester, a);
        var pendingB = pending(requester, b);
        var resolution = engine.resolveAnyOne(List.of(approved, pendingB, pending(requester, c)));
        assertThat(resolution.outcome()).isEqualTo("APPROVE");
        assertThat(resolution.stepComplete()).isTrue();
        assertThat(resolution.requestsToCancel()).contains(pendingB.id());
    }

    @Test
    void allFirstRejectionRoutesReject() {
        UUID requester = UUID.randomUUID();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();
        var resolution = engine.resolveAll(List.of(
                approved(requester, a),
                rejected(requester, b, "budget exceeded"),
                pending(requester, c)));
        assertThat(resolution.outcome()).isEqualTo("REJECT");
        assertThat(resolution.stepComplete()).isTrue();
    }

    @Test
    void anyOneRejectBecomesFinalOnlyWhenApprovalImpossible() {
        UUID requester = UUID.randomUUID();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        var stillOpen = engine.resolveAnyOne(List.of(
                rejected(requester, a, "no"),
                pending(requester, b)));
        assertThat(stillOpen.stepComplete()).isFalse();

        var exhausted = engine.resolveAnyOne(List.of(
                rejected(requester, a, "no"),
                rejected(requester, b, "no from me either")));
        assertThat(exhausted.outcome()).isEqualTo("REJECT");
        assertThat(exhausted.stepComplete()).isTrue();
    }

    @Test
    void allRequiresEveryApproval() {
        UUID requester = UUID.randomUUID();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        var open = engine.resolveAll(List.of(approved(requester, a), pending(requester, b)));
        assertThat(open.stepComplete()).isFalse();

        var complete = engine.resolveAll(List.of(approved(requester, a), approved(requester, b)));
        assertThat(complete.outcome()).isEqualTo("APPROVE");
        assertThat(complete.stepComplete()).isTrue();
    }

    @Test
    void rejectionReasonIsRequired() {
        var request = pending(UUID.randomUUID(), UUID.randomUUID());
        assertThatThrownBy(() -> request.reject(UUID.randomUUID(), null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> request.reject(UUID.randomUUID(), "  "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void selfApprovalDeniedByDefaultEvenWithExplicitOverrideIntent() {
        UUID requester = UUID.randomUUID();
        var request = pending(requester, requester);
        assertThatThrownBy(() -> request.approve(requester, "self"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void explicitAllowPolicyPermitsSelfApprovalAtDomainLevel() {
        UUID requester = UUID.randomUUID();
        var request = WorkflowApprovalRequest.create(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), requester, "MANAGER", Instant.now().plusSeconds(600),
                requester, null,
                new WorkflowApprovalPolicy(WorkflowApprovalPolicy.Aggregation.ANY_ONE,
                        WorkflowApprovalPolicy.SelfApproval.ALLOW));
        var approved = request.approve(requester, "configured self-approval");
        assertThat(approved.status()).isEqualTo(WorkflowApprovalRequest.Status.APPROVED);
        assertThat(request.policySnapshot()).contains("\"selfApproval\":\"ALLOW\"");
    }

    @Test
    void policyDefaultsDenyAndAnyOne() {
        var policy = WorkflowApprovalPolicy.defaultPolicy();
        assertThat(policy.aggregation()).isEqualTo(WorkflowApprovalPolicy.Aggregation.ANY_ONE);
        assertThat(policy.selfApproval()).isEqualTo(WorkflowApprovalPolicy.SelfApproval.DENY);
    }
}
