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
import { HrActionBar, HrKpiCard, HrKpiGrid, HrMobileRecordList, HrOperationalPanel, HrProductHeader } from "../components/hr-product-surface";
import { formatLocalizedDate } from "../hr-labels";
import styles from "../hr.module.css";

interface Timesheet { id: string; employmentId: string; periodStart: string; periodEnd: string; state: string; submittedAt: string | null; approverComment: string | null; }

export default function TeamTimesheetsPage() {
  const { state, me } = useAuth();
  const { t, locale } = useI18n();
  const capabilities = me?.capabilities ?? [];
  const canApprove = capabilities.includes("HRM.TIMESHEET.TEAM_APPROVE");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<unknown>(null);
  const [timesheets, setTimesheets] = useState<Timesheet[]>([]);
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState<string | null>(null);
  const load = useCallback(async () => { setLoading(true); setError(null); try { setTimesheets(await hrG2Api.listTeamTimesheets("SUBMITTED")); } catch (err) { setError(err); } finally { setLoading(false); } }, []);
  useEffect(() => { if (state !== "AUTHENTICATED") return; const timer = window.setTimeout(() => void load(), 0); return () => window.clearTimeout(timer); }, [state, load]);
  async function approve(id: string) { setBusy(true); try { await hrG2Api.approveTimesheet(id, "approved"); setNotice(t("hrm.timesheets.team.approved")); await load(); } catch (err) { setNotice(hrmErrorMessage(err).message); } finally { setBusy(false); } }
  async function reject(id: string) { setBusy(true); try { await hrG2Api.rejectTimesheet(id, "rejected"); setNotice(t("hrm.timesheets.team.rejected")); await load(); } catch (err) { setNotice(hrmErrorMessage(err).message); } finally { setBusy(false); } }
  if (["INITIALIZING", "CHECKING_SESSION", "REFRESHING"].includes(state)) return <AuthLoadingState phase="session" />;
  if (!canApprove) return <HrWorkspace capabilities={capabilities} activeHref="/hr/team-timesheets" translate={t}><p role="alert" className={styles.kpiHint}>{t("hrm.recruitment.dashboard.permissionHint")}</p></HrWorkspace>;

  const pendingCount = timesheets.filter((sheet) => sheet.state === "SUBMITTED").length;
  const columns: HrColumn<Timesheet>[] = [
    { key: "periodStart", header: t("hrm.timesheets.period"), render: (r) => `${formatLocalizedDate(r.periodStart, locale)} — ${formatLocalizedDate(r.periodEnd, locale)}` },
    { key: "state", header: t("hrm.timesheets.state"), render: (r) => <HrStateBadge label={t("hrm.timesheets.state." + r.state)} code={r.state} tone={toneForState(r.state)} /> },
    { key: "actions", header: t("hrm.timesheets.actions"), render: (r) => <span className={styles.actionRow}>{r.state === "SUBMITTED" ? <><button type="button" data-testid={`team-timesheets-approve-action-${r.id}`} className={styles.linkButton} onClick={() => void approve(r.id)} disabled={busy}>{t("hrm.timesheets.team.approve")}</button><button type="button" data-testid={`team-timesheets-reject-action-${r.id}`} className={styles.linkButton} onClick={() => void reject(r.id)} disabled={busy}>{t("hrm.timesheets.team.reject")}</button></> : null}</span> },
  ];

  return <HrWorkspace capabilities={capabilities} activeHref="/hr/team-timesheets" translate={t}>
    <div data-testid="team-timesheets-product-surface">
      <HrProductHeader eyebrow={t("hrm.g2.landing.myTeam")} title={t("hrm.timesheets.team.title")} subtitle={t("hrm.timesheets.team.subtitle")} />
      {notice ? <p role="status" className={styles.kpiHint}>{notice}</p> : null}
      {loading ? <HrLoading /> : error ? <HrErrorState error={error} onRetry={load} /> : <div data-testid="team-timesheets-ready">
        <HrKpiGrid label={t("hrm.timesheets.team.title")}>
          <div data-testid="team-timesheets-pending-count"><HrKpiCard label={t("hrm.timesheets.kpi.awaitingApproval")} value={pendingCount} tone={pendingCount > 0 ? "attention" : "neutral"} /></div>
          <HrKpiCard label={t("hrm.timesheets.team.caption")} value={timesheets.length} />
        </HrKpiGrid>
        <HrActionBar label={t("hrm.timesheets.team.title")}><span className={styles.kpiHint}>{t("hrm.timesheets.team.subtitle")}</span></HrActionBar>
        <div data-testid="team-timesheets-queue-panel"><HrOperationalPanel label={t("hrm.timesheets.team.caption")} title={t("hrm.timesheets.team.caption")} description={t("hrm.timesheets.team.subtitle")}>
          <HrDataTable<Timesheet> caption={t("hrm.timesheets.team.caption")} columns={columns} rows={timesheets} rowKey={(r) => r.id} emptyTitle={t("hrm.timesheets.team.empty")} />
          <HrMobileRecordList label={t("hrm.timesheets.team.caption")}>{timesheets.map((sheet) => <article key={sheet.id} className={styles.statCard}><strong>{formatLocalizedDate(sheet.periodStart, locale)} — {formatLocalizedDate(sheet.periodEnd, locale)}</strong><HrStateBadge label={t("hrm.timesheets.state." + sheet.state)} code={sheet.state} tone={toneForState(sheet.state)} />{sheet.state === "SUBMITTED" ? <span className={styles.actionRow}><button type="button" data-testid={`team-timesheets-approve-action-mobile-${sheet.id}`} className={styles.linkButton} onClick={() => void approve(sheet.id)} disabled={busy}>{t("hrm.timesheets.team.approve")}</button><button type="button" data-testid={`team-timesheets-reject-action-mobile-${sheet.id}`} className={styles.linkButton} onClick={() => void reject(sheet.id)} disabled={busy}>{t("hrm.timesheets.team.reject")}</button></span> : null}</article>)}</HrMobileRecordList>
        </HrOperationalPanel></div>
      </div>}
    </div>
  </HrWorkspace>;
}
