"use client";

/**
 * Onboarding Dashboard — G1-T11 Screen 13.
 * ============================================================================
 * Plans by state + overdue tasks emphasis. KPI tiles for plans, in-progress,
 * completed, overdue, tasks-due. Permission-scoped: tiles hidden if user lacks
 * HRM.ONBOARDING.PLAN.MANAGE.
 *
 * Arabic/RTL: logical CSS only; onboardingPlanStateLabel(dict, code) resolves
 * backend state codes to localized labels with raw-code fallback.
 *
 * Overdue emphasis uses font-weight + icon (CSS class .taskItemOverdue), never
 * color alone (WCAG 1.4.1).
 */

import { useCallback, useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { AuthLoadingState } from "@/components/auth/auth-loading-state";
import { useAuth } from "@/lib/auth/auth-provider";
import { HRM_CAPABILITIES } from "@/lib/auth/capabilities";
import { useI18n } from "@/lib/i18n/I18nProvider";
import { translations } from "@/lib/i18n";
import {
  hrmOnboardingApi,
  type OnboardingPlanSummaryResponse,
} from "@/lib/api/hr-v2-recruitment-api";
import { HrWorkspace } from "../components/hr-workspace";
import { HrErrorState, HrLoading } from "../components/hr-feedback";
import { HrDataTable, type HrColumn } from "../components/hr-data-table";
import { HrStateBadge, toneForState } from "../components/hr-state-badge";
import { formatArabicDate, onboardingPlanStateLabel } from "../hr-labels";
import styles from "../hr.module.css";

export default function OnboardingDashboardPage() {
  const { state, me } = useAuth();
  const { t, locale } = useI18n();
  const capabilities = me?.capabilities ?? [];

  const canView = capabilities.includes(HRM_CAPABILITIES.ONBOARDING_PLAN_MANAGE);
  const canUseTasks = capabilities.includes(HRM_CAPABILITIES.ONBOARDING_TASK_COMPLETE)
    || capabilities.includes(HRM_CAPABILITIES.ONBOARDING_TASK_WAIVE);

  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<unknown>(null);
  const [plans, setPlans] = useState<OnboardingPlanSummaryResponse[]>([]);

  const dict = useMemo(() => translations[locale], [locale]);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await hrmOnboardingApi.listPlans();
      setPlans(data);
    } catch (err) {
      setError(err);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    if (state !== "AUTHENTICATED") return;
    const timer = window.setTimeout(() => void load(), 0);
    return () => window.clearTimeout(timer);
  }, [state, load]);

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

  const inProgress = plans.filter((p) => p.state === "IN_PROGRESS").length;
  const completed = plans.filter((p) => p.state === "COMPLETED").length;
  const overdue = plans.filter((p) => p.tasksOverdue > 0).length;
  const tasksDue = plans.reduce((sum, p) => sum + (p.tasksTotal - p.tasksCompleted), 0);

  const columns: HrColumn<OnboardingPlanSummaryResponse>[] = [
    {
      key: "candidateDisplayName",
      header: t("hrm.onboarding.dashboard.col.candidate"),
      render: (row) => (
        <Link href={`/hr/onboarding/plans/${row.planId}`} className={styles.linkButton}>
          {row.candidateDisplayName}
        </Link>
      ),
    },
    {
      key: "state",
      header: t("hrm.onboarding.dashboard.col.state"),
      render: (row) => (
        <HrStateBadge
          label={onboardingPlanStateLabel(dict, row.state)}
          code={row.state}
          tone={toneForState(row.state)}
        />
      ),
    },
    {
      key: "progress",
      header: t("hrm.onboarding.dashboard.col.progress"),
      align: "end",
      render: (row) => `${row.tasksCompleted}/${row.tasksTotal}`,
    },
    {
      key: "tasksOverdue",
      header: t("hrm.onboarding.dashboard.col.overdue"),
      align: "end",
      render: (row) => row.tasksOverdue > 0 ? (
        <strong data-overdue="true">{row.tasksOverdue}</strong>
      ) : "0",
    },
    {
      key: "dueAt",
      header: t("hrm.onboarding.dashboard.col.dueAt"),
      render: (row) => row.dueAt ? formatArabicDate(row.dueAt) : "—",
    },
    {
      key: "actions",
      header: t("hrm.onboarding.dashboard.col.actions"),
      render: (row) => (
        <Link href={`/hr/onboarding/plans/${row.planId}`} className={styles.linkButton}>
          {t("hrm.onboarding.dashboard.action.view")}
        </Link>
      ),
    },
  ];

  return (
    <HrWorkspace capabilities={capabilities} activeHref="/hr/onboarding">
      <header>
        <h1>{t("hrm.onboarding.dashboard.title")}</h1>
        <p className={styles.kpiHint}>{t("hrm.onboarding.dashboard.subtitle")}</p>
        {canUseTasks ? (
          <p>
            <Link href="/hr/onboarding/tasks" className={styles.linkButton}>
              {t("hrm.onboarding.myTasks.title")}
            </Link>
          </p>
        ) : null}
      </header>

      {loading ? (
        <HrLoading />
      ) : error ? (
        <HrErrorState error={error} onRetry={load} />
      ) : (
        <>
          <section aria-label={t("hrm.onboarding.dashboard.title")} className={styles.kpiGrid}>
            <article className={styles.kpiTile} aria-label={t("hrm.onboarding.dashboard.kpi.plans")}>
              <span className={styles.kpiValue} data-testid="kpi-plans">{plans.length}</span>
              <span className={styles.kpiLabel}>{t("hrm.onboarding.dashboard.kpi.plans")}</span>
            </article>
            <article className={styles.kpiTile} aria-label={t("hrm.onboarding.dashboard.kpi.inProgress")}>
              <span className={styles.kpiValue} data-testid="kpi-in-progress">{inProgress}</span>
              <span className={styles.kpiLabel}>{t("hrm.onboarding.dashboard.kpi.inProgress")}</span>
            </article>
            <article className={styles.kpiTile} aria-label={t("hrm.onboarding.dashboard.kpi.completed")}>
              <span className={styles.kpiValue} data-testid="kpi-completed">{completed}</span>
              <span className={styles.kpiLabel}>{t("hrm.onboarding.dashboard.kpi.completed")}</span>
            </article>
            <article className={styles.kpiTile} aria-label={t("hrm.onboarding.dashboard.kpi.overdue")}>
              <span className={styles.kpiValue} data-testid="kpi-overdue">{overdue}</span>
              <span className={styles.kpiLabel}>{t("hrm.onboarding.dashboard.kpi.overdue")}</span>
            </article>
            <article className={styles.kpiTile} aria-label={t("hrm.onboarding.dashboard.kpi.tasksDue")}>
              <span className={styles.kpiValue} data-testid="kpi-tasks-due">{tasksDue}</span>
              <span className={styles.kpiLabel}>{t("hrm.onboarding.dashboard.kpi.tasksDue")}</span>
            </article>
          </section>

          <section aria-label={t("hrm.onboarding.dashboard.list.title")}>
            <h2>{t("hrm.onboarding.dashboard.list.title")}</h2>
            <HrDataTable<OnboardingPlanSummaryResponse>
              caption={t("hrm.onboarding.dashboard.list.title")}
              columns={columns}
              rows={plans}
              rowKey={(row) => row.planId}
              emptyTitle={t("hrm.onboarding.dashboard.empty.title")}
              emptyDescription={t("hrm.onboarding.dashboard.empty.description")}
            />
          </section>
        </>
      )}
    </HrWorkspace>
  );
}
