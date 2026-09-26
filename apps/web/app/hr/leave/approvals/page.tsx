"use client";

import { useCallback, useEffect, useState } from "react";
import { AuthLoadingState } from "@/components/auth/auth-loading-state";
import { hrG2Api } from "@/lib/api/hr-g2-api";
import { useAuth } from "@/lib/auth/auth-provider";
import { useI18n } from "@/lib/i18n/I18nProvider";
import { HrWorkspace } from "../../components/hr-workspace";
import { HrG2ProductSurface } from "../../components/hr-g2-product-surface";
import { HrErrorState, HrLoading, hrmErrorMessage } from "../../components/hr-feedback";
import { HrDataTable, type HrColumn } from "../../components/hr-data-table";
import { HrStateBadge, toneForState } from "../../components/hr-state-badge";
import { formatLocalizedDate } from "../../hr-labels";
import visualStyles from "../../components/hr-g2-visual.module.css";
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
      const [team, hr] = await Promise.all([canManagerApprove ? hrG2Api.listTeamLeaveRequests("PENDING_MANAGER") : Promise.resolve([]), canHrApprove ? hrG2Api.listHrLeaveRequests("PENDING_HR") : Promise.resolve([])]);
      const byId = new Map<string, LeaveRequest>(); for (const request of [...team, ...hr]) byId.set(request.id, request); setRequests(Array.from(byId.values()));
    } catch (err) { setError(err); } finally { setLoading(false); }
  }, [canManagerApprove, canHrApprove]);
  useEffect(() => { if (state !== "AUTHENTICATED") return; const timer = window.setTimeout(() => void load(), 0); return () => window.clearTimeout(timer); }, [state, load]);

  async function managerApprove(id: string) { setBusy(true); try { await hrG2Api.managerApproveLeave(id, "Manager approved (G2 E2E)"); setNotice(t("hrm.leaveApprovals.managerApproved")); await load(); } catch (err) { setNotice(hrmErrorMessage(err).message); } finally { setBusy(false); } }
  async function hrApprove(id: string) { setBusy(true); try { await hrG2Api.hrApproveLeave(id, "HR approved (G2 E2E)"); setNotice(t("hrm.leaveApprovals.hrApproved")); await load(); } catch (err) { setNotice(hrmErrorMessage(err).message); } finally { setBusy(false); } }
  async function managerReject(id: string) { setBusy(true); try { await hrG2Api.managerRejectLeave(id, "Manager rejected (G2 E2E)"); setNotice(t("hrm.leaveApprovals.managerRejected")); await load(); } catch (err) { setNotice(hrmErrorMessage(err).message); } finally { setBusy(false); } }
  async function hrReject(id: string) { setBusy(true); try { await hrG2Api.hrRejectLeave(id, "HR rejected (G2 E2E)"); setNotice(t("hrm.leaveApprovals.hrRejected")); await load(); } catch (err) { setNotice(hrmErrorMessage(err).message); } finally { setBusy(false); } }

  if (["INITIALIZING", "CHECKING_SESSION", "REFRESHING"].includes(state)) return <AuthLoadingState phase="session" />;
  if (!canView) return <HrWorkspace capabilities={capabilities} activeHref="/hr/leave/approvals" translate={t}><p role="alert" className={styles.kpiHint}>{t("hrm.recruitment.dashboard.permissionHint")}</p></HrWorkspace>;

  const columns: HrColumn<LeaveRequest>[] = [
    { key: "startDate", header: t("hrm.leaveApprovals.from"), render: (r) => formatLocalizedDate(r.startDate, locale) },
    { key: "endDate", header: t("hrm.leaveApprovals.to"), render: (r) => formatLocalizedDate(r.endDate, locale) },
    { key: "daysCount", header: t("hrm.leaveApprovals.days"), align: "end" },
    { key: "reason", header: t("hrm.leaveApprovals.reason"), render: (r) => r.reason ?? "—" },
    { key: "state", header: t("hrm.leaveApprovals.state"), render: (r) => <HrStateBadge label={r.state} code={r.state} tone={toneForState(r.state)} /> },
    { key: "actions", header: t("hrm.leaveApprovals.actions"), render: (r) => <span className={styles.actionRow}>
      {r.state === "PENDING_MANAGER" && canManagerApprove ? <><button type="button" className={styles.linkButton} onClick={() => void managerApprove(r.id)} disabled={busy} data-testid={`manager-approve-${r.id}`}>{t("hrm.leaveApprovals.managerApprove")}</button><button type="button" className={styles.linkButton} onClick={() => void managerReject(r.id)} disabled={busy} data-testid={`manager-reject-${r.id}`}>{t("hrm.leaveApprovals.managerReject")}</button></> : null}
      {r.state === "PENDING_HR" && canHrApprove ? <><button type="button" className={styles.linkButton} onClick={() => void hrApprove(r.id)} disabled={busy} data-testid={`hr-approve-${r.id}`}>{t("hrm.leaveApprovals.hrApprove")}</button><button type="button" className={styles.linkButton} onClick={() => void hrReject(r.id)} disabled={busy} data-testid={`hr-reject-${r.id}`}>{t("hrm.leaveApprovals.hrReject")}</button></> : null}
    </span> },
  ];
  const managerQueue = requests.filter((request) => request.state === "PENDING_MANAGER").length;
  const hrQueue = requests.filter((request) => request.state === "PENDING_HR").length;

  return (
    <HrWorkspace capabilities={capabilities} activeHref="/hr/leave/approvals" translate={t}>
      <HrG2ProductSurface eyebrow={canHrApprove ? t("hrm.g2.landing.hrOperations") : t("hrm.g2.landing.myTeam")} title={t("hrm.leaveApprovals.title")} subtitle={t("hrm.leaveApprovals.subtitle")}
        metrics={[
          { label: t("hrm.leaveApprovals.managerApprove"), value: managerQueue },
          { label: t("hrm.leaveApprovals.hrApprove"), value: hrQueue },
          { label: t("hrm.leaveApprovals.caption"), value: requests.length },
        ]}
      >
        {notice ? <p role="status" className={styles.kpiHint}>{notice}</p> : null}
        <section className={visualStyles.productPanel} data-testid="leave-approval-queue">
          {loading ? <HrLoading /> : error ? <HrErrorState error={error} onRetry={load} /> : <div data-testid="leave-approvals-ready"><HrDataTable<LeaveRequest> caption={t("hrm.leaveApprovals.caption")} columns={columns} rows={requests} rowKey={(r) => r.id} emptyTitle={t("hrm.leaveApprovals.empty")} /></div>}
        </section>
      </HrG2ProductSurface>
    </HrWorkspace>
  );
}
