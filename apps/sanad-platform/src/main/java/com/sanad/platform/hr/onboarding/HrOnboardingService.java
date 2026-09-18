package com.sanad.platform.hr.onboarding;

import com.sanad.platform.hr.compliance.domain.HrCommandContext;
import com.sanad.platform.hr.onboarding.application.OnboardingAuthorizationPort;
import com.sanad.platform.hr.onboarding.domain.OnboardingReasonCodes;
import com.sanad.platform.hr.onboarding.infrastructure.JdbcHrOnboardingRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.util.Objects;
import java.util.UUID;

/**
 * HRM-G1 T9 onboarding application service.
 *
 * <p>Plan completion is deliberately not exposed as a command. It is derived
 * transactionally after task transitions by the repository.</p>
 */
@Service
public class HrOnboardingService {

    private final JdbcHrOnboardingRepository repository;
    private final OnboardingAuthorizationPort authorization;
    @SuppressWarnings("unused")
    private final DataSource dataSource;

    @Autowired
    public HrOnboardingService(JdbcHrOnboardingRepository repository,
                               OnboardingAuthorizationPort authorization,
                               DataSource dataSource) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.authorization = Objects.requireNonNull(authorization, "authorization");
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    public UUID createPlan(HrCommandContext ctx, UUID employmentId,
                           String templateCode, boolean workflowLinked) {
        requireContext(ctx);
        Objects.requireNonNull(employmentId, "employmentId");
        requireText(templateCode, "templateCode");
        authorization.requirePlanManage(ctx, employmentId);
        return repository.createPlan(ctx, employmentId, templateCode, workflowLinked);
    }

    public void completeTask(HrCommandContext ctx, UUID taskId, UUID requestId) {
        requireContext(ctx);
        Objects.requireNonNull(taskId, "taskId");
        Objects.requireNonNull(requestId, "requestId");
        JdbcHrOnboardingRepository.TaskSnapshot task = repository.findTask(ctx, taskId);
        if (task == null) {
            throw new IllegalStateException("HRM_ONBOARDING_TASK_NOT_FOUND: " + taskId);
        }
        authorization.requireTaskComplete(ctx, taskId, task.assigneeUserId());
        repository.completeTask(ctx, taskId, requestId);
    }

    public void waiveTask(HrCommandContext ctx, UUID taskId, String reasonCode, UUID requestId) {
        requireContext(ctx);
        Objects.requireNonNull(taskId, "taskId");
        Objects.requireNonNull(requestId, "requestId");

        // Authorization intentionally precedes reason validation (fail closed,
        // and prevents reason-registry probing by an unauthorized caller).
        authorization.requireTaskWaive(ctx, taskId);
        if (!OnboardingReasonCodes.TASK_WAIVER.equals(reasonCode)) {
            throw new IllegalStateException("HRM_REASON_REJECTED: " + reasonCode);
        }
        JdbcHrOnboardingRepository.TaskSnapshot task = repository.findTask(ctx, taskId);
        if (task == null) {
            throw new IllegalStateException("HRM_ONBOARDING_TASK_NOT_FOUND: " + taskId);
        }
        repository.waiveTask(ctx, taskId, reasonCode, requestId);
    }

    public void cancelPlan(HrCommandContext ctx, UUID planId, String reasonCode, UUID requestId) {
        requireContext(ctx);
        Objects.requireNonNull(planId, "planId");
        Objects.requireNonNull(requestId, "requestId");
        authorization.requirePlanManage(ctx, planId);
        if (!OnboardingReasonCodes.PLAN_CANCELLATION.equals(reasonCode)) {
            throw new IllegalStateException("HRM_REASON_REJECTED: " + reasonCode);
        }
        repository.cancelPlan(ctx, planId, reasonCode, requestId);
    }

    private static void requireContext(HrCommandContext ctx) {
        Objects.requireNonNull(ctx, "ctx");
        Objects.requireNonNull(ctx.tenantId(), "ctx.tenantId");
        Objects.requireNonNull(ctx.actorUserId(), "ctx.actorUserId");
        Objects.requireNonNull(ctx.correlationId(), "ctx.correlationId");
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
    }
}
