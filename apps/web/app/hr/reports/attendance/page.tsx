"use client";

import { useCallback, useEffect, useState } from "react";
import { AuthLoadingState } from "@/components/auth/auth-loading-state";
import { hrG2Api } from "@/lib/api/hr-g2-api";
import { useAuth } from "@/lib/auth/auth-provider";
import { useI18n } from "@/lib/i18n/I18nProvider";
import { HrWorkspace } from "../../components/hr-workspace";
import { HrErrorState, HrLoading } from "../../components/hr-feedback";
import { HrDataTable, type HrColumn } from "../../components/hr-data-table";
import styles from "../../hr.module.css";

interface ReportRow {
  employmentId: string; scheduledDays: number | null; scheduledMinutes: number | null;
  workedMinutes: number; absentDays: number; leaveDays: number;
  lateOccurrences: number; earlyDepartures: number; missingPunches: number; attendanceStatus: string;
}

export default function MonthlyReportPage() {
  const { state, me } = useAuth();
  const { t } = useI18n();
  const capabilities = me?.capabilities ?? [];
  const canView = capabilities.includes("HRM.ATTENDANCE.TEAM_VIEW");

  const today = new Date();
  const [year, setYear] = useState(today.getFullYear());
  const [month, setMonth] = useState(today.getMonth() + 1);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<unknown>(null);
  const [rows, setRows] = useState<ReportRow[]>([]);

  const load = useCallback(async () => {
    setLoading(true); setError(null);
    try {
      setRows(await hrG2Api.monthlyAttendanceReport(year, month));
    } catch (err) { setError(err); } finally { setLoading(false); }
  }, [year, month]);

  useEffect(() => {
    if (state !== "AUTHENTICATED") return;
    const timer = window.setTimeout(() => void load(), 0);
    return () => window.clearTimeout(timer);
  }, [state, load]);

  if (["INITIALIZING","CHECKING_SESSION","REFRESHING"].includes(state)) return <AuthLoadingState phase="session" />;
  if (!canView) return <HrWorkspace capabilities={capabilities} activeHref="/hr/reports/attendance"><p role="alert" className={styles.kpiHint}>{t("hrm.recruitment.dashboard.permissionHint")}</p></HrWorkspace>;

  const columns: HrColumn<ReportRow>[] = [
    { key: "employmentId", header: "Employee" },
    { key: "scheduledDays", header: "Sched. Days", align: "end", render: (r) => r.scheduledDays ?? "—" },
    { key: "workedMinutes", header: "Worked Min", align: "end" },
    { key: "absentDays", header: "Absent", align: "end" },
    { key: "leaveDays", header: "Leave", align: "end" },
    { key: "lateOccurrences", header: "Late", align: "end" },
    { key: "earlyDepartures", header: "Early Dep.", align: "end" },
    { key: "missingPunches", header: "Missing Punch", align: "end" },
    { key: "attendanceStatus", header: "Status" },
  ];

  return (
    <HrWorkspace capabilities={capabilities} activeHref="/hr/reports/attendance">
      <header><h1>Monthly Attendance Report</h1></header>
      <div className={styles.filterBar}>
        <label htmlFor="year" className={styles.kpiLabel}>Year:</label>
        <input id="year" type="number" className={styles.filterSelect} value={year} onChange={(e) => setYear(Number(e.target.value))} />
        <label htmlFor="month" className={styles.kpiLabel}>Month:</label>
        <select id="month" className={styles.filterSelect} value={month} onChange={(e) => setMonth(Number(e.target.value))}>
          {Array.from({length: 12}, (_, i) => <option key={i+1} value={i+1}>{i+1}</option>)}
        </select>
      </div>
      {loading ? <HrLoading /> : error ? <HrErrorState error={error} onRetry={load} /> : (
        <HrDataTable<ReportRow> caption={`Monthly Report — ${year}/${month}`} columns={columns} rows={rows} rowKey={(r) => r.employmentId} emptyTitle="No data for this period" />
      )}
    </HrWorkspace>
  );
}
