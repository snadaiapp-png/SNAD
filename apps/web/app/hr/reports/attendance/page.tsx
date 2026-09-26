"use client";

import { useCallback, useEffect, useState } from "react";
import { AuthLoadingState } from "@/components/auth/auth-loading-state";
import { hrG2Api } from "@/lib/api/hr-g2-api";
import { useAuth } from "@/lib/auth/auth-provider";
import { useI18n } from "@/lib/i18n/I18nProvider";
import { HrWorkspace } from "../../components/hr-workspace";
import { HrErrorState, HrLoading } from "../../components/hr-feedback";
import { HrDataTable, type HrColumn } from "../../components/hr-data-table";
import { HrActionBar, HrKpiCard, HrKpiGrid, HrMobileRecordList, HrOperationalPanel, HrProductHeader } from "../../components/hr-product-surface";
import styles from "../../hr.module.css";

interface ReportRow { employmentId: string; scheduledDays: number | null; scheduledMinutes: number | null; workedMinutes: number; absentDays: number; leaveDays: number; lateOccurrences: number; earlyDepartures: number; missingPunches: number; attendanceStatus: string; }

export default function MonthlyReportPage() {
  const { state, me } = useAuth();
  const { t } = useI18n();
  const capabilities = me?.capabilities ?? [];
  const canAdmin = capabilities.includes("HRM.ATTENDANCE.ADMIN");
  const canTeamView = capabilities.includes("HRM.ATTENDANCE.TEAM_VIEW");
  const canView = canAdmin || canTeamView;
  const today = new Date();
  const [year, setYear] = useState(today.getFullYear());
  const [month, setMonth] = useState(today.getMonth() + 1);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<unknown>(null);
  const [rows, setRows] = useState<ReportRow[]>([]);
  const load = useCallback(async () => { setLoading(true); setError(null); try { setRows(canAdmin ? await hrG2Api.adminMonthlyAttendanceReport(year, month) : await hrG2Api.teamMonthlyAttendanceReport(year, month)); } catch (err) { setError(err); } finally { setLoading(false); } }, [year, month, canAdmin]);
  useEffect(() => { if (state !== "AUTHENTICATED") return; const timer = window.setTimeout(() => void load(), 0); return () => window.clearTimeout(timer); }, [state, load]);
  if (["INITIALIZING", "CHECKING_SESSION", "REFRESHING"].includes(state)) return <AuthLoadingState phase="session" />;
  if (!canView) return <HrWorkspace capabilities={capabilities} activeHref="/hr/reports/attendance" translate={t}><p role="alert" className={styles.kpiHint}>{t("hrm.recruitment.dashboard.permissionHint")}</p></HrWorkspace>;

  const peopleCount = rows.length;
  const absentCount = rows.reduce((sum, row) => sum + row.absentDays, 0);
  const lateCount = rows.reduce((sum, row) => sum + row.lateOccurrences, 0);
  const workedMinutes = rows.reduce((sum, row) => sum + row.workedMinutes, 0);
  const columns: HrColumn<ReportRow>[] = [
    { key: "employmentId", header: t("hrm.attendanceReport.employee") },
    { key: "scheduledDays", header: t("hrm.attendanceReport.scheduledDays"), align: "end", render: (r) => r.scheduledDays ?? "—" },
    { key: "workedMinutes", header: t("hrm.attendanceReport.workedMinutes"), align: "end" },
    { key: "absentDays", header: t("hrm.attendanceReport.absent"), align: "end" },
    { key: "leaveDays", header: t("hrm.attendanceReport.leave"), align: "end" },
    { key: "lateOccurrences", header: t("hrm.attendanceReport.late"), align: "end" },
    { key: "earlyDepartures", header: t("hrm.attendanceReport.earlyDeparture"), align: "end" },
    { key: "missingPunches", header: t("hrm.attendanceReport.missingPunch"), align: "end" },
    { key: "attendanceStatus", header: t("hrm.attendanceReport.status") },
  ];

  return <HrWorkspace capabilities={capabilities} activeHref="/hr/reports/attendance" translate={t}>
    <div data-testid="attendance-report-product-surface">
      <HrProductHeader eyebrow={canAdmin ? t("hrm.g2.landing.hrOperations") : t("hrm.g2.landing.myTeam")} title={t("hrm.attendanceReport.title")} subtitle={t("hrm.attendanceReport.subtitle")} />
      <HrKpiGrid label={t("hrm.attendanceReport.title")}>
        <div data-testid="attendance-report-people-count"><HrKpiCard label={t("hrm.attendanceReport.employee")} value={peopleCount} /></div>
        <div data-testid="attendance-report-absent-count"><HrKpiCard label={t("hrm.attendanceReport.absent")} value={absentCount} tone={absentCount > 0 ? "attention" : "neutral"} /></div>
        <div data-testid="attendance-report-late-count"><HrKpiCard label={t("hrm.attendanceReport.late")} value={lateCount} tone={lateCount > 0 ? "attention" : "neutral"} /></div>
        <HrKpiCard label={t("hrm.attendanceReport.workedMinutes")} value={workedMinutes} />
      </HrKpiGrid>
      <div data-testid="attendance-report-filter-bar"><HrActionBar label={t("hrm.attendanceReport.title")}><label htmlFor="year" className={styles.kpiLabel}>{t("hrm.attendanceReport.year")}</label><input id="year" type="number" className={styles.filterSelect} value={year} onChange={(e) => setYear(Number(e.target.value))} /><label htmlFor="month" className={styles.kpiLabel}>{t("hrm.attendanceReport.month")}</label><select id="month" className={styles.filterSelect} value={month} onChange={(e) => setMonth(Number(e.target.value))}>{Array.from({ length: 12 }, (_, i) => <option key={i + 1} value={i + 1}>{i + 1}</option>)}</select></HrActionBar></div>
      {loading ? <HrLoading /> : error ? <HrErrorState error={error} onRetry={load} /> : <div data-testid="attendance-report-results-panel"><HrOperationalPanel label={t("hrm.attendanceReport.title")} title={`${t("hrm.attendanceReport.title")} — ${year}/${month}`} description={t("hrm.attendanceReport.subtitle")}>
        <HrDataTable<ReportRow> caption={`${t("hrm.attendanceReport.title")} — ${year}/${month}`} columns={columns} rows={rows} rowKey={(r) => r.employmentId} emptyTitle={t("hrm.attendanceReport.empty")} />
        <HrMobileRecordList label={t("hrm.attendanceReport.title")}>{rows.map((row) => <article key={row.employmentId} className={styles.statCard}><strong>{row.employmentId}</strong><span>{t("hrm.attendanceReport.workedMinutes")}: {row.workedMinutes}</span><span>{t("hrm.attendanceReport.absent")}: {row.absentDays}</span><span>{t("hrm.attendanceReport.late")}: {row.lateOccurrences}</span></article>)}</HrMobileRecordList>
      </HrOperationalPanel></div>}
    </div>
  </HrWorkspace>;
}
