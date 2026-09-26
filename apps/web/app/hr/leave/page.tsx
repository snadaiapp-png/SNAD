"use client";

/** Leave Management — G2-T04 SELF product surface. */

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

interface LeaveType { id: string; code: string; nameAr: string; nameEn: string; isPaid: boolean; }
interface LeaveRequest {
  id: string; employmentId: string; leaveTypeId: string;
  startDate: string; endDate: string; daysCount: string;
  reason: string | null; state: string;
  submittedAt: string | null; approvedAt: string | null; approverComment: string | null;
}
interface LeaveBalance {
  id: string; employmentId: string; leaveTypeId: string; year: number;
  entitledDays: string; usedDays: string; pendingDays: string; carriedOverDays: string;
}

export default function LeavePage() {
  const { state, me } = useAuth();
  const { t, locale } = useI18n();
  const capabilities = me?.capabilities ?? [];
  const canView = capabilities.includes("HRM.LEAVE.SELF_VIEW") || capabilities.includes("HRM.LEAVE.SELF_REQUEST");
  const canRequest = capabilities.includes("HRM.LEAVE.SELF_REQUEST");

  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<unknown>(null);
  const [leaveTypes, setLeaveTypes] = useState<LeaveType[]>([]);
  const [requests, setRequests] = useState<LeaveRequest[]>([]);
  const [balances, setBalances] = useState<LeaveBalance[]>([]);
  const [showForm, setShowForm] = useState(false);
  const [formLeaveType, setFormLeaveType] = useState("");
  const [formStartDate, setFormStartDate] = useState("");
  const [formEndDate, setFormEndDate] = useState("");
  const [formReason, setFormReason] = useState("");
  const [busy, setBusy] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const [types, requestsResult, balancesResult] = await Promise.all([
        hrG2Api.listLeaveTypes(), hrG2Api.listLeaveRequests(), hrG2Api.listLeaveBalances(),
      ]);
      setLeaveTypes(types as LeaveType[]);
      setRequests(requestsResult as LeaveRequest[]);
      setBalances(balancesResult as LeaveBalance[]);
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

  async function submitLeaveRequest() {
    setFormError(null);
    if (!formLeaveType) { setFormError(t("hrm.leave.form.error.typeRequired")); return; }
    if (!formStartDate || !formEndDate) { setFormError(t("hrm.leave.form.error.datesRequired")); return; }
    if (formEndDate < formStartDate) { setFormError(t("hrm.leave.form.error.dateOrder")); return; }
    setBusy(true);
    try {
      const created = await hrG2Api.createLeaveRequest({ leaveTypeId: formLeaveType, startDate: formStartDate, endDate: formEndDate, reason: formReason });
      await hrG2Api.submitLeaveRequest(created.requestId);
      setNotice(t("hrm.leave.notice.requested"));
      setShowForm(false); setFormLeaveType(""); setFormStartDate(""); setFormEndDate(""); setFormReason("");
      await load();
    } catch (err) {
      setFormError(hrmErrorMessage(err).message);
    } finally {
      setBusy(false);
    }
  }

  if (["INITIALIZING", "CHECKING_SESSION", "REFRESHING"].includes(state)) return <AuthLoadingState phase="session" />;
  if (!canView) return <HrWorkspace capabilities={capabilities} activeHref="/hr/leave" translate={t}><p role="alert" className={styles.kpiHint}>{t("hrm.recruitment.dashboard.permissionHint")}</p></HrWorkspace>;

  const typeName = (typeId: string) => {
    const type = leaveTypes.find((item) => item.id === typeId);
    if (!type) return "—";
    return locale === "ar" ? type.nameAr : type.nameEn;
  };

  const reqColumns: HrColumn<LeaveRequest>[] = [
    { key: "leaveTypeId", header: t("hrm.leave.col.type"), render: (r) => typeName(r.leaveTypeId) },
    { key: "startDate", header: t("hrm.leave.col.startDate"), render: (r) => formatLocalizedDate(r.startDate, locale) },
    { key: "endDate", header: t("hrm.leave.col.endDate"), render: (r) => formatLocalizedDate(r.endDate, locale) },
    { key: "daysCount", header: t("hrm.leave.col.days"), align: "end" },
    { key: "state", header: t("hrm.leave.col.state"), render: (r) => <HrStateBadge label={t("hrm.leave.state." + r.state)} code={r.state} tone={toneForState(r.state)} /> },
  ];

  const balColumns: HrColumn<LeaveBalance>[] = [
    { key: "leaveTypeId", header: t("hrm.leave.balances.col.type"), render: (b) => typeName(b.leaveTypeId) },
    { key: "entitledDays", header: t("hrm.leave.balances.col.entitled"), align: "end" },
    { key: "usedDays", header: t("hrm.leave.balances.col.used"), align: "end" },
    { key: "pendingDays", header: t("hrm.leave.balances.col.pending"), align: "end" },
    { key: "remaining", header: t("hrm.leave.balances.col.remaining"), align: "end", render: (b) => (Number(b.entitledDays) - Number(b.usedDays) - Number(b.pendingDays)).toFixed(1) },
  ];

  const remainingDays = balances.reduce((sum, balance) => sum + Number(balance.entitledDays) - Number(balance.usedDays) - Number(balance.pendingDays), 0);
  const pendingRequests = requests.filter((request) => request.state.includes("PENDING") || request.state === "SUBMITTED").length;
  const latestState = requests[0]?.state;

  const primaryAction = canRequest ? (
    <button type="button" onClick={() => setShowForm(!showForm)} data-testid="request-leave-toggle">{t("hrm.leave.action.request")}</button>
  ) : undefined;

  return (
    <HrWorkspace capabilities={capabilities} activeHref="/hr/leave" translate={t}>
      <HrG2ProductSurface
        eyebrow={t("hrm.g2.landing.myWorkday")}
        title={t("hrm.leave.title")}
        subtitle={t("hrm.leave.subtitle")}
        metrics={[
          { label: t("hrm.leave.balances.col.remaining"), value: <span data-testid="leave-balance-card">{remainingDays.toFixed(1)}</span> },
          { label: t("hrm.leave.balances.col.pending"), value: <span data-testid="leave-pending-card">{pendingRequests}</span> },
          { label: t("hrm.leave.col.state"), value: <span data-testid="leave-lifecycle-panel">{latestState ? t("hrm.leave.state." + latestState) : "—"}</span> },
        ]}
        primaryAction={primaryAction}
      >
        {notice ? <p role="status" className={styles.kpiHint}>{notice}</p> : null}

        {showForm ? (
          <form className={visualStyles.g2FormGrid} onSubmit={(e) => { e.preventDefault(); void submitLeaveRequest(); }} aria-label={t("hrm.leave.action.request")} data-testid="leave-request-form">
            <label className={styles.kpiLabel} htmlFor="leave-type">{t("hrm.leave.form.type")}<select id="leave-type" className={styles.filterSelect} value={formLeaveType} onChange={(e) => setFormLeaveType(e.target.value)} required data-testid="leave-type"><option value="">—</option>{leaveTypes.map((item) => <option key={item.id} value={item.id}>{locale === "ar" ? item.nameAr : item.nameEn}</option>)}</select></label>
            <label className={styles.kpiLabel} htmlFor="leave-start">{t("hrm.leave.form.startDate")}<input id="leave-start" type="date" className={styles.filterSelect} value={formStartDate} onChange={(e) => setFormStartDate(e.target.value)} required data-testid="leave-start" /></label>
            <label className={styles.kpiLabel} htmlFor="leave-end">{t("hrm.leave.form.endDate")}<input id="leave-end" type="date" className={styles.filterSelect} value={formEndDate} onChange={(e) => setFormEndDate(e.target.value)} required data-testid="leave-end" /></label>
            <label className={styles.kpiLabel} htmlFor="leave-reason">{t("hrm.leave.form.reason")}<textarea id="leave-reason" className={styles.reasonTextarea} value={formReason} onChange={(e) => setFormReason(e.target.value)} placeholder={t("hrm.leave.form.reasonPlaceholder")} data-testid="leave-reason" /></label>
            {formError ? <p role="alert" className={styles.kpiHint} data-kind="error">{formError}</p> : null}
            <div className={styles.actionRow}><button type="submit" className={styles.linkButton} disabled={busy} data-testid="leave-submit">{busy ? t("hrm.leave.form.submitting") : t("hrm.leave.form.submit")}</button><button type="button" className={styles.linkButton} onClick={() => setShowForm(false)} data-testid="leave-cancel">{t("hrm.leave.reasonDialog.cancel")}</button></div>
          </form>
        ) : null}

        {loading ? <HrLoading /> : error ? <HrErrorState error={error} onRetry={load} /> : (
          <div data-testid="leave-ready">
            <section className={visualStyles.productPanel} aria-label={t("hrm.leave.balances.title")}><h2>{t("hrm.leave.balances.title")}</h2><HrDataTable<LeaveBalance> caption={t("hrm.leave.balances.title")} columns={balColumns} rows={balances} rowKey={(b) => b.id} emptyTitle={t("hrm.leave.balances.empty")} /></section>
            <section className={visualStyles.productPanel} aria-label={t("hrm.leave.title")}><h2>{t("hrm.leave.title")}</h2><HrDataTable<LeaveRequest> caption={t("hrm.leave.title")} columns={reqColumns} rows={requests} rowKey={(r) => r.id} emptyTitle={t("hrm.leave.empty")} /></section>
          </div>
        )}
      </HrG2ProductSurface>
    </HrWorkspace>
  );
}
