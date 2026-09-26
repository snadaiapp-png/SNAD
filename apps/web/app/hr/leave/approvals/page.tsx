"use client";

import { useCallback, useEffect, useState } from "react";
import { AuthLoadingState } from "@/components/auth/auth-loading-state";
import { hrG2Api } from "@/lib/api/hr-g2-api";
import { useAuth } from "@/lib/auth/auth-provider";
import { useI18n } from "@/lib/i18n/I18nProvider";
import { HrWorkspace } from "../../components/hr-workspace";
import { HrErrorState, HrLoading, hrmErrorMessage } from "../../components/hr-feedback";
import { HrDataTable, type HrColumn } from "../../components/hr-data-table";
import { HrStateBadge, toneForState } from "../../components/hr-state-badge";
import { HrActionBar, HrKpiCard, HrKpiGrid, HrMobileRecordList, HrOperationalPanel, HrProductHeader } from "../../components/hr-product-surface";
import { formatLocalizedDate } from "../../hr-labels";
import styles from "../../hr.module.css";

interface LeaveRequest { id: string; employmentId: string; leaveTypeId: string; startDate: string; endDate: string; daysCount: string; reason: string | null; state: string; submittedAt: string | null; }

export default function LeaveApprovalsPage() {
  const { state, me } = useAuth();
  const { t, locale } = useI18n();
  const capabilities = me?.capabilities ?? [];
  const canManagerApprove = capabilities.includes("HRM.LEAVE.TEAM_APPROVE");
  const canHrApprove = capabilities.includes("HRM.LEAVE.HR_APPROVE");
  const canView = canManagerApprove || canHrApprove;
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<unknown>(null);
  const [requests, setRequests] = useState<LeaveRequest[]>([]);
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true); setError(null);
    try {
      const [team, hr] = await Promise.all([
        canManagerApprove ? hrG2Api.listTeamLeaveRequests("PENDING_MANAGER") : Promise.resolve([]),
        canHrApprove ? hrG2Api.listHrLeaveRequests("PENDING_HR") : Promise.resolve([]),
      ]);
      const byId = new Map<string, LeaveRequest>();
      for (const request of [...team, ...hr]) byId.set(request.id, request);
      setRequests(Array.from(byId.values()));
    } catch (err) { setError(err); } finally { setLoading(false); }
  }, [canManagerApprove, canHrApprove]);
  useEffect(() => { if (state !== "AUTHENTICATED") return; const timer = window.setTimeout(() => void load(), 0); return () => window.clearTimeout(timer); }, [state, load]);

  async function managerApprove(id: string) { setBusy(true); try { await hrG2Api.managerApproveLeave(id, "Manager approved (G2 E2E)"); setNotice(t("hrm.leaveApprovals.managerApproved")); await load(); } catch (err) { setNotice(hrmErrorMessage(err).message); } finally { setBusy(false); } }
  async function hrApprove(id: string) { setBusy(true); try { await hrG2Api.hrApproveLeave(id, "HR approved (G2 E2E)"); setNotice(t("hrm.leaveApprovals.hrApproved")); await load(); } catch (err) { setNotice(hrmErrorMessage(err).message); } finally { setBusy(false); } }
  async function managerReject(id: string) { setBusy(true); try { await hrG2Api.managerRejectLeave(id, "Manager rejected (G2 E2E)"); setNotice(t("hrm.leaveApprovals.managerRejected")); await load(); } catch (err) { setNotice(hrmErrorMessage(err).message); } finally { setBusy(false); } }
  async function hrReject(id: string) { setBusy(true); try { await hrG2Api.hrRejectLeave(id, "HR rejected (G2 E2E)"); setNotice(t("hrm.leaveApprovals.hrRejected")); await load(); } catch (err) { setNotice(hrmErrorMessage(err).message); } finally { setBusy(false); } }

  if (["INITIALIZING", "CHECKING_SESSION", "REFRESHING"].includes(state)) return <AuthLoadingState phase="session" />;
  if (!canView) return <HrWorkspace capabilities={capabilities} activeHref="/hr/leave/approvals" translate={t}><p role="alert" className={styles.kpiHint}>{t("hrm.recruitment.dashboard.permissionHint")}</p></HrWorkspace>;

  const managerCount = requests.filter((r) => r.state === "PENDING_MANAGER").length;
  const hrCount = requests.filter((r) => r.state === "PENDING_HR").length;
  const columns: HrColumn<LeaveRequest>[] = [
    { key: "startDate", header: t("hrm.leaveApprovals.from"), render: (r) => formatLocalizedDate(r.startDate, locale) },
    { key: "endDate", header: t("hrm.leaveApprovals.to"), render: (r) => formatLocalizedDate(r.endDate, locale) },
    { key: "daysCount", header: t("hrm.leaveApprovals.days"), align: "end" },
    { key: "reason", header: t("hrm.leaveApprovals.reason"), render: (r) => r.reason ?? "—" },
    { key: "state", header: t("hrm.leaveApprovals.state"), render: (r) => <HrStateBadge label={t("hrm.leave.state." + r.state)} code={r.state} tone={toneForState(r.state)} /> },
    { key: "actions", header: t("hrm.leaveApprovals.actions"), render: (r) => <span className={styles.actionRow}>
      {r.state === "PENDING_MANAGER" && canManagerApprove ? <><button type="button" className={styles.linkButton} onClick={() => void managerApprove(r.id)} disabled={busy} data-testid={`manager-approve-${r.id}`}>{t("hrm.leaveApprovals.managerApprove")}</button><button type="button" className={styles.linkButton} onClick={() => void managerReject(r.id)} disabled={busy} data-testid={`manager-reject-${r.id}`}>{t("hrm.leaveApprovals.managerReject")}</button></> : null}
      {r.state === "PENDING_HR" && canHrApprove ? <><button type="button" className={styles.linkButton} onClick={() => void hrApprove(r.id)} disabled={busy} data-testid={`hr-approve-${r.id}`}>{t("hrm.leaveApprovals.hrApprove")}</button><button type="button" className={styles.linkButton} onClick={() => void hrReject(r.id)} disabled={busy} data-testid={`hr-reject-${r.id}`}>{t("hrm.leaveApprovals.hrReject")}</button></> : null}
    </span> },
  ];

  return <HrWorkspace capabilities={capabilities} activeHref="/hr/leave/approvals" translate={t}>
    <div data-testid="leave-approvals-product-surface">
      <HrProductHeader eyebrow={canHrApprove ? t("hrm.g2.landing.hrOperations") : t("hrm.g2.landing.myTeam")} title={t("hrm.leaveApprovals.title")} subtitle={t("hrm.leaveApprovals.subtitle")} />
      {notice ? <p role="status" className={styles.kpiHint}>{notice}</p> : null}
      {loading ? <HrLoading /> : error ? <HrErrorState error={error} onRetry={load} /> : <div data-testid="leave-approvals-ready">
        <HrKpiGrid label={t("hrm.leaveApprovals.title")}>
          <div data-testid="leave-approvals-manager-count"><HrKpiCard label={t("hrm.leave.state.PENDING_MANAGER")} value={managerCount} tone={managerCount > 0 ? "attention" : "neutral"} /></div>
          <div data-testid="leave-approvals-hr-count"><HrKpiCard label={t("hrm.leave.state.PENDING_HR")} value={hrCount} tone={hrCount > 0 ? "attention" : "neutral"} /></div>
          <HrKpiCard label={t("hrm.leaveApprovals.caption")} value={requests.length} />
        </HrKpiGrid>
        <HrActionBar label={t("hrm.leaveApprovals.title")}><span className={styles.kpiHint}>{canHrApprove ? t("hrm.g2.landing.hrOperations") : t("hrm.g2.landing.myTeam")}</span></HrActionBar>
        <div data-testid="leave-approvals-queue-panel"><HrOperationalPanel label={t("hrm.leaveApprovals.caption")} title={t("hrm.leaveApprovals.caption")} description={t("hrm.leaveApprovals.subtitle")}>
          <HrDataTable<LeaveRequest> caption={t("hrm.leaveApprovals.caption")} columns={columns} rows={requests} rowKey={(r) => r.id} emptyTitle={t("hrm.leaveApprovals.empty")} />
          <HrMobileRecordList label={t("hrm.leaveApprovals.caption")}>{requests.map((r) => <article key={r.id} className={styles.statCard}><strong>{formatLocalizedDate(r.startDate, locale)} — {formatLocalizedDate(r.endDate, locale)}</strong><span>{r.daysCount}</span><HrStateBadge label={t("hrm.leave.state." + r.state)} code={r.state} tone={toneForState(r.state)} /><span className={styles.actionRow}>{r.state === "PENDING_MANAGER" && canManagerApprove ? <button type="button" className={styles.linkButton} onClick={() => void managerApprove(r.id)} disabled={busy}>{t("hrm.leaveApprovals.managerApprove")}</button> : null}{r.state === "PENDING_HR" && canHrApprove ? <button type="button" className={styles.linkButton} onClick={() => void hrApprove(r.id)} disabled={busy}>{t("hrm.leaveApprovals.hrApprove")}</button> : null}</span></article>)}</HrMobileRecordList>
        </HrOperationalPanel></div>
      </div>}
    </div>
  </HrWorkspace>;
}
