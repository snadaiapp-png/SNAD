"use client";

/**
 * HRM G3 Task 5 — Goals tracking SELF product surface.
 *
 * Employees view their authorized performance goals and update progress
 * through the governed typed facade (hrG3Api). Capability gating here is
 * UX-only; the backend @RequireCapability remains authoritative. Update
 * history is not rendered because the Task 4 service contract supplies no
 * history endpoint — only current createdAt/updatedAt facts are shown.
 */

import { useCallback, useEffect, useMemo, useState } from "react";
import { AuthLoadingState } from "@/components/auth/auth-loading-state";
import { hrG3Api } from "@/lib/api/hr-g3-api";
import { useAuth } from "@/lib/auth/auth-provider";
import { HRM_CAPABILITIES } from "@/lib/auth/capabilities";
import { useI18n } from "@/lib/i18n/I18nProvider";
import { HrWorkspace } from "../../components/hr-workspace";
import { HrErrorState, HrLoading, hrmErrorMessage } from "../../components/hr-feedback";
import { HrDataTable, type HrColumn } from "../../components/hr-data-table";
import { HrStateBadge, toneForState } from "../../components/hr-state-badge";
import {
  HrActionBar,
  HrKpiCard,
  HrKpiGrid,
  HrMobileRecordList,
  HrOperationalPanel,
  HrProductHeader,
} from "../../components/hr-product-surface";
import type { G3PerformanceGoal } from "@/lib/api/hr-g3-api";
import { formatLocalizedDate } from "../../hr-labels";
import styles from "../../hr.module.css";

const AUTH_LOADING_STATES = new Set(["INITIALIZING", "CHECKING_SESSION", "REFRESHING"]);

