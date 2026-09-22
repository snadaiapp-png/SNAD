"use client";

/**
 * Attendance Tracking — G2-T03.
 * Clock in/out + monthly attendance records.
 * Permission-scoped: HRM.ATTENDANCE.VIEW + .MANAGE.
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

interface AttendanceRecord {
  id: string;
  employmentId: string;
  recordDate: string;
  clockIn: string | null;
  clockOut: string | null;
  workedMinutes: number | null;
  source: string;
  state: string;
}

function formatTime(iso: string | null): string {
  if (!iso) return "—";
  const d = new Date(iso);
  return d.toLocaleTimeString("ar", { hour: "2-digit", minute: "2-digit" });
}

function formatHours(minutes: number | null): string {
  if (minutes === null || minutes === 0) return "—";
  const h = Math.floor(minutes / 60);
  const m = minutes % 60;
  return `${h}س ${m}د`;
}

export default function AttendancePage() {
  const { state, me } = useAuth();
  const { t } = useI18n();
  const capabilities = me?.capabilities ?? [];

  const canView = capabilities.includes("HRM.ATTENDANCE.VIEW");
  const canManage = capabilities.includes("HRM.ATTENDANCE.MANAGE");

  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<unknown>(null);
  const [records, setRecords] = useState<AttendanceRecord[]>([]);
  const [busy, setBusy] = useState(false);
  const [dialogError, setDialogError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const res = await fetch("/api/platform/api/v2/hr/time/attendance", { credentials: "same-origin" });
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      const data = await res.json() as AttendanceRecord[];
      setRecords(data);
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

  async function clockIn() {
    setBusy(true);
    setDialogError(null);
    try {
      const today = new Date().toISOString().slice(0, 10);
      const res = await fetch("/api/platform/api/v2/hr/time/attendance/clock-in", {
        method: "POST",
        headers: { "Content-Type": "application/json", "Idempotency-Key": crypto.randomUUID() },
        body: JSON.stringify({ employmentId: me?.id ?? "", recordDate: today }),
        credentials: "same-origin",
      });
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      setNotice(t("hrm.attendance.notice.clockedIn"));
      await load();
    } catch (err) {
      setDialogError(hrmErrorMessage(err).message);
    } finally {
      setBusy(false);
    }
  }

  async function clockOut() {
    setBusy(true);
    setDialogError(null);
    try {
      const openRecord = records.find((r) => r.state === "OPEN");
      if (!openRecord) {
        setDialogError("No open attendance record to clock out");
        return;
      }
      const res = await fetch(`/api/platform/api/v2/hr/time/attendance/${openRecord.id}/clock-out`, {
        method: "POST",
        headers: { "Idempotency-Key": crypto.randomUUID() },
        credentials: "same-origin",
      });
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      setNotice(t("hrm.attendance.notice.clockedOut"));
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
      <HrWorkspace capabilities={capabilities} activeHref="/hr/attendance">
        <p role="alert" className={styles.kpiHint}>{t("hrm.recruitment.dashboard.permissionHint")}</p>
      </HrWorkspace>
    );
  }

  const columns: HrColumn<AttendanceRecord>[] = [
    { key: "recordDate", header: t("hrm.attendance.col.date"), render: (r) => formatArabicDate(r.recordDate) },
    { key: "clockIn", header: t("hrm.attendance.col.clockIn"), render: (r) => formatTime(r.clockIn) },
    { key: "clockOut", header: t("hrm.attendance.col.clockOut"), render: (r) => formatTime(r.clockOut) },
    { key: "workedMinutes", header: t("hrm.attendance.col.worked"), render: (r) => formatHours(r.workedMinutes) },
    { key: "state", header: t("hrm.attendance.col.state"), render: (r) => (
      <HrStateBadge label={t("hrm.attendance.state." + r.state)} code={r.state} tone={toneForState(r.state)} />
    )},
  ];

  return (
    <HrWorkspace capabilities={capabilities} activeHref="/hr/attendance">
      <header>
        <h1>{t("hrm.attendance.title")}</h1>
        <p className={styles.kpiHint}>{t("hrm.attendance.subtitle")}</p>
      </header>

      {notice ? <p role="status" className={styles.kpiHint}>{notice}</p> : null}
      {dialogError ? <p role="alert" className={styles.kpiHint} data-kind="error">{dialogError}</p> : null}

      {canManage ? (
        <div className={styles.actionRow}>
          <button type="button" className={styles.linkButton} onClick={() => void clockIn()} disabled={busy}>
            {busy ? t("hrm.attendance.clockingIn") : t("hrm.attendance.clockIn")}
          </button>
          <button type="button" className={styles.linkButton} onClick={() => void clockOut()} disabled={busy}>
            {t("hrm.attendance.clockOut")}
          </button>
        </div>
      ) : null}

      {loading ? (
        <HrLoading />
      ) : error ? (
        <HrErrorState error={error} onRetry={load} />
      ) : (
        <HrDataTable<AttendanceRecord>
          caption={t("hrm.attendance.title")}
          columns={columns}
          rows={records}
          rowKey={(r) => r.id}
          emptyTitle={t("hrm.attendance.empty")}
        />
      )}
    </HrWorkspace>
  );
}
