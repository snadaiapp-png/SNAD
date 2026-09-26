"use client";

/**
 * Leave Management — G2-T04 SELF product surface.
 * Employees request leave and view only their own requests/balances.
 * Manager/HR decisions live on the scoped /hr/leave/approvals surface.
 */

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
        hrG2Api.listLeaveTypes(),
        hrG2Api.listLeaveRequests(),
        hrG2Api.listLeaveBalances(),
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
      const created = await hrG2Api.createLeaveRequest({
        leaveTypeId: formLeaveType,
        startDate: formStartDate,
        endDate: formEndDate,
        reason: formReason,
      });
      await hrG2Api.submitLeaveRequest(created.requestId);
      setNotice(t("hrm.leave.notice.requested"));
      setShowForm(false);
      setFormLeaveType("");
      setFormStartDate("");
      setFormEndDate("");
      setFormReason("");
      await load();
    } catch (err) {
      setFormError(hrmErrorMessage(err).message);
    } finally {
      setBusy(false);
    }
  }

  if (["INITIALIZING", "CHECKING_SESSION", "REFRESHING"].includes(state)) {
    return <AuthLoadingState phase="session" />;
  }

  if (!canView) {
    return (
      <HrWorkspace capabilities={capabilities} activeHref="/hr/leave" translate={t}>
        <p role="alert" className={styles.kpiHint}>{t("hrm.recruitment.dashboard.permissionHint")}</p>
      </HrWorkspace>
    );
  }

  const typeName = (typeId: string) => {
    const type = leaveTypes.find((item) => item.id === typeId);
    if (!type) return "—";
    return locale === "ar" ? type.nameAr : type.nameEn;
  };

  const pendingStates = new Set(["DRAFT", "SUBMITTED", "PENDING", "PENDING_MANAGER", "PENDING_HR"]);
  const remainingDays = balances.reduce((total, balance) => total + Number(balance.entitledDays) - Number(balance.usedDays) - Number(balance.pendingDays), 0);
  const pendingRequests = requests.filter((request) => pendingStates.has(request.state));
  const approvedRequests = requests.filter((request) => request.state === "APPROVED" || request.state === "COMPLETED");
  const orderedRequests = [...requests].sort((a, b) => b.startDate.localeCompare(a.startDate));

  const reqColumns: HrColumn<LeaveRequest>[] = [
    { key: "leaveTypeId", header: t("hrm.leave.col.type"), render: (r) => typeName(r.leaveTypeId) },
    { key: "startDate", header: t("hrm.leave.col.startDate"), render: (r) => formatLocalizedDate(r.startDate, locale) },
    { key: "endDate", header: t("hrm.leave.col.endDate"), render: (r) => formatLocalizedDate(r.endDate, locale) },
    { key: "daysCount", header: t("hrm.leave.col.days"), align: "end" },
    { key: "state", header: t("hrm.leave.col.state"), render: (r) => (
      <HrStateBadge label={t("hrm.leave.state." + r.state)} code={r.state} tone={toneForState(r.state)} />
    )},
  ];

  const balColumns: HrColumn<LeaveBalance>[] = [
    { key: "leaveTypeId", header: t("hrm.leave.balances.col.type"), render: (b) => typeName(b.leaveTypeId) },
    { key: "entitledDays", header: t("hrm.leave.balances.col.entitled"), align: "end" },
    { key: "usedDays", header: t("hrm.leave.balances.col.used"), align: "end" },
    { key: "pendingDays", header: t("hrm.leave.balances.col.pending"), align: "end" },
    { key: "remaining", header: t("hrm.leave.balances.col.remaining"), align: "end",
      render: (b) => (Number(b.entitledDays) - Number(b.usedDays) - Number(b.pendingDays)).toFixed(1) },
  ];

  return (
    <HrWorkspace capabilities={capabilities} activeHref="/hr/leave" translate={t}>
      <div data-testid="leave-product-surface">
        <HrProductHeader eyebrow={t("hrm.g2.landing.myWorkday")} title={t("hrm.leave.title")} subtitle={t("hrm.leave.subtitle")} />

        {notice ? <p role="status" className={styles.kpiHint}>{notice}</p> : null}

        {loading ? (
          <HrLoading />
        ) : error ? (
          <HrErrorState error={error} onRetry={load} />
        ) : (
          <div data-testid="leave-ready">
            <HrKpiGrid label={t("hrm.leave.title")}>
              <div data-testid="leave-balance-summary">
                <HrKpiCard label={t("hrm.leave.balances.col.remaining")} value={remainingDays.toFixed(1)} hint={t("hrm.leave.balances.title")} tone="positive" />
              </div>
              <div data-testid="leave-pending-summary">
                <HrKpiCard label={t("hrm.leave.balances.col.pending")} value={pendingRequests.length} hint={t("hrm.leave.col.state")} tone={pendingRequests.length > 0 ? "attention" : "neutral"} />
              </div>
              <div data-testid="leave-approved-summary">
                <HrKpiCard label={t("hrm.leave.state.APPROVED")} value={approvedRequests.length} hint={t("hrm.leave.title")} tone="positive" />
              </div>
            </HrKpiGrid>

            <HrActionBar label={t("hrm.leave.action.request")}>
              {canRequest ? (
                <button type="button" className={styles.linkButton} onClick={() => setShowForm(!showForm)} data-testid="request-leave-toggle">
                  {t("hrm.leave.action.request")}
                </button>
              ) : <span className={styles.kpiHint}>{t("hrm.leave.title")}</span>}
            </HrActionBar>

            {showForm ? (
              <HrOperationalPanel label={t("hrm.leave.action.request")} title={t("hrm.leave.action.request")} description={t("hrm.leave.subtitle")}>
                <form onSubmit={(e) => { e.preventDefault(); void submitLeaveRequest(); }} aria-label={t("hrm.leave.action.request")} data-testid="leave-request-form">
                  <p>
                    <label htmlFor="leave-type" className={styles.kpiLabel}>{t("hrm.leave.form.type")}</label>
                    <select id="leave-type" className={styles.filterSelect} value={formLeaveType}
                      onChange={(e) => setFormLeaveType(e.target.value)} required aria-required="true" data-testid="leave-type">
                      <option value="">—</option>
                      {leaveTypes.map((item) => <option key={item.id} value={item.id}>{locale === "ar" ? item.nameAr : item.nameEn}</option>)}
                    </select>
                  </p>
                  <p>
                    <label htmlFor="leave-start" className={styles.kpiLabel}>{t("hrm.leave.form.startDate")}</label>
                    <input id="leave-start" type="date" className={styles.filterSelect} value={formStartDate}
                      onChange={(e) => setFormStartDate(e.target.value)} required aria-required="true" data-testid="leave-start" />
                  </p>
                  <p>
                    <label htmlFor="leave-end" className={styles.kpiLabel}>{t("hrm.leave.form.endDate")}</label>
                    <input id="leave-end" type="date" className={styles.filterSelect} value={formEndDate}
                      onChange={(e) => setFormEndDate(e.target.value)} required aria-required="true" data-testid="leave-end" />
                  </p>
                  <p>
                    <label htmlFor="leave-reason" className={styles.kpiLabel}>{t("hrm.leave.form.reason")}</label>
                    <textarea id="leave-reason" className={styles.reasonTextarea} value={formReason}
                      onChange={(e) => setFormReason(e.target.value)} placeholder={t("hrm.leave.form.reasonPlaceholder")} data-testid="leave-reason" />
                  </p>
                  {formError ? <p role="alert" className={styles.kpiHint} data-kind="error">{formError}</p> : null}
                  <div className={styles.actionRow}>
                    <button type="submit" className={styles.linkButton} disabled={busy} data-testid="leave-submit">
                      {busy ? t("hrm.leave.form.submitting") : t("hrm.leave.form.submit")}
                    </button>
                    <button type="button" className={styles.linkButton} onClick={() => setShowForm(false)} data-testid="leave-cancel">
                      {t("hrm.leave.reasonDialog.cancel")}
                    </button>
                  </div>
                </form>
              </HrOperationalPanel>
            ) : null}

            <HrOperationalPanel label={t("hrm.leave.balances.title")} title={t("hrm.leave.balances.title")}>
              <HrDataTable<LeaveBalance>
                caption={t("hrm.leave.balances.title")}
                columns={balColumns}
                rows={balances}
                rowKey={(b) => b.id}
                emptyTitle={t("hrm.leave.balances.empty")}
              />
            </HrOperationalPanel>

            <div data-testid="leave-requests-panel">
              <HrOperationalPanel label={t("hrm.leave.title")} title={t("hrm.leave.title")} description={t("hrm.leave.subtitle")}>
                <HrDataTable<LeaveRequest>
                  caption={t("hrm.leave.title")}
                  columns={reqColumns}
                  rows={orderedRequests}
                  rowKey={(r) => r.id}
                  emptyTitle={t("hrm.leave.empty")}
                />
                <HrMobileRecordList label={t("hrm.leave.title")}>
                  {orderedRequests.slice(0, 5).map((request) => (
                    <article key={request.id} className={styles.statCard}>
                      <strong>{typeName(request.leaveTypeId)}</strong>
                      <span>{formatLocalizedDate(request.startDate, locale)} — {formatLocalizedDate(request.endDate, locale)}</span>
                      <span>{request.daysCount} · {t("hrm.leave.col.days")}</span>
                      <HrStateBadge label={t("hrm.leave.state." + request.state)} code={request.state} tone={toneForState(request.state)} />
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
