"use client";

/**
 * Attendance Tracking — G2-T03 SELF surface.
 * Clock in/out + operational summary + recent attendance history.
 */

import { useCallback, useEffect, useState } from "react";
import { AuthLoadingState } from "@/components/auth/auth-loading-state";
import { hrG2Api } from "@/lib/api/hr-g2-api";
import { useAuth } from "@/lib/auth/auth-provider";
import { HRM_CAPABILITIES } from "@/lib/auth/capabilities";
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

function formatTime(iso: string | null, locale: "ar" | "en"): string {
  if (!iso) return "—";
  const d = new Date(iso);
  const timeLocale = locale === "ar" ? "ar-SA" : "en-US";
  return d.toLocaleTimeString(timeLocale, { hour: "2-digit", minute: "2-digit" });
}

function formatHours(minutes: number | null, locale: "ar" | "en"): string {
  if (minutes === null || minutes === 0) return "—";
  const h = Math.floor(minutes / 60);
  const m = minutes % 60;
  return locale === "ar" ? `${h}س ${m}د` : `${h}h ${m}m`;
}

export default function AttendancePage() {
  const { state, me } = useAuth();
  const { t, locale } = useI18n();
  const capabilities = me?.capabilities ?? [];
  const canView = capabilities.includes(HRM_CAPABILITIES.ATTENDANCE_SELF_VIEW);
  const canManage = capabilities.includes(HRM_CAPABILITIES.ATTENDANCE_SELF_RECORD);

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
      setRecords(await hrG2Api.listAttendance() as AttendanceRecord[]);
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
      await hrG2Api.clockIn(today);
      setNotice(t("hrm.attendance.notice.clockedIn"));
      await load();
    } catch (err) {
      setDialogError(hrmErrorMessage(err).message);
    } finally {
      setBusy(false);
    }
  }

  async function clockOut(recordId: string) {
    setBusy(true);
    setDialogError(null);
    try {
      await hrG2Api.clockOut(recordId);
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
      <HrWorkspace capabilities={capabilities} activeHref="/hr/attendance" translate={t}>
        <p role="alert" className={styles.kpiHint}>{t("hrm.recruitment.dashboard.permissionHint")}</p>
      </HrWorkspace>
    );
  }

  const today = new Date().toISOString().slice(0, 10);
  const openRecord = records.find((record) => record.state === "OPEN" && !record.clockOut);
  const todayRecord = records.find((record) => record.recordDate === today) ?? openRecord;
  const currentState = openRecord?.state ?? todayRecord?.state ?? null;
  const monthWorked = records.reduce((total, record) => total + (record.workedMinutes ?? 0), 0);
  const recentRecords = [...records].sort((a, b) => b.recordDate.localeCompare(a.recordDate)).slice(0, 5);

  const columns: HrColumn<AttendanceRecord>[] = [
    { key: "recordDate", header: t("hrm.attendance.col.date"), render: (r) => formatLocalizedDate(r.recordDate, locale) },
    { key: "clockIn", header: t("hrm.attendance.col.clockIn"), render: (r) => formatTime(r.clockIn, locale) },
    { key: "clockOut", header: t("hrm.attendance.col.clockOut"), render: (r) => formatTime(r.clockOut, locale) },
    { key: "workedMinutes", header: t("hrm.attendance.col.worked"), render: (r) => formatHours(r.workedMinutes, locale) },
    { key: "state", header: t("hrm.attendance.col.state"), render: (r) => (
      <HrStateBadge label={t("hrm.attendance.state." + r.state)} code={r.state} tone={toneForState(r.state)} />
    )},
  ];

  return (
    <HrWorkspace capabilities={capabilities} activeHref="/hr/attendance" translate={t}>
      <div data-testid="attendance-product-surface">
        <HrProductHeader
          eyebrow={t("hrm.g2.landing.myWorkday")}
          title={t("hrm.attendance.title")}
          subtitle={t("hrm.attendance.subtitle")}
          trailing={currentState ? <HrStateBadge label={t("hrm.attendance.state." + currentState)} code={currentState} tone={toneForState(currentState)} /> : null}
        />

        {notice ? <p role="status" className={styles.kpiHint}>{notice}</p> : null}
        {dialogError ? <p role="alert" className={styles.kpiHint} data-kind="error">{dialogError}</p> : null}

        {loading ? (
          <HrLoading />
        ) : error ? (
          <HrErrorState error={error} onRetry={load} />
        ) : (
          <div data-testid="attendance-ready">
            <HrKpiGrid label={t("hrm.attendance.title")}>
              <div data-testid="attendance-current-state">
                <HrKpiCard
                  label={t("hrm.attendance.col.state")}
                  value={currentState ? t("hrm.attendance.state." + currentState) : "—"}
                  hint={todayRecord ? formatLocalizedDate(todayRecord.recordDate, locale) : t("hrm.attendance.empty")}
                  tone={openRecord ? "attention" : todayRecord ? "positive" : "neutral"}
                />
              </div>
              <div data-testid="attendance-today-worked">
                <HrKpiCard label={t("hrm.attendance.kpi.todayWorked")} value={formatHours(todayRecord?.workedMinutes ?? null, locale)} />
              </div>
              <HrKpiCard label={t("hrm.attendance.kpi.monthWorked")} value={formatHours(monthWorked, locale)} hint={`${records.length}`} />
            </HrKpiGrid>

            <HrActionBar label={t("hrm.attendance.title")}>
              {canManage ? (
                openRecord ? (
                  <button type="button" data-testid="attendance-clock-out" className={styles.linkButton} onClick={() => void clockOut(openRecord.id)} disabled={busy}>
                    {busy ? t("hrm.attendance.clockingOut") : t("hrm.attendance.clockOut")}
                  </button>
                ) : (
                  <button type="button" data-testid="attendance-clock-in" className={styles.linkButton} onClick={() => void clockIn()} disabled={busy}>
                    {busy ? t("hrm.attendance.clockingIn") : t("hrm.attendance.clockIn")}
                  </button>
                )
              ) : <span className={styles.kpiHint}>{t("hrm.attendance.col.state")}</span>}
            </HrActionBar>

            <div data-testid="attendance-history-panel">
              <HrOperationalPanel label={t("hrm.attendance.title")} title={t("hrm.attendance.title")} description={t("hrm.attendance.subtitle")}>
                <HrDataTable<AttendanceRecord>
                  caption={t("hrm.attendance.title")}
                  columns={columns}
                  rows={records}
                  rowKey={(r) => r.id}
                  emptyTitle={t("hrm.attendance.empty")}
                />
                <HrMobileRecordList label={t("hrm.attendance.title")}>
                  {recentRecords.map((record) => (
                    <article key={record.id} className={styles.statCard}>
                      <strong>{formatLocalizedDate(record.recordDate, locale)}</strong>
                      <span>{formatTime(record.clockIn, locale)} — {formatTime(record.clockOut, locale)}</span>
                      <span>{formatHours(record.workedMinutes, locale)}</span>
                      <HrStateBadge label={t("hrm.attendance.state." + record.state)} code={record.state} tone={toneForState(record.state)} />
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
