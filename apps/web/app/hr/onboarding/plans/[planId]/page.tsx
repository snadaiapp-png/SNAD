"use client";

/**
 * Onboarding Plan Detail — G1-T11 Screen 14.
 * ============================================================================
 * Per-plan view: candidate, state, derived progress, task list with
 * complete/waive-with-reason actions, and plan cancel action.
 *
 * Permission scoping:
 *   - HRM.ONBOARDING.PLAN.VIEW required to view.
 *   - HRM.ONBOARDING.TASK.COMPLETE to see "complete" action.
 *   - HRM.ONBOARDING.TASK.WAIVE to see "waive" action.
 *   - HRM.ONBOARDING.PLAN.MANAGE to see "cancel plan" action.
 *
 * Arabic/RTL: logical CSS only; onboardingTaskStateLabel(dict, code) for
 * backend task state codes.
 *
 * Mandatory reason: waive + cancel require a non-empty reason (spec §6.4).
 * The dialog disables confirm until reason.trim().length > 0.
 *
 * Overdue emphasis: tasks whose dueAt < today and state=PENDING get
 * .taskItemOverdue class (font-weight 700 + border-color) — never color alone.
 *
 * Idempotency: complete/waive/cancel send Idempotency-Key header; the backend
 * HrmIdempotentCommandExecutor replays the original result on retry.
 */

import { useCallback, useEffect, useMemo, useState } from "react";
import { useParams } from "next/navigation";
import { AuthLoadingState } from "@/components/auth/auth-loading-state";
import { useAuth } from "@/lib/auth/auth-provider";
import { HRM_CAPABILITIES } from "@/lib/auth/capabilities";
import { useI18n } from "@/lib/i18n/I18nProvider";
import { translations } from "@/lib/i18n";
import {
  hrmOnboardingApi,
  type OnboardingPlanDetailResponse,
  type OnboardingTaskResponse,
} from "@/lib/api/hr-v2-recruitment-api";
import { newIdempotencyKey } from "@/lib/api/hr-v2-api";
import { HrWorkspace } from "../../../components/hr-workspace";
import { HrErrorState, HrLoading, hrmErrorMessage } from "../../../components/hr-feedback";
import { HrStateBadge, toneForState } from "../../../components/hr-state-badge";
import { formatArabicDate, onboardingPlanStateLabel, onboardingTaskStateLabel } from "../../../hr-labels";
import styles from "../../../hr.module.css";

type DialogKind = "waive-task" | "cancel-plan" | null;

function isOverdue(dueAt: string | null, state: string): boolean {
  if (state !== "PENDING" || !dueAt) return false;
  const today = new Date().toISOString().slice(0, 10);
  return dueAt < today;
}