export default function GoalsPage() {
  const { state, me } = useAuth();
  const { t, locale } = useI18n();
  const capabilities = me?.capabilities ?? [];

  const canViewSelf = capabilities.includes(HRM_CAPABILITIES.GOAL_SELF_VIEW);
  const canUpdate = capabilities.includes(HRM_CAPABILITIES.GOAL_SELF_UPDATE);
  const canManageTeam = capabilities.includes(HRM_CAPABILITIES.GOAL_TEAM_MANAGE);
  const canView = canViewSelf || canManageTeam;

  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<unknown>(null);
  const [goals, setGoals] = useState<G3PerformanceGoal[]>([]);
  const [selectedGoalId, setSelectedGoalId] = useState<string>("");
  const [progressDraft, setProgressDraft] = useState<string>("");
  const [progressError, setProgressError] = useState<string | null>(null);
  const [mutationError, setMutationError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState(false);
  const [teamEmploymentId, setTeamEmploymentId] = useState("");
  const [teamGoals, setTeamGoals] = useState<G3PerformanceGoal[] | null>(null);
  const [teamError, setTeamError] = useState<unknown>(null);
  const [teamLoading, setTeamLoading] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      if (canViewSelf) {
        const result = await hrG3Api.listGoals();
        setGoals(result);
        setSelectedGoalId((current) => (current && result.some((goal) => goal.id === current) ? current : result[0]?.id ?? ""));
      } else {
        setGoals([]);
        setSelectedGoalId("");
      }
    } catch (err) {
      setError(err);
    } finally {
      setLoading(false);
    }
  }, [canViewSelf]);

  useEffect(() => {
    if (state !== "AUTHENTICATED" || !canView) return;
    const timer = window.setTimeout(() => void load(), 0);
    return () => window.clearTimeout(timer);
  }, [state, canView, load]);

  const selectedGoal = useMemo(
    () => goals.find((goal) => goal.id === selectedGoalId) ?? null,
    [goals, selectedGoalId],
  );

  async function loadTeamGoals() {
    const employmentId = teamEmploymentId.trim();
    if (!employmentId) return;
    setTeamLoading(true);
    setTeamError(null);
    try {
      setTeamGoals(await hrG3Api.listTeamGoals(employmentId));
    } catch (err) {
      setTeamGoals(null);
      setTeamError(err);
    } finally {
      setTeamLoading(false);
    }
  }

  function selectGoal(goalId: string) {
    setSelectedGoalId(goalId);
    setProgressDraft("");
    setProgressError(null);
    setMutationError(null);
    setNotice(false);
  }

  async function submitProgress() {
    if (!selectedGoal) return;
    setProgressError(null);
    setMutationError(null);
    setNotice(false);

    const parsed = Number(progressDraft);
    if (progressDraft.trim() === "" || !Number.isInteger(parsed) || parsed < 0 || parsed > 100) {
      setProgressError(t("hrm.g3.goals.validation.progressRange"));
      return;
    }

    setBusy(true);
    try {
      await hrG3Api.updateGoalProgress(selectedGoal.id, { progress: parsed });
      // No optimistic fake success: reconcile from the backend response path.
      await load();
      setNotice(true);
      setProgressDraft("");
    } catch (err) {
      setMutationError(hrmErrorMessage(err).message);
    } finally {
      setBusy(false);
    }
  }

  if (AUTH_LOADING_STATES.has(state)) {
    return <AuthLoadingState phase="session" />;
  }

  if (!canView) {
    return (
      <HrWorkspace capabilities={capabilities} activeHref="/hr/performance/goals" translate={t}>
        <p role="alert" className={styles.kpiHint} data-testid="goals-capability-denied">
          {t("hrm.g3.goals.forbidden")}
        </p>
      </HrWorkspace>
    );
  }

  const statusLabel = (code: string) => t(`hrm.g3.goals.state.${code}`);
  const periodLabel = (goal: G3PerformanceGoal) =>
    `${formatLocalizedDate(goal.startsOn, locale)} – ${formatLocalizedDate(goal.endsOn, locale)}`;

  const columns: HrColumn<G3PerformanceGoal>[] = [
    { key: "title", header: t("hrm.g3.goals.col.title") },
    { key: "metric", header: t("hrm.g3.goals.col.metric") },
    { key: "targetValue", header: t("hrm.g3.goals.col.target"), align: "end" },
    { key: "progress", header: t("hrm.g3.goals.col.progress"), align: "end", render: (goal) => `${goal.progress}%` },
    { key: "status", header: t("hrm.g3.goals.col.status"), render: (goal) => (
      <HrStateBadge label={statusLabel(goal.status)} code={goal.status} tone={toneForState(goal.status)} />
    )},
    { key: "period", header: t("hrm.g3.goals.col.period"), render: periodLabel },
    { key: "updatedAt", header: t("hrm.g3.goals.col.updated"), render: (goal) => formatLocalizedDate(goal.updatedAt.slice(0, 10), locale) },
  ];

  const totalGoals = goals.length;
  const averageProgress = totalGoals === 0 ? 0 : Math.round(goals.reduce((sum, goal) => sum + goal.progress, 0) / totalGoals);
  const completedGoals = goals.filter((goal) => goal.status === "COMPLETED").length;

  return (
    <HrWorkspace capabilities={capabilities} activeHref="/hr/performance/goals" translate={t}>
      <div data-testid="goals-product-surface">
        <HrProductHeader
          eyebrow={t("hrm.g3.goals.eyebrow")}
          title={t("hrm.g3.goals.title")}
          subtitle={t("hrm.g3.goals.subtitle")}
        />

        {loading ? (
          <HrLoading label={t("hrm.g3.goals.loading")} />
        ) : error ? (
          <HrErrorState error={error} onRetry={load} />
        ) : (
          <div data-testid="goals-ready">
            {notice ? (
              <p role="status" className={styles.kpiHint} data-testid="goals-notice">
                {t("hrm.g3.goals.notice.progressUpdated")}
              </p>
            ) : null}

            {mutationError ? (
              <p role="alert" className={styles.kpiHint} data-kind="error" data-testid="goals-mutation-error">
                {mutationError}
              </p>
            ) : null}

            <HrKpiGrid label={t("hrm.g3.goals.title")}>
              <div data-testid="goals-kpi-total">
                <HrKpiCard label={t("hrm.g3.goals.kpi.total")} value={String(totalGoals)} hint={t("hrm.g3.goals.col.title")} />
              </div>
              <div data-testid="goals-kpi-avg">
                <HrKpiCard label={t("hrm.g3.goals.kpi.avgProgress")} value={`${averageProgress}%`} hint={t("hrm.g3.goals.col.progress")} tone="positive" />
              </div>
              <div data-testid="goals-kpi-completed">
                <HrKpiCard label={t("hrm.g3.goals.kpi.completed")} value={String(completedGoals)} hint={t("hrm.g3.goals.col.status")} tone="positive" />
              </div>
            </HrKpiGrid>

            {canManageTeam ? (
              <div data-testid="goals-team-panel">
                <HrOperationalPanel
                  label={t("hrm.g3.workspace.nav.goals")}
                  title={t("hrm.g3.workspace.nav.goals")}
                  description={t("hrm.g3.reviews.team.description")}
                >
                  <div className={styles.actionRow}>
                    <label htmlFor="goals-team-employment-input" className={styles.kpiLabel}>
                      {t("hrm.g3.reviews.team.employmentId")}
                    </label>
                    <input
                      id="goals-team-employment-input"
                      className={styles.filterSelect}
                      value={teamEmploymentId}
                      onChange={(event) => setTeamEmploymentId(event.target.value)}
                      disabled={teamLoading}
                      data-testid="goals-team-employment-input"
                    />
                    <button
                      type="button"
                      className={styles.linkButton}
                      disabled={teamLoading || teamEmploymentId.trim() === ""}
                      onClick={() => void loadTeamGoals()}
                      data-testid="goals-team-load"
                    >
                      {teamLoading ? t("hrm.g3.goals.loading") : t("hrm.g3.workspace.nav.goals")}
                    </button>
                  </div>
                  {teamError ? <HrErrorState error={teamError} onRetry={() => void loadTeamGoals()} /> : null}
                  {teamGoals ? (
                    <HrDataTable<G3PerformanceGoal>
                      caption={t("hrm.g3.workspace.nav.goals")}
                      columns={columns}
                      rows={teamGoals}
                      rowKey={(goal) => goal.id}
                      emptyTitle={t("hrm.g3.goals.empty.title")}
                      emptyDescription={t("hrm.g3.goals.empty.description")}
                    />
                  ) : null}
                </HrOperationalPanel>
              </div>
            ) : null}

            {canUpdate && selectedGoal ? (
              <HrActionBar label={t("hrm.g3.goals.action.updateProgress")}>
                <HrOperationalPanel
                  label={t("hrm.g3.goals.action.updateProgress")}
                  title={t("hrm.g3.goals.action.updateProgress")}
                  description={periodLabel(selectedGoal)}
                >
                  <form
                    onSubmit={(event) => { event.preventDefault(); void submitProgress(); }}
                    aria-label={t("hrm.g3.goals.action.updateProgress")}
                    data-testid="goals-progress-form"
                  >
                    <p>
                      <label htmlFor="goals-update-select" className={styles.kpiLabel}>
                        {t("hrm.g3.goals.form.selectGoal")}
                      </label>
                      <select
                        id="goals-update-select"
                        className={styles.filterSelect}
                        value={selectedGoalId}
                        onChange={(event) => selectGoal(event.target.value)}
                        data-testid="goals-update-select"
                      >
                        {goals.map((goal) => (
                          <option key={goal.id} value={goal.id}>{goal.title}</option>
                        ))}
                      </select>
                    </p>
                    <p>
                      <label htmlFor="goals-progress-input" className={styles.kpiLabel}>
                        {t("hrm.g3.goals.progress.label")}
                      </label>
                      <input
                        id="goals-progress-input"
                        type="number"
                        className={styles.filterSelect}
                        value={progressDraft}
                        onChange={(event) => setProgressDraft(event.target.value)}
                        disabled={busy}
                        aria-required="true"
                        aria-label={t("hrm.g3.goals.progress.label")}
                        data-testid={`progress-input-${selectedGoalId}`}
                      />
                    </p>
                    {progressError ? (
                      <p role="alert" className={styles.kpiHint} data-kind="error" data-testid={`progress-error-${selectedGoalId}`}>
                        {progressError}
                      </p>
                    ) : null}
                    <div className={styles.actionRow}>
                      <button
                        type="submit"
                        className={styles.linkButton}
                        disabled={busy}
                        data-testid={`progress-save-${selectedGoalId}`}
                      >
                        {busy ? t("hrm.g3.goals.action.saving") : t("hrm.g3.goals.action.updateProgress")}
                      </button>
                    </div>
                  </form>
                </HrOperationalPanel>
              </HrActionBar>
            ) : null}

            <div data-testid="goals-list-panel">
              <HrOperationalPanel
                label={t("hrm.g3.goals.title")}
                title={t("hrm.g3.goals.title")}
                description={t("hrm.g3.goals.subtitle")}
              >
                <HrDataTable<G3PerformanceGoal>
                  caption={t("hrm.g3.goals.title")}
                  columns={columns}
                  rows={goals}
                  rowKey={(goal) => goal.id}
                  emptyTitle={t("hrm.g3.goals.empty.title")}
                  emptyDescription={t("hrm.g3.goals.empty.description")}
                />
                <HrMobileRecordList label={t("hrm.g3.goals.title")}>
                  {goals.map((goal) => (
                    <article key={goal.id} className={styles.statCard}>
                      <strong>{goal.title}</strong>
                      <span>{goal.metric} · {t("hrm.g3.goals.col.target")}: {goal.targetValue}</span>
                      <span>{t("hrm.g3.goals.col.progress")}: {goal.progress}%</span>
                      <span>{periodLabel(goal)}</span>
                      <HrStateBadge label={statusLabel(goal.status)} code={goal.status} tone={toneForState(goal.status)} />
                    </article>
                  ))}
                </HrMobileRecordList>
              </HrOperationalPanel>
            </div>
          </div>
        )}
      </div>
    </HrWorkspace>
  );
}
