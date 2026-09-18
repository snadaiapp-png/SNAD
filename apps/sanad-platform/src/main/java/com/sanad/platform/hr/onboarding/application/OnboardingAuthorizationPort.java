package com.sanad.platform.hr.onboarding.application;

import com.sanad.platform.hr.compliance.domain.HrCommandContext;

import java.util.UUID;

/**
 * HRM-G1 T9 authorization boundary for onboarding commands.
 *
 * <p>All decisions are fail-closed. Task completion is allowed to the task
 * assignee; every other completion requires HRM.ONBOARDING.TASK.COMPLETE.
 * Waive and plan-management privileges are always explicit and distinct.</p>
 */
public interface OnboardingAuthorizationPort {

    void requirePlanManage(HrCommandContext ctx, UUID resourceId);

    void requireTaskComplete(HrCommandContext ctx, UUID taskId, UUID assigneeUserId);

    void requireTaskWaive(HrCommandContext ctx, UUID taskId);
}
