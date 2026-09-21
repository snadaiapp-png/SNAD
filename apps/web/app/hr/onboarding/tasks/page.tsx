"use client";

/**
 * Onboarding Tasks (my tasks) — G1-T11 Screen 15.
 * ============================================================================
 * Assignee worklist, complete action. Keyboard-first completion; audit-visible
 * history. Spec §14: "assignee worklist, complete action".
 *
 * Permission scoping:
 *   - HRM.ONBOARDING.TASK.COMPLETE required to see + complete.
 *   - HRM.ONBOARDING.TASK.WAIVE required to see waive action.
 *
 * Tenant scoping: listPlans() + completeTask()/waiveTask() are session-tenant-scoped.
 * The listPlans filter is by assigneeId — the frontend passes the current user's
 * ID extracted from the auth context (the backend filter ensures tenant-scoped
 * plans only).
 *
 * Arabic/RTL: logical CSS only; overdue emphasis non-color-coded (font-weight
 * + border color, never color alone).
 */

import { useCallback, useEffect, useMemo, useState } from "react";
import { AuthLoadingState } from "@/components/auth/auth-loading-state";
import { useAuth } from "@/lib/auth/auth-provider";
import { HRM_CAPABILITIES } from "@/lib/auth/capabilities";
import { useI18n } from "@/lib/i18n/I18nProvider";
import { translations } from "@/lib/i18n";
import {
  hrmOnboardingApi,
  type OnboardingPlanSummaryResponse,
  type OnboardingTaskResponse,
} from "@/lib/api/hr-v2-recruitment-api";
import { newIdempotencyKey } from "@/lib/api/hr-v2-api";
import { HrWorkspace } from "../../components/hr-workspace";
import { HrErrorState, HrLoading, HrEmptyState, hrmErrorMessage } from "../../components/hr-feedback";
import { HrStateBadge, toneForState } from "../../components/hr-state-badge";
import { formatArabicDate, onboardingTaskStateLabel } from "../../hr-labels";
import styles from "../../hr.module.css";

interface FlatTask {
  planId: string;
  plan: OnboardingPlanSummaryResponse;
  task: OnboardingTaskResponse;
}

function isOverdue(dueAt: string | null, state: string): boolean {
  if (state !== "PENDING" || !dueAt) return false;
  const today = new Date().toISOString().slice(0, 10);
  return dueAt < today;
}

