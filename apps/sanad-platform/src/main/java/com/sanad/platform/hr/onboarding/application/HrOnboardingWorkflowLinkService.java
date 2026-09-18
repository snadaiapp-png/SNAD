package com.sanad.platform.hr.onboarding.application;

import com.sanad.platform.hr.compliance.domain.HrCommandContext;
import com.sanad.platform.hr.onboarding.domain.OnboardingReasonCodes;
import com.sanad.platform.hr.onboarding.infrastructure.JdbcHrOnboardingRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.util.Objects;
import java.util.UUID;

/**
 * Applies Y2 onboarding task outcomes back into the HR-owned state machine.
 *
 * <p>Idempotency is persisted by (tenant_id, task_id, transition_seq).
 * Y2 remains process authority; HRM remains business-state authority.</p>
 */
@Service
public class HrOnboardingWorkflowLinkService {

    private final JdbcHrOnboardingRepository repository;
    private final OnboardingAuthorizationPort authorization;
    @SuppressWarnings("unused")
    private final DataSource dataSource;

    @Autowired
    public HrOnboardingWorkflowLinkService(JdbcHrOnboardingRepository repository,
                                           OnboardingAuthorizationPort authorization,
                                           DataSource dataSource) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.authorization = Objects.requireNonNull(authorization, "authorization");
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    public void applyTransition(HrCommandContext ctx, UUID taskId, long transitionSeq,
                                String outcome, String reasonCode) {
        Objects.requireNonNull(ctx, "ctx");
        Objects.requireNonNull(ctx.tenantId(), "ctx.tenantId");
        Objects.requireNonNull(ctx.actorUserId(), "ctx.actorUserId");
        Objects.requireNonNull(taskId, "taskId");
        if (transitionSeq < 0) {
            throw new IllegalArgumentException("transitionSeq must be non-negative");
        }
        if (outcome == null || outcome.isBlank()) {
            throw new IllegalArgumentException("outcome is required");
        }

        JdbcHrOnboardingRepository.TaskSnapshot task = repository.findTask(ctx, taskId);
        if (task == null) {
            throw new IllegalStateException("HRM_ONBOARDING_TASK_NOT_FOUND: " + taskId);
        }
        if (!task.workflowLinked()) {
            throw new IllegalStateException("HRM_ONBOARDING_WORKFLOW_NOT_LINKED: " + taskId);
        }

        String normalized = outcome.trim().toUpperCase(java.util.Locale.ROOT);
        if ("DONE".equals(normalized)) {
            authorization.requireTaskComplete(ctx, taskId, task.assigneeUserId());
        } else if ("WAIVED".equals(normalized) || "CANCELLED".equals(normalized)) {
            authorization.requireTaskWaive(ctx, taskId);
            if (!OnboardingReasonCodes.isRegistered(reasonCode)) {
                throw new IllegalStateException("HRM_REASON_REJECTED: " + reasonCode);
            }
        } else {
            throw new IllegalStateException("HRM_ONBOARDING_WORKFLOW_OUTCOME_REJECTED: " + outcome);
        }

        repository.applyWorkflowTransition(ctx, taskId, transitionSeq, normalized, reasonCode);
    }
}
