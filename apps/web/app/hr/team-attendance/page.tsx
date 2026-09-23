"use client";

import { useCallback, useEffect, useState } from "react";
import { AuthLoadingState } from "@/components/auth/auth-loading-state";
import { useAuth } from "@/lib/auth/auth-provider";
import { useI18n } from "@/lib/i18n/I18nProvider";
import { HrWorkspace } from "../components/hr-workspace";
import { HrErrorState, HrLoading, HrEmptyState } from "../components/hr-feedback";
import { HrDataTable, type HrColumn } from "../components/hr-data-table";
import { formatArabicDate } from "../hr-labels";
import styles from "../hr.module.css";

interface AttendanceRecord { id: string; employmentId: string; recordDate: string; clockIn: string | null; clockOut: string | null; workedMinutes: number | null; state: string; }

function formatTime(iso: string | null): string {
  if (!iso) return "—";
  return new Date(iso).toLocaleTimeString("ar", { hour: "2-digit", minute: "2-digit" });
}

export default function TeamAttendancePage() {
  const { state, me } = useAuth();
  const { t } = useI18n();
  const capabilities = me?.capabilities ?? [];
  const canView = capabilities.includes("HRM.ATTENDANCE.TEAM_VIEW");

  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<unknown>(null);
  const [records, setRecords] = useState<AttendanceRecord[]>([]);

  const load = useCallback(async () => {
    setLoading(true); setError(null);
    try {
      const res = await fetch("/api/platform/api/v2/hr/time/attendance", { credentials: "same-origin" });
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      setRecords(await res.json());
    } catch (err) { setError(err); } finally { setLoading(false); }
  }, []);

  useEffect(() => {
    if (state !== "AUTHENTICATED") return;
    const timer = window.setTimeout(() => void load(), 0);
    return () => window.clearTimeout(timer);
  }, [state, load]);

  if (["INITIALIZING","CHECKING_SESSION","REFRESHING"].includes(state)) return <AuthLoadingState phase="session" />;
  if (!canView) return <HrWorkspace capabilities={capabilities} activeHref="/hr/team-attendance"><p role="alert" className={styles.kpiHint}>{t("hrm.recruitment.dashboard.permissionHint")}</p></HrWorkspace>;

  const columns: HrColumn<AttendanceRecord>[] = [
    { key: "recordDate", header: "Date", render: (r) => formatArabicDate(r.recordDate) },
    { key: "clockIn", header: "Clock In", render: (r) => formatTime(r.clockIn) },
    { key: "clockOut", header: "Clock Out", render: (r) => formatTime(r.clockOut) },
    { key: "workedMinutes", header: "Worked", align: "end", render: (r) => r.workedMinutes ? `${Math.floor(r.workedMinutes/60)}h ${r.workedMinutes%60}m` : "—" },
    { key: "state", header: "State" },
  ];

  return (
    <HrWorkspace capabilities={capabilities} activeHref="/hr/team-attendance">
      <header><h1>Team Attendance</h1></header>
      {loading ? <HrLoading /> : error ? <HrErrorState error={error} onRetry={load} /> : (
        <HrDataTable<AttendanceRecord> caption="Team Attendance Records" columns={columns} rows={records} rowKey={(r) => r.id} emptyTitle="No attendance records" />
      )}
    </HrWorkspace>
  );
}
