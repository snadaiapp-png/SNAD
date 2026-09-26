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
import {
  HrActionBar,
  HrKpiCard,
  HrKpiGrid,
  HrMobileRecordList,
  HrOperationalPanel,
  HrProductHeader,
} from "../components/hr-product-surface";
import { formatLocalizedDate } from "../hr-labels";
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

function stateLabel(state: string, locale: "ar" | "en"): string {
  const labels: Record<string, { ar: string; en: string }> = {
    DRAFT: { ar: "مسودة", en: "Draft" },
    SUBMITTED: { ar: "مرسل", en: "Submitted" },
    APPROVED: { ar: "معتمد", en: "Approved" },
    REJECTED: { ar: "مرفوض", en: "Rejected" },
  };
  return labels[state]?.[locale] ?? state;
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

  const orderedTimesheets = [...timesheets].sort((a, b) => b.periodEnd.localeCompare(a.periodEnd));
  const currentTimesheet = orderedTimesheets[0] ?? null;
  const submittedCount = timesheets.filter((item) => item.state === "SUBMITTED").length;
  const approvedCount = timesheets.filter((item) => item.state === "APPROVED").length;

  const columns: HrColumn<Timesheet>[] = [
    {
      key: "periodStart",
      header: t("hrm.timesheets.period"),
      render: (r) => `${formatLocalizedDate(r.periodStart, locale)} — ${formatLocalizedDate(r.periodEnd, locale)}`,
    },
    {
      key: "state",
      header: t("hrm.timesheets.state"),
      render: (r) => <HrStateBadge label={stateLabel(r.state, locale)} code={r.state} tone={toneForState(r.state)} />,
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
      <div data-testid="timesheets-product-surface">
        <HrProductHeader
          eyebrow={t("hrm.g2.landing.myWorkday")}
          title={t("hrm.timesheets.title")}
          subtitle={t("hrm.timesheets.subtitle")}
          trailing={currentTimesheet ? <HrStateBadge label={stateLabel(currentTimesheet.state, locale)} code={currentTimesheet.state} tone={toneForState(currentTimesheet.state)} /> : null}
        />

        {notice ? <p role="status" className={styles.kpiHint}>{notice}</p> : null}

        {loading ? <HrLoading /> : error ? <HrErrorState error={error} onRetry={load} /> : (
          <div data-testid="timesheets-ready">
            <HrKpiGrid label={t("hrm.timesheets.title")}>
              <div data-testid="timesheets-current-period">
                <HrKpiCard
                  label={t("hrm.timesheets.period")}
                  value={currentTimesheet ? `${formatLocalizedDate(currentTimesheet.periodStart, locale)} — ${formatLocalizedDate(currentTimesheet.periodEnd, locale)}` : "—"}
                />
              </div>
              <div data-testid="timesheets-current-state">
                <HrKpiCard
                  label={t("hrm.timesheets.state")}
                  value={currentTimesheet ? stateLabel(currentTimesheet.state, locale) : "—"}
                  tone={currentTimesheet?.state === "APPROVED" ? "positive" : currentTimesheet?.state === "SUBMITTED" ? "attention" : "neutral"}
                />
              </div>
              <HrKpiCard label={locale === "ar" ? "بانتظار الاعتماد" : "Awaiting approval"} value={submittedCount} />
              <HrKpiCard label={locale === "ar" ? "معتمدة" : "Approved"} value={approvedCount} tone="positive" />
            </HrKpiGrid>

            <HrActionBar label={t("hrm.timesheets.actions")}>
              <div data-testid="timesheets-submit-action">
                {canSubmit && currentTimesheet?.state === "DRAFT" ? (
                  <button type="button" className={styles.linkButton} onClick={() => void submit(currentTimesheet)} disabled={busy}>
                    {busy ? t("hrm.timesheets.submitting") : t("hrm.timesheets.submit")}
                  </button>
                ) : <span className={styles.kpiHint}>{currentTimesheet ? stateLabel(currentTimesheet.state, locale) : t("hrm.timesheets.empty")}</span>}
              </div>
            </HrActionBar>

            <div data-testid="timesheets-history-panel">
              <HrOperationalPanel label={t("hrm.timesheets.title")} title={t("hrm.timesheets.title")} description={t("hrm.timesheets.subtitle")}>
                <HrDataTable<Timesheet>
                  caption={t("hrm.timesheets.title")}
                  columns={columns}
                  rows={orderedTimesheets}
                  rowKey={(r) => r.id}
                  emptyTitle={t("hrm.timesheets.empty")}
                />
                <HrMobileRecordList label={t("hrm.timesheets.title")}>
                  {orderedTimesheets.slice(0, 5).map((item) => (
                    <article key={item.id} className={styles.statCard}>
                      <strong>{formatLocalizedDate(item.periodStart, locale)} — {formatLocalizedDate(item.periodEnd, locale)}</strong>
                      <HrStateBadge label={stateLabel(item.state, locale)} code={item.state} tone={toneForState(item.state)} />
                      {item.approverComment ? <span>{item.approverComment}</span> : null}
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
