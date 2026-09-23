"use client";

import { useCallback, useEffect, useState } from "react";
import { AuthLoadingState } from "@/components/auth/auth-loading-state";
import { useAuth } from "@/lib/auth/auth-provider";
import { useI18n } from "@/lib/i18n/I18nProvider";
import { HrWorkspace } from "../components/hr-workspace";
import { HrErrorState, HrLoading, HrEmptyState, hrmErrorMessage } from "../components/hr-feedback";
import { HrDataTable, type HrColumn } from "../components/hr-data-table";
import { HrStateBadge, toneForState } from "../components/hr-state-badge";
import { formatArabicDate } from "../hr-labels";
import styles from "../hr.module.css";

interface Timesheet { id: string; employmentId: string; periodStart: string; periodEnd: string; state: string; submittedAt: string | null; approverComment: string | null; }

export default function TeamTimesheetsPage() {
  const { state, me } = useAuth();
  const { t } = useI18n();
  const capabilities = me?.capabilities ?? [];
  const canApprove = capabilities.includes("HRM.TIMESHEET.TEAM_APPROVE");

  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<unknown>(null);
  const [timesheets, setTimesheets] = useState<Timesheet[]>([]);
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true); setError(null);
    try {
      const res = await fetch("/api/platform/api/v2/hr/time/timesheets?state=SUBMITTED", { credentials: "same-origin" });
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      setTimesheets(await res.json());
    } catch (err) { setError(err); } finally { setLoading(false); }
  }, []);

  useEffect(() => {
    if (state !== "AUTHENTICATED") return;
    const timer = window.setTimeout(() => void load(), 0);
    return () => window.clearTimeout(timer);
  }, [state, load]);

  async function approve(id: string) {
    setBusy(true);
    try {
      const res = await fetch(`/api/platform/api/v2/hr/time/timesheets/${id}/approve`, {
        method: "POST", headers: { "Content-Type": "application/json", "Idempotency-Key": crypto.randomUUID() },
        body: JSON.stringify({ comment: "approved" }), credentials: "same-origin",
      });
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      setNotice("Timesheet approved"); await load();
    } catch (err) { setNotice(hrmErrorMessage(err).message); } finally { setBusy(false); }
  }

  async function reject(id: string) {
    setBusy(true);
    try {
      const res = await fetch(`/api/platform/api/v2/hr/time/timesheets/${id}/reject`, {
        method: "POST", headers: { "Content-Type": "application/json", "Idempotency-Key": crypto.randomUUID() },
        body: JSON.stringify({ reason: "rejected" }), credentials: "same-origin",
      });
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      setNotice("Timesheet rejected"); await load();
    } catch (err) { setNotice(hrmErrorMessage(err).message); } finally { setBusy(false); }
  }

  if (["INITIALIZING","CHECKING_SESSION","REFRESHING"].includes(state)) return <AuthLoadingState phase="session" />;
  if (!canApprove) return <HrWorkspace capabilities={capabilities} activeHref="/hr/team-timesheets"><p role="alert" className={styles.kpiHint}>{t("hrm.recruitment.dashboard.permissionHint")}</p></HrWorkspace>;

  const columns: HrColumn<Timesheet>[] = [
    { key: "periodStart", header: "Period", render: (r) => `${formatArabicDate(r.periodStart)} — ${formatArabicDate(r.periodEnd)}` },
    { key: "state", header: "State", render: (r) => <HrStateBadge label={r.state} code={r.state} tone={toneForState(r.state)} /> },
    { key: "actions", header: "Actions", render: (r) => (
      <span className={styles.actionRow}>
        {r.state === "SUBMITTED" ? <>
          <button type="button" className={styles.linkButton} onClick={() => void approve(r.id)} disabled={busy}>Approve</button>
          <button type="button" className={styles.linkButton} onClick={() => void reject(r.id)} disabled={busy}>Reject</button>
        </> : null}
      </span>
    )},
  ];

  return (
    <HrWorkspace capabilities={capabilities} activeHref="/hr/team-timesheets">
      <header><h1>Team Timesheets</h1></header>
      {notice ? <p role="status" className={styles.kpiHint}>{notice}</p> : null}
      {loading ? <HrLoading /> : error ? <HrErrorState error={error} onRetry={load} /> : (
        <HrDataTable<Timesheet> caption="Pending Timesheet Approvals" columns={columns} rows={timesheets} rowKey={(r) => r.id} emptyTitle="No pending timesheets" />
      )}
    </HrWorkspace>
  );
}