export default function OnboardingPlanDetailPage() {
  const params = useParams<{ planId: string }>();
  const planId = params.planId;

  const { state, me } = useAuth();
  const { t, locale } = useI18n();
  const capabilities = me?.capabilities ?? [];

  const canView = capabilities.includes(HRM_CAPABILITIES.ONBOARDING_PLAN_VIEW)
    || capabilities.includes(HRM_CAPABILITIES.ONBOARDING_PLAN_MANAGE);
  const canComplete = capabilities.includes(HRM_CAPABILITIES.ONBOARDING_TASK_COMPLETE);
  const canWaive = capabilities.includes(HRM_CAPABILITIES.ONBOARDING_TASK_WAIVE);
  const canManagePlan = capabilities.includes(HRM_CAPABILITIES.ONBOARDING_PLAN_MANAGE);

  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<unknown>(null);
  const [plan, setPlan] = useState<OnboardingPlanDetailResponse | null>(null);
  const [dialog, setDialog] = useState<DialogKind>(null);
  const [dialogTask, setDialogTask] = useState<OnboardingTaskResponse | null>(null);
  const [reason, setReason] = useState("");
  const [busy, setBusy] = useState(false);
  const [dialogError, setDialogError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const dict = useMemo(() => translations[locale], [locale]);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await hrmOnboardingApi.getPlan(planId);
      setPlan(data);
    } catch (err) {
      setError(err);
    } finally {
      setLoading(false);
    }
  }, [planId]);

  useEffect(() => {
    if (state !== "AUTHENTICATED") return;
    const timer = window.setTimeout(() => void load(), 0);
    return () => window.clearTimeout(timer);
  }, [state, load]);

  function openWaiveDialog(task: OnboardingTaskResponse) {
    setDialog("waive-task");
    setDialogTask(task);
    setReason("");
    setDialogError(null);
  }

  function openCancelDialog() {
    setDialog("cancel-plan");
    setDialogTask(null);
    setReason("");
    setDialogError(null);
  }

  function closeDialog() {
    setDialog(null);
    setDialogTask(null);
    setReason("");
    setDialogError(null);
  }

  async function submitDialog() {
    if (!plan) return;
    if (!reason.trim()) {
      setDialogError(t("hrm.onboarding.planDetail.reasonDialog.reasonLabel"));
      return;
    }
    setBusy(true);
    setDialogError(null);
    try {
      const key = newIdempotencyKey();
      if (dialog === "waive-task" && dialogTask) {
        await hrmOnboardingApi.waiveTask(plan.planId, dialogTask.taskId, { reason }, key);
        setNotice(t("hrm.onboarding.planDetail.notice.waived"));
      } else if (dialog === "cancel-plan") {
        await hrmOnboardingApi.cancelPlan(plan.planId, key, reason);
        setNotice(t("hrm.onboarding.planDetail.notice.cancelled"));
      }
      closeDialog();
      await load();
    } catch (err) {
      setDialogError(hrmErrorMessage(err).message);
    } finally {
      setBusy(false);
    }
  }

  async function completeTask(task: OnboardingTaskResponse) {
    if (!plan) return;
    setBusy(true);
    setDialogError(null);
    try {
      const key = newIdempotencyKey();
      await hrmOnboardingApi.completeTask(plan.planId, task.taskId, key);
      setNotice(t("hrm.onboarding.planDetail.notice.completed"));
      await load();
    } catch (err) {
      setDialogError(hrmErrorMessage(err).message);
    } finally {
      setBusy(false);
    }
  }

  if (["INITIALIZING", "CHECKING_SESSION", "REFRESHING"].includes(state)) {
    return <AuthLoadingState phase="session" />;
  }

  if (!canView) {
    return (
      <HrWorkspace capabilities={capabilities} activeHref="/hr/onboarding">
        <p role="alert" className={styles.kpiHint}>{t("hrm.recruitment.dashboard.permissionHint")}</p>
      </HrWorkspace>
    );
  }

  const title = dialog === "waive-task" && dialogTask
    ? t("hrm.onboarding.planDetail.reasonDialog.title.waive", { task: dialogTask.title })
    : dialog === "cancel-plan"
      ? t("hrm.onboarding.planDetail.reasonDialog.title.cancel")
      : "";

  return (
    <HrWorkspace capabilities={capabilities} activeHref="/hr/onboarding">
      <header>
        <h1>{t("hrm.onboarding.planDetail.title")}</h1>
      </header>

      {notice ? <p role="status" className={styles.kpiHint}>{notice}</p> : null}
      {dialogError && !dialog ? (
        <p role="alert" className={styles.kpiHint} data-kind="error">{dialogError}</p>
      ) : null}

      {loading ? (
        <HrLoading />
      ) : error ? (
        <HrErrorState error={error} onRetry={load} />
      ) : plan ? (
        <>
          <section aria-label={plan.candidateDisplayName}>
            <h2>{plan.candidateDisplayName}</h2>
            <p>
              <HrStateBadge
                label={onboardingPlanStateLabel(dict, plan.state)}
                code={plan.state}
                tone={toneForState(plan.state)}
              />
            </p>
            <p className={styles.kpiHint}>
              {t("hrm.onboarding.planDetail.progress", {
                done: plan.tasks.filter((task) => task.state === "COMPLETED").length,
                total: plan.tasks.length,
              })}
            </p>
            {canManagePlan && plan.state !== "CANCELLED" ? (
              <button type="button" className={styles.linkButton} onClick={openCancelDialog}>
                {t("hrm.onboarding.planDetail.task.cancelPlan")}
              </button>
            ) : null}
          </section>

          <section aria-label={t("hrm.onboarding.planDetail.tasks")}>
            <h2>{t("hrm.onboarding.planDetail.tasks")}</h2>
            {plan.tasks.length === 0 ? (
              <p role="status">{t("hrm.onboarding.planDetail.tasks.empty")}</p>
            ) : (
              <ul className={styles.taskList}>
                {plan.tasks.map((task) => {
                  const overdue = isOverdue(task.dueAt, task.state);
                  return (
                    <li
                      key={task.taskId}
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
                        {task.description ? <p className={styles.taskMeta}>{task.description}</p> : null}
                        {task.dueAt ? (
                          <p className={styles.taskMeta}>
                            {t("hrm.onboarding.planDetail.task.dueAt", { date: formatArabicDate(task.dueAt) })}
                            {overdue ? <strong> — {t("hrm.onboarding.planDetail.task.overdue")}</strong> : null}
                          </p>
                        ) : null}
                      </div>
                      <span className={styles.actionRow}>
                        {canComplete && task.state === "PENDING" ? (
                          <button
                            type="button"
                            className={styles.linkButton}
                            onClick={() => void completeTask(task)}
                            disabled={busy}
                          >
                            {t("hrm.onboarding.planDetail.task.complete")}
                          </button>
                        ) : null}
                        {canWaive && task.state === "PENDING" ? (
                          <button
                            type="button"
                            className={styles.linkButton}
                            onClick={() => openWaiveDialog(task)}
                            disabled={busy}
                          >
                            {t("hrm.onboarding.planDetail.task.waive")}
                          </button>
                        ) : null}
                      </span>
                    </li>
                  );
                })}
              </ul>
            )}
          </section>

          {dialog ? (
            <div role="dialog" aria-modal="true" aria-label={title} className={styles.feedbackError}>
              <h3>{title}</h3>
              {dialog === "cancel-plan" ? (
                <p>{t("hrm.onboarding.planDetail.cancel.confirmDescription")}</p>
              ) : null}
              <label htmlFor="reason-text" className={styles.kpiLabel}>
                {t("hrm.onboarding.planDetail.reasonDialog.reasonLabel")}
              </label>
              <textarea
                id="reason-text"
                className={styles.reasonTextarea}
                value={reason}
                onChange={(e) => setReason(e.target.value)}
                placeholder={t("hrm.onboarding.planDetail.reasonDialog.reasonPlaceholder")}
                required
                aria-required="true"
              />
              {dialogError ? <p role="alert">{dialogError}</p> : null}
              <div className={styles.actionRow}>
                <button
                  type="button"
                  className={styles.linkButton}
                  onClick={closeDialog}
                  disabled={busy}
                >
                  {t("hrm.onboarding.planDetail.reasonDialog.cancel")}
                </button>
                <button
                  type="button"
                  className={styles.linkButton}
                  onClick={() => void submitDialog()}
                  disabled={busy || !reason.trim()}
                >
                  {dialog === "waive-task"
                    ? t("hrm.onboarding.planDetail.reasonDialog.confirm.waive")
                    : t("hrm.onboarding.planDetail.reasonDialog.confirm.cancel")}
                </button>
              </div>
            </div>
          ) : null}
        </>
      ) : null}
    </HrWorkspace>
  );
}
