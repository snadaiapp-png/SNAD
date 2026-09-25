"use client";

import { useCallback, useEffect, useState } from "react";
import { AuthLoadingState } from "@/components/auth/auth-loading-state";
import { hrG2Api } from "@/lib/api/hr-g2-api";
import { useAuth } from "@/lib/auth/auth-provider";
import { useI18n } from "@/lib/i18n/I18nProvider";
import { HrWorkspace } from "../../components/hr-workspace";
import { HrErrorState, HrLoading } from "../../components/hr-feedback";
import { HrDataTable, type HrColumn } from "../../components/hr-data-table";
import { HrStateBadge, toneForState } from "../../components/hr-state-badge";
import { formatArabicDate } from "../../hr-labels";
import styles from "../../hr.module.css";
import visualStyles from "../../components/hr-g2-visual.module.css";

interface AttendanceRecord { id: string; employmentId: string; recordDate: string; clockIn: string | null; clockOut: string | null; workedMinutes: number | null; state: string; }

function formatTime(iso: string | null, locale: string): string {
  if (!iso) return "—";
  return new Date(iso).toLocaleTimeString(locale === "ar" ? "ar-SA" : "en-US", { hour: "2-digit", minute: "2-digit" });
}

export default function AttendanceAdminPage() {
  const { state, me } = useAuth();
  const { t, locale } = useI18n();
  const capabilities = me?.capabilities ?? [];
  const canAdmin = capabilities.includes("HRM.ATTENDANCE.ADMIN");
  const canCorrect = capabilities.includes("HRM.ATTENDANCE.CORRECT");

  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<unknown>(null);
  const [records, setRecords] = useState<AttendanceRecord[]>([]);

  const load = useCallback(async () => {
    setLoading(true); setError(null);
    try {
      setRecords(await hrG2Api.listAdminAttendance());
    } catch (err) { setError(err); } finally { setLoading(false); }
  }, []);

  useEffect(() => {
    if (state !== "AUTHENTICATED") return;
    const timer = window.setTimeout(() => void load(), 0);
    return () => window.clearTimeout(timer);
  }, [state, load]);

  if (["INITIALIZING","CHECKING_SESSION","REFRESHING"].includes(state)) return <AuthLoadingState phase="session" />;
  if (!canAdmin && !canCorrect) return <HrWorkspace capabilities={capabilities} activeHref="/hr/attendance/admin" translate={t}><p role="alert" className={styles.kpiHint}>{t("hrm.recruitment.dashboard.permissionHint")}</p></HrWorkspace>;

  const columns: HrColumn<AttendanceRecord>[] = [
    { key: "recordDate", header: t("hrm.attendanceAdmin.date"), render: (r) => formatArabicDate(r.recordDate) },
    { key: "clockIn", header: t("hrm.attendanceAdmin.clockIn"), render: (r) => formatTime(r.clockIn, locale) },
    { key: "clockOut", header: t("hrm.attendanceAdmin.clockOut"), render: (r) => formatTime(r.clockOut, locale) },
    { key: "workedMinutes", header: t("hrm.attendanceAdmin.worked"), align: "end", render: (r) => r.workedMinutes ? `${Math.floor(r.workedMinutes/60)}h ${r.workedMinutes%60}m` : "—" },
    { key: "state", header: t("hrm.attendanceAdmin.state"), render: (r) => <HrStateBadge label={r.state} code={r.state} tone={toneForState(r.state)} /> },
  ];

  return (
    <HrWorkspace capabilities={capabilities} activeHref="/hr/attendance/admin" translate={t}>
      <header className={visualStyles.g2PageHeader}>
        <div>
          <h1>{t("hrm.attendanceAdmin.title")}</h1>
          <p className={styles.kpiHint}>{t("hrm.attendanceAdmin.subtitle")}</p>
        </div>
      </header>
      {canCorrect ? <p role="status" className={styles.kpiHint}>{t("hrm.attendanceAdmin.correctionUnavailable")}</p> : null}
      {loading ? <HrLoading /> : error ? <HrErrorState error={error} onRetry={load} /> : (
        <div data-testid="attendance-admin-ready">
          <HrDataTable<AttendanceRecord> caption={t("hrm.attendanceAdmin.caption")} columns={columns} rows={records} rowKey={(r) => r.id} emptyTitle={t("hrm.attendanceAdmin.empty")} />
        </div>
      )}
    </HrWorkspace>
  );
}