export default function OnboardingMyTasksPage() {
  const { state, me } = useAuth();
  const { t, locale } = useI18n();
  const capabilities = me?.capabilities ?? [];

  const canComplete = capabilities.includes(HRM_CAPABILITIES.ONBOARDING_TASK_COMPLETE);
  const canWaive = capabilities.includes(HRM_CAPABILITIES.ONBOARDING_TASK_WAIVE);

  const myId = me?.id;

  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<unknown>(null);
  const [plans, setPlans] = useState<OnboardingPlanSummaryResponse[]>([]);
  const [planDetails, setPlanDetails] = useState<Map<string, OnboardingTaskResponse[]>>(new Map());
  const [busyTaskKey, setBusyTaskKey] = useState<string | null>(null);
  const [dialogError, setDialogError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const dict = useMemo(() => translations[locale], [locale]);

  const load = useCallback(async () => {
    if (!myId) return;
    setLoading(true);
    setError(null);
    try {
      const data = await hrmOnboardingApi.listPlans({ assigneeId: myId });
      setPlans(data);
      // Fetch task details for each plan (parallel)
      const details = new Map<string, OnboardingTaskResponse[]>();
      const tasks = await Promise.all(
        data.map((p) => hrmOnboardingApi.getPlan(p.planId).then((d) => [p.planId, d.tasks] as const)),
      );
      for (const [planId, planTasks] of tasks) details.set(planId, planTasks);
      setPlanDetails(details);
    } catch (err) {
      setError(err);
    } finally {
      setLoading(false);
    }
  }, [myId]);

  useEffect(() => {
    if (state !== "AUTHENTICATED") return;
    const timer = window.setTimeout(() => void load(), 0);
    return () => window.clearTimeout(timer);
  }, [state, load]);

  async function completeTask(planId: string, task: OnboardingTaskResponse) {
    setBusyTaskKey(`${planId}:${task.taskId}`);
    setDialogError(null);
    try {
      const key = newIdempotencyKey();
      await hrmOnboardingApi.completeTask(planId, task.taskId, key);
      setNotice(t("hrm.onboarding.myTasks.notice.completed"));
      await load();
    } catch (err) {
      setDialogError(hrmErrorMessage(err).message);
    } finally {
      setBusyTaskKey(null);
    }
  }

  async function waiveTask(planId: string, task: OnboardingTaskResponse, reason: string) {
    if (!reason.trim()) {
      setDialogError(t("hrm.onboarding.planDetail.reasonDialog.reasonLabel"));
      return;
    }
    setBusyTaskKey(`${planId}:${task.taskId}`);
    setDialogError(null);
    try {
      const key = newIdempotencyKey();
      await hrmOnboardingApi.waiveTask(planId, task.taskId, { reason }, key);
      setNotice(t("hrm.onboarding.myTasks.notice.waived"));
      await load();
    } catch (err) {
      setDialogError(hrmErrorMessage(err).message);
    } finally {
      setBusyTaskKey(null);
    }
  }

  if (["INITIALIZING", "CHECKING_SESSION", "REFRESHING"].includes(state)) {
    return <AuthLoadingState phase="session" />;
  }

  if (!canComplete && !canWaive) {
    return (
      <HrWorkspace capabilities={capabilities} activeHref="/hr/onboarding/tasks">
        <p role="alert" className={styles.kpiHint}>{t("hrm.recruitment.dashboard.permissionHint")}</p>
      </HrWorkspace>
    );
  }

  // Flatten all tasks across plans into a single worklist
  const flatTasks: FlatTask[] = [];
  for (const plan of plans) {
    const planTasks = planDetails.get(plan.planId) ?? [];
    for (const task of planTasks) {
      if (task.state === "PENDING") {
        flatTasks.push({ planId: plan.planId, plan, task });
      }
    }
  }

  return (
    <HrWorkspace capabilities={capabilities} activeHref="/hr/onboarding/tasks">
      <header>
        <h1>{t("hrm.onboarding.myTasks.title")}</h1>
        <p className={styles.kpiHint}>{t("hrm.onboarding.myTasks.subtitle")}</p>
      </header>

      {notice ? <p role="status" className={styles.kpiHint}>{notice}</p> : null}
      {dialogError ? (
        <p role="alert" className={styles.kpiHint} data-kind="error">{dialogError}</p>
      ) : null}

      {loading ? (
        <HrLoading />
      ) : error ? (
        <HrErrorState error={error} onRetry={load} />
      ) : flatTasks.length === 0 ? (
        <HrEmptyState
          title={t("hrm.onboarding.myTasks.empty.title")}
          description={t("hrm.onboarding.myTasks.empty.description")}
        />
      ) : (
        <ul className={styles.taskList}>
          {flatTasks.map(({ planId, plan, task }) => {
            const overdue = isOverdue(task.dueAt, task.state);
            const taskKey = `${planId}:${task.taskId}`;
            return (
              <li
                key={taskKey}
                className={`${styles.taskItem} ${overdue ? styles.taskItemOverdue : ""}`}
                data-task-state={task.state}
                data-overdue={overdue ? "true" : "false"}
              >
                <span>
                  <HrStateBadge
                    label={onboardingTaskStateLabel(dict, task.state)}
                    code={task.state}
                    tone={toneForState(task.state)}
                  />
                </span>
                <div>
                  <p className={styles.taskTitle}>{task.title}</p>
                  <p className={styles.taskMeta}>
                    {t("hrm.onboarding.myTasks.col.plan")}: {plan.candidateDisplayName}
                  </p>
                  {task.dueAt ? (
                    <p className={styles.taskMeta}>
                      {t("hrm.onboarding.planDetail.task.dueAt", { date: formatArabicDate(task.dueAt) })}
                      {overdue ? <strong> — {t("hrm.onboarding.planDetail.task.overdue")}</strong> : null}
                    </p>
                  ) : null}
                </div>
                <span className={styles.actionRow}>
                  {canComplete ? (
                    <button
                      type="button"
                      className={styles.linkButton}
                      onClick={() => void completeTask(planId, task)}
                      disabled={busyTaskKey === taskKey}
                    >
                      {t("hrm.onboarding.myTasks.action.complete")}
                    </button>
                  ) : null}
                  {canWaive ? (
                    <button
                      type="button"
                      className={styles.linkButton}
                      onClick={() => void waiveTask(planId, task, window.prompt(t("hrm.onboarding.planDetail.reasonDialog.reasonLabel")) ?? "")}
                      disabled={busyTaskKey === taskKey}
                    >
                      {t("hrm.onboarding.myTasks.action.waive")}
                    </button>
                  ) : null}
                </span>
              </li>
            );
          })}
        </ul>
      )}
    </HrWorkspace>
  );
}
