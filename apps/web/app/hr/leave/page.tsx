"use client";

/**
 * Leave Management — G2-T04 SELF surface.
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
      <header>
        <h1 data-testid="g2-page-title">{t("hrm.leave.title")}</h1>
        <p className={styles.kpiHint}>{t("hrm.leave.subtitle")}</p>
      </header>

      {notice ? <p role="status" className={styles.kpiHint}>{notice}</p> : null}

      {canRequest ? (
        <button type="button" className={styles.linkButton} onClick={() => setShowForm(!showForm)} data-testid="request-leave-toggle">
          {t("hrm.leave.action.request")}
        </button>
      ) : null}

      {showForm ? (
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
      ) : null}

      {loading ? (
        <HrLoading />
      ) : error ? (
        <HrErrorState error={error} onRetry={load} />
      ) : (
        <div data-testid="leave-ready">
          <section aria-label={t("hrm.leave.balances.title")}>
            <h2>{t("hrm.leave.balances.title")}</h2>
            <HrDataTable<LeaveBalance>
              caption={t("hrm.leave.balances.title")}
              columns={balColumns}
              rows={balances}
              rowKey={(b) => b.id}
              emptyTitle={t("hrm.leave.balances.empty")}
            />
          </section>

          <section aria-label={t("hrm.leave.title")}>
            <h2>{t("hrm.leave.title")}</h2>
            <HrDataTable<LeaveRequest>
              caption={t("hrm.leave.title")}
              columns={reqColumns}
              rows={requests}
              rowKey={(r) => r.id}
              emptyTitle={t("hrm.leave.empty")}
            />
          </section>
        </div>
      )}
    </HrWorkspace>
  );
}
