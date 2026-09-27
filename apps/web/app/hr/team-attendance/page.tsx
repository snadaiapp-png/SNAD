"use client";

import { useCallback, useEffect, useState } from "react";
import { AuthLoadingState } from "@/components/auth/auth-loading-state";
import { hrG2Api } from "@/lib/api/hr-g2-api";
import { useAuth } from "@/lib/auth/auth-provider";
import { useI18n } from "@/lib/i18n/I18nProvider";
import { HrWorkspace } from "../components/hr-workspace";
import { HrErrorState, HrLoading } from "../components/hr-feedback";
import { HrDataTable, type HrColumn } from "../components/hr-data-table";
import { HrStateBadge, toneForState } from "../components/hr-state-badge";
import { HrKpiCard, HrKpiGrid, HrMobileRecordList, HrOperationalPanel, HrProductHeader } from "../components/hr-product-surface";
import { formatLocalizedDate } from "../hr-labels";
import styles from "../hr.module.css";

interface AttendanceRecord { id: string; employmentId: string; recordDate: string; clockIn: string | null; clockOut: string | null; workedMinutes: number | null; state: string; }

function formatTime(iso: string | null, locale: "ar" | "en"): string { if (!iso) return "—"; return new Date(iso).toLocaleTimeString(locale === "ar" ? "ar-SA" : "en-US", { hour: "2-digit", minute: "2-digit" }); }
function formatWorked(minutes: number | null, locale: "ar" | "en"): string { if (!minutes) return "—"; const hours = Math.floor(minutes / 60); const remainder = minutes % 60; return locale === "ar" ? `${hours}س ${remainder}د` : `${hours}h ${remainder}m`; }

export default function TeamAttendancePage() {
  const { state, me } = useAuth();
  const { t, locale } = useI18n();
  const capabilities = me?.capabilities ?? [];
  const canView = capabilities.includes("HRM.ATTENDANCE.TEAM_VIEW");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<unknown>(null);
  const [records, setRecords] = useState<AttendanceRecord[]>([]);
  const load = useCallback(async () => { setLoading(true); setError(null); try { setRecords(await hrG2Api.listTeamAttendance()); } catch (err) { setError(err); } finally { setLoading(false); } }, []);
  useEffect(() => { if (state !== "AUTHENTICATED") return; const timer = window.setTimeout(() => void load(), 0); return () => window.clearTimeout(timer); }, [state, load]);
  if (["INITIALIZING", "CHECKING_SESSION", "REFRESHING"].includes(state)) return <AuthLoadingState phase="session" />;
  if (!canView) return <HrWorkspace capabilities={capabilities} activeHref="/hr/team-attendance" translate={t}><p role="alert" className={styles.kpiHint}>{t("hrm.recruitment.dashboard.permissionHint")}</p></HrWorkspace>;

  const openCount = records.filter((record) => record.state === "OPEN" || !record.clockOut).length;
  const completedCount = records.filter((record) => Boolean(record.clockOut)).length;
  const totalWorkedMinutes = records.reduce((sum, record) => sum + (record.workedMinutes ?? 0), 0);
  const columns: HrColumn<AttendanceRecord>[] = [
    { key: "recordDate", header: t("hrm.teamAttendance.date"), render: (r) => formatLocalizedDate(r.recordDate, locale) },
    { key: "clockIn", header: t("hrm.teamAttendance.clockIn"), render: (r) => formatTime(r.clockIn, locale) },
    { key: "clockOut", header: t("hrm.teamAttendance.clockOut"), render: (r) => formatTime(r.clockOut, locale) },
    { key: "workedMinutes", header: t("hrm.teamAttendance.worked"), align: "end", render: (r) => formatWorked(r.workedMinutes, locale) },
    { key: "state", header: t("hrm.teamAttendance.state"), render: (r) => <HrStateBadge label={t("hrm.attendance.state." + r.state)} code={r.state} tone={toneForState(r.state)} /> },
  ];

  return <HrWorkspace capabilities={capabilities} activeHref="/hr/team-attendance" translate={t}>
    <div data-testid="team-attendance-product-surface">
      <HrProductHeader eyebrow={t("hrm.g2.landing.myTeam")} title={t("hrm.teamAttendance.title")} subtitle={t("hrm.teamAttendance.subtitle")} />
      {loading ? <HrLoading /> : error ? <HrErrorState error={error} onRetry={load} /> : <div data-testid="team-attendance-ready">
        <HrKpiGrid label={t("hrm.teamAttendance.title")}>
          <div data-testid="team-attendance-open-count"><HrKpiCard label={t("hrm.teamAttendance.state")} value={openCount} tone={openCount > 0 ? "attention" : "neutral"} /></div>
          <div data-testid="team-attendance-completed-count"><HrKpiCard label={t("hrm.teamAttendance.caption")} value={completedCount} /></div>
          <div data-testid="team-attendance-worked-summary"><HrKpiCard label={t("hrm.teamAttendance.worked")} value={formatWorked(totalWorkedMinutes, locale)} /></div>
        </HrKpiGrid>
        <div data-testid="team-attendance-records-panel"><HrOperationalPanel label={t("hrm.teamAttendance.caption")} title={t("hrm.teamAttendance.caption")} description={t("hrm.teamAttendance.subtitle")}>
          <HrDataTable<AttendanceRecord> caption={t("hrm.teamAttendance.caption")} columns={columns} rows={records} rowKey={(r) => r.id} emptyTitle={t("hrm.teamAttendance.empty")} />
          <HrMobileRecordList label={t("hrm.teamAttendance.caption")}>{records.map((record) => <article key={record.id} className={styles.statCard}><strong>{formatLocalizedDate(record.recordDate, locale)}</strong><span>{formatTime(record.clockIn, locale)} — {formatTime(record.clockOut, locale)}</span><span>{formatWorked(record.workedMinutes, locale)}</span><HrStateBadge label={t("hrm.attendance.state." + record.state)} code={record.state} tone={toneForState(record.state)} /></article>)}</HrMobileRecordList>
        </HrOperationalPanel></div>
      </div>}
    </div>
  </HrWorkspace>;
}
