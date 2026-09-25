"use client";

import { useCallback, useEffect, useState } from "react";
import { AuthLoadingState } from "@/components/auth/auth-loading-state";
import { hrG2Api } from "@/lib/api/hr-g2-api";
import { useAuth } from "@/lib/auth/auth-provider";
import { useI18n } from "@/lib/i18n/I18nProvider";
import { HrWorkspace } from "../components/hr-workspace";
import { HrErrorState, HrLoading, hrmErrorMessage } from "../components/hr-feedback";
import { HrDataTable, type HrColumn } from "../components/hr-data-table";
import { HrStateBadge, toneForState } from "../components/hr-state-badge";
import { formatArabicDate } from "../hr-labels";
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
  const { t } = useI18n();
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

  if (["INITIALIZING", "CHECKING_SESSION", "REFRESHING"].includes(state)) {
    return <AuthLoadingState phase="session" />;
  }

  if (!canView) {
    return (
      <HrWorkspace capabilities={capabilities} activeHref="/hr/timesheets" translate={t}>
        <p role="alert" className={styles.kpiHint}>{t("hrm.recruitment.dashboard.permissionHint")}</p>
      </HrWorkspace>
    );
  }

  const columns: HrColumn<Timesheet>[] = [
    {
      key: "periodStart",
      header: t("hrm.timesheets.period"),
      render: (r) => `${formatArabicDate(r.periodStart)} — ${formatArabicDate(r.periodEnd)}`,
    },
    {
      key: "state",
      header: t("hrm.timesheets.state"),
      render: (r) => <HrStateBadge label={r.state} code={r.state} tone={toneForState(r.state)} />,
    },
    {
      key: "approverComment",
      header: t("hrm.timesheets.comment"),
      render: (r) => r.approverComment ?? "—",
    },
    ...(canSubmit ? [{
      key: "actions",
      header: t("hrm.timesheets.actions"),
      render: (r: Timesheet) => r.state === "DRAFT" ? (
        <button type="button" className={styles.linkButton} onClick={() => void submit(r)} disabled={busy}>
          {busy ? t("hrm.timesheets.submitting") : t("hrm.timesheets.submit")}
        </button>
      ) : "—",
    }] : []),
  ];

  return (
    <HrWorkspace capabilities={capabilities} activeHref="/hr/timesheets" translate={t}>
      <header>
        <h1 data-testid="g2-page-title">{t("hrm.timesheets.title")}</h1>
        <p className={styles.kpiHint}>{t("hrm.timesheets.subtitle")}</p>
      </header>
      {notice ? <p role="status" className={styles.kpiHint}>{notice}</p> : null}
      {loading ? <HrLoading /> : error ? <HrErrorState error={error} onRetry={load} /> : (
        <div data-testid="timesheets-ready">
          <HrDataTable<Timesheet>
            caption={t("hrm.timesheets.title")}
            columns={columns}
            rows={timesheets}
            rowKey={(r) => r.id}
            emptyTitle={t("hrm.timesheets.empty")}
          />
        </div>
      )}
    </HrWorkspace>
  );
}
