"use client";

import { useCallback, useEffect, useState } from "react";
import { AuthLoadingState } from "@/components/auth/auth-loading-state";
import { hrG2Api } from "@/lib/api/hr-g2-api";
import { useAuth } from "@/lib/auth/auth-provider";
import { useI18n } from "@/lib/i18n/I18nProvider";
import { HrWorkspace } from "../components/hr-workspace";
import { HrG2ProductSurface } from "../components/hr-g2-product-surface";
import { HrErrorState, HrLoading, hrmErrorMessage } from "../components/hr-feedback";
import { HrDataTable, type HrColumn } from "../components/hr-data-table";
import { HrStateBadge, toneForState } from "../components/hr-state-badge";
import { formatLocalizedDate } from "../hr-labels";
import visualStyles from "../components/hr-g2-visual.module.css";
import styles from "../hr.module.css";

interface Timesheet {
  id: string;
  employmentId: string;
  periodStart: string;
  periodEnd: string;
  state: string;
  submittedAt: string | null;
  approverComment: string | null;
}

export default function TimesheetsPage() {
  const { state, me } = useAuth();
  const { t, locale } = useI18n();
  const capabilities = me?.capabilities ?? [];
  const canView = capabilities.includes("HRM.TIMESHEET.SELF_VIEW");
  const canSubmit = capabilities.includes("HRM.TIMESHEET.SELF_SUBMIT");

  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<unknown>(null);
  const [timesheets, setTimesheets] = useState<Timesheet[]>([]);
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setTimesheets(await hrG2Api.listTimesheets());
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

  async function submit(ts: Timesheet) {
    setBusy(true);
    try {
      await hrG2Api.submitTimesheet(ts.id);
      setNotice(t("hrm.timesheets.submitted"));
      await load();
    } catch (err) {
      setNotice(hrmErrorMessage(err).message);
    } finally {
      setBusy(false);
    }
  }

  if (["INITIALIZING", "CHECKING_SESSION", "REFRESHING"].includes(state)) return <AuthLoadingState phase="session" />;
  if (!canView) return <HrWorkspace capabilities={capabilities} activeHref="/hr/timesheets" translate={t}><p role="alert" className={styles.kpiHint}>{t("hrm.recruitment.dashboard.permissionHint")}</p></HrWorkspace>;

  const columns: HrColumn<Timesheet>[] = [
    { key: "periodStart", header: t("hrm.timesheets.period"), render: (r) => `${formatLocalizedDate(r.periodStart, locale)} — ${formatLocalizedDate(r.periodEnd, locale)}` },
    { key: "state", header: t("hrm.timesheets.state"), render: (r) => <HrStateBadge label={r.state} code={r.state} tone={toneForState(r.state)} /> },
    { key: "approverComment", header: t("hrm.timesheets.comment"), render: (r) => r.approverComment ?? "—" },
    ...(canSubmit ? [{ key: "actions", header: t("hrm.timesheets.actions"), render: (r: Timesheet) => r.state === "DRAFT" ? (
      <button type="button" className={styles.linkButton} onClick={() => void submit(r)} disabled={busy}>{busy ? t("hrm.timesheets.submitting") : t("hrm.timesheets.submit")}</button>
    ) : "—" }] : []),
  ];

  const draftCount = timesheets.filter((item) => item.state === "DRAFT").length;
  const submittedCount = timesheets.filter((item) => item.state !== "DRAFT").length;
  const latest = timesheets[0];

  return (
    <HrWorkspace capabilities={capabilities} activeHref="/hr/timesheets" translate={t}>
      <HrG2ProductSurface
        eyebrow={t("hrm.g2.landing.myWorkday")}
        title={t("hrm.timesheets.title")}
        subtitle={t("hrm.timesheets.subtitle")}
        metrics={[
          { label: t("hrm.timesheets.state"), value: <span data-testid="timesheet-draft-card">{draftCount}</span>, hint: t("hrm.timesheets.submit") },
          { label: t("hrm.timesheets.team.approved"), value: <span data-testid="timesheet-submitted-card">{submittedCount}</span> },
          { label: t("hrm.timesheets.period"), value: <span data-testid="timesheet-period-panel">{latest ? `${formatLocalizedDate(latest.periodStart, locale)} — ${formatLocalizedDate(latest.periodEnd, locale)}` : "—"}</span> },
        ]}
      >
        {notice ? <p role="status" className={styles.kpiHint}>{notice}</p> : null}
        {loading ? <HrLoading /> : error ? <HrErrorState error={error} onRetry={load} /> : (
          <section className={visualStyles.productPanel}>
            <div data-testid="timesheets-ready">
              <HrDataTable<Timesheet> caption={t("hrm.timesheets.title")} columns={columns} rows={timesheets} rowKey={(r) => r.id} emptyTitle={t("hrm.timesheets.empty")} />
            </div>
          </section>
        )}
      </HrG2ProductSurface>
    </HrWorkspace>
  );
}
