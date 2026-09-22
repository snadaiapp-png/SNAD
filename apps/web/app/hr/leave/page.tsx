"use client";

/**
 * Leave Management — G2-T04.
 * Request leave + view balance + manager approval.
 * Permission-scoped: HRM.LEAVE.VIEW + .REQUEST + .APPROVE.
 * Arabic/RTL: logical CSS only; i18n keys; SDS tokens.
 */

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

  const canView = capabilities.includes("HRM.LEAVE.VIEW");
  const canRequest = capabilities.includes("HRM.LEAVE.REQUEST");
  const canApprove = capabilities.includes("HRM.LEAVE.APPROVE");

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

  const [dialog, setDialog] = useState<"approve" | "reject" | null>(null);
  const [dialogTarget, setDialogTarget] = useState<LeaveRequest | null>(null);
  const [dialogComment, setDialogComment] = useState("");
  const [dialogError, setDialogError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const [typesRes, reqRes, balRes] = await Promise.all([
        fetch("/api/platform/api/v2/hr/leave/types", { credentials: "same-origin" }),
        fetch("/api/platform/api/v2/hr/leave/requests", { credentials: "same-origin" }),
        fetch("/api/platform/api/v2/hr/leave/balances", { credentials: "same-origin" }),
      ]);
      if (!typesRes.ok || !reqRes.ok || !balRes.ok) throw new Error("HTTP error");
      setLeaveTypes(await typesRes.json());
      setRequests(await reqRes.json());
      setBalances(await balRes.json());
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
      const res = await fetch("/api/platform/api/v2/hr/leave/requests", {
        method: "POST",
        headers: { "Content-Type": "application/json", "Idempotency-Key": crypto.randomUUID() },
        body: JSON.stringify({
          employmentId: me?.id ?? "",
          leaveTypeId: formLeaveType,
          startDate: formStartDate,
          endDate: formEndDate,
          reason: formReason,
        }),
        credentials: "same-origin",
      });
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      setNotice(t("hrm.leave.notice.requested"));
      setShowForm(false);
      setFormLeaveType(""); setFormStartDate(""); setFormEndDate(""); setFormReason("");
      await load();
    } catch (err) {
      setFormError(hrmErrorMessage(err).message);
    } finally {
      setBusy(false);
    }
  }

  function openDialog(kind: "approve" | "reject", req: LeaveRequest) {
    setDialog(kind);
    setDialogTarget(req);
    setDialogComment("");
    setDialogError(null);
  }

  function closeDialog() {
    setDialog(null); setDialogTarget(null); setDialogComment(""); setDialogError(null);
  }

  async function submitDialog() {
    if (!dialogTarget || !dialog) return;
    if (dialog === "reject" && !dialogComment.trim()) {
      setDialogError(t("hrm.leave.reasonDialog.comment"));
      return;
    }
    setBusy(true);
    setDialogError(null);
    try {
      const endpoint = dialog === "approve" ? "approve" : "reject";
      const body = dialog === "approve"
        ? { comment: dialogComment }
        : { reason: dialogComment };
      const res = await fetch(`/api/platform/api/v2/hr/leave/requests/${dialogTarget.id}/${endpoint}`, {
        method: "POST",
        headers: { "Content-Type": "application/json", "Idempotency-Key": crypto.randomUUID() },
        body: JSON.stringify(body),
        credentials: "same-origin",
      });
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      setNotice(t("hrm.leave.notice." + dialog));
      closeDialog();
      await load();
    } catch (err) {
      setDialogError(hrmErrorMessage(err).message);
    } finally {
      setBusy(false);
    }
  }

  if (["INITIALIZING", "CHECKING_SESSION", "REFRESHING"].includes(state)) {
    return <AuthLoadingState phase="session" />;
  }

  if (!canView) {
    return (
      <HrWorkspace capabilities={capabilities} activeHref="/hr/leave">
        <p role="alert" className={styles.kpiHint}>{t("hrm.recruitment.dashboard.permissionHint")}</p>
      </HrWorkspace>
    );
  }

  const typeName = (typeId: string) => {
    const type = leaveTypes.find((t) => t.id === typeId);
    if (!type) return "—";
    return locale === "ar" ? type.nameAr : type.nameEn;
  };

  const reqColumns: HrColumn<LeaveRequest>[] = [
    { key: "leaveTypeId", header: t("hrm.leave.col.type"), render: (r) => typeName(r.leaveTypeId) },
    { key: "startDate", header: t("hrm.leave.col.startDate"), render: (r) => formatArabicDate(r.startDate) },
    { key: "endDate", header: t("hrm.leave.col.endDate"), render: (r) => formatArabicDate(r.endDate) },
    { key: "daysCount", header: t("hrm.leave.col.days"), align: "end" },
    { key: "state", header: t("hrm.leave.col.state"), render: (r) => (
      <HrStateBadge label={t("hrm.leave.state." + r.state)} code={r.state} tone={toneForState(r.state)} />
    )},
    ...(canApprove ? [{
      key: "actions",
      header: t("hrm.leave.col.actions"),
      render: (r: LeaveRequest) => (
        <span className={styles.actionRow}>
          {r.state === "PENDING" ? (
            <>
              <button type="button" className={styles.linkButton} onClick={() => openDialog("approve", r)} disabled={busy}>
                {t("hrm.leave.action.approve")}
              </button>
              <button type="button" className={styles.linkButton} onClick={() => openDialog("reject", r)} disabled={busy}>
                {t("hrm.leave.action.reject")}
              </button>
            </>
          ) : null}
        </span>
      ),
    }] : []),
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
    <HrWorkspace capabilities={capabilities} activeHref="/hr/leave">
      <header>
        <h1>{t("hrm.leave.title")}</h1>
        <p className={styles.kpiHint}>{t("hrm.leave.subtitle")}</p>
      </header>

      {notice ? <p role="status" className={styles.kpiHint}>{notice}</p> : null}

      {canRequest ? (
        <button type="button" className={styles.linkButton} onClick={() => setShowForm(!showForm)}>
          {t("hrm.leave.action.request")}
        </button>
      ) : null}

      {showForm ? (
        <form onSubmit={(e) => { e.preventDefault(); void submitLeaveRequest(); }} aria-label={t("hrm.leave.action.request")}>
          <p>
            <label htmlFor="leave-type" className={styles.kpiLabel}>{t("hrm.leave.form.type")}</label>
            <select id="leave-type" className={styles.filterSelect} value={formLeaveType}
              onChange={(e) => setFormLeaveType(e.target.value)} required aria-required="true">
              <option value="">—</option>
              {leaveTypes.map((t) => <option key={t.id} value={t.id}>{locale === "ar" ? t.nameAr : t.nameEn}</option>)}
            </select>
          </p>
          <p>
            <label htmlFor="leave-start" className={styles.kpiLabel}>{t("hrm.leave.form.startDate")}</label>
            <input id="leave-start" type="date" className={styles.filterSelect} value={formStartDate}
              onChange={(e) => setFormStartDate(e.target.value)} required aria-required="true" />
          </p>
          <p>
            <label htmlFor="leave-end" className={styles.kpiLabel}>{t("hrm.leave.form.endDate")}</label>
            <input id="leave-end" type="date" className={styles.filterSelect} value={formEndDate}
              onChange={(e) => setFormEndDate(e.target.value)} required aria-required="true" />
          </p>
          <p>
            <label htmlFor="leave-reason" className={styles.kpiLabel}>{t("hrm.leave.form.reason")}</label>
            <textarea id="leave-reason" className={styles.reasonTextarea} value={formReason}
              onChange={(e) => setFormReason(e.target.value)} placeholder={t("hrm.leave.form.reasonPlaceholder")} />
          </p>
          {formError ? <p role="alert" className={styles.kpiHint} data-kind="error">{formError}</p> : null}
          <div className={styles.actionRow}>
            <button type="submit" className={styles.linkButton} disabled={busy}>
              {busy ? t("hrm.leave.form.submitting") : t("hrm.leave.form.submit")}
            </button>
            <button type="button" className={styles.linkButton} onClick={() => setShowForm(false)}>
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
        <>
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
        </>
      )}

      {dialog && dialogTarget ? (
        <div role="dialog" aria-modal="true" aria-label={t("hrm.leave.reasonDialog.title." + dialog)} className={styles.feedbackError}>
          <h3>{t("hrm.leave.reasonDialog.title." + dialog)}</h3>
          <label htmlFor="dialog-comment" className={styles.kpiLabel}>{t("hrm.leave.reasonDialog.comment")}</label>
          <textarea id="dialog-comment" className={styles.reasonTextarea} value={dialogComment}
            onChange={(e) => setDialogComment(e.target.value)}
            required={dialog === "reject"} aria-required={dialog === "reject" ? "true" : "false"}
            placeholder={t("hrm.leave.reasonDialog.commentPlaceholder")} />
          {dialogError ? <p role="alert">{dialogError}</p> : null}
          <div className={styles.actionRow}>
            <button type="button" className={styles.linkButton} onClick={closeDialog} disabled={busy}>
              {t("hrm.leave.reasonDialog.cancel")}
            </button>
            <button type="button" className={styles.linkButton}
              onClick={() => void submitDialog()} disabled={busy || (dialog === "reject" && !dialogComment.trim())}>
              {t("hrm.leave.reasonDialog.confirm." + dialog)}
            </button>
          </div>
        </div>
      ) : null}
    </HrWorkspace>
  );
}
