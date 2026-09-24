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

interface AttendanceRecord { id: string; employmentId: string; recordDate: string; clockIn: string | null; clockOut: string | null; workedMinutes: number | null; state: string; }

function formatTime(iso: string | null): string {
  if (!iso) return "—";
  return new Date(iso).toLocaleTimeString("ar", { hour: "2-digit", minute: "2-digit" });
}

export default function AttendanceAdminPage() {
  const { state, me } = useAuth();
  const { t } = useI18n();
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
  if (!canAdmin && !canCorrect) return <HrWorkspace capabilities={capabilities} activeHref="/hr/attendance/admin"><p role="alert" className={styles.kpiHint}>{t("hrm.recruitment.dashboard.permissionHint")}</p></HrWorkspace>;

  const columns: HrColumn<AttendanceRecord>[] = [
    { key: "recordDate", header: "Date", render: (r: AttendanceRecord) => formatArabicDate(r.recordDate) },
    { key: "clockIn", header: "Clock In", render: (r: AttendanceRecord) => formatTime(r.clockIn) },
    { key: "clockOut", header: "Clock Out", render: (r: AttendanceRecord) => formatTime(r.clockOut) },
    { key: "workedMinutes", header: "Worked", align: "end", render: (r: AttendanceRecord) => r.workedMinutes ? `${Math.floor(r.workedMinutes/60)}h ${r.workedMinutes%60}m` : "—" },
    { key: "state", header: "State", render: (r: AttendanceRecord) => <HrStateBadge label={r.state} code={r.state} tone={toneForState(r.state)} /> },
  ];

  return (
    <HrWorkspace capabilities={capabilities} activeHref="/hr/attendance/admin">
      <header>
        <h1>Attendance Administration</h1>
        <p className={styles.kpiHint}>Manual corrections append provenance — never silently overwrite historical truth.</p>
      </header>
      {canCorrect ? (
        <p role="status" className={styles.kpiHint}>
          Attendance correction is unavailable until the canonical correction command is active; no self clock mutation is used as an administrative fallback.
        </p>
      ) : null}
      {loading ? <HrLoading /> : error ? <HrErrorState error={error} onRetry={load} /> : (
        <HrDataTable<AttendanceRecord> caption="Attendance Records (Admin)" columns={columns} rows={records} rowKey={(r) => r.id} emptyTitle="No attendance records" />
      )}
    </HrWorkspace>
  );
}
