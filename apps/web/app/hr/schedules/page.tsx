"use client";

import { useCallback, useEffect, useState } from "react";
import { AuthLoadingState } from "@/components/auth/auth-loading-state";
import { hrG2Api } from "@/lib/api/hr-g2-api";
import { useAuth } from "@/lib/auth/auth-provider";
import { useI18n } from "@/lib/i18n/I18nProvider";
import { HrWorkspace } from "../components/hr-workspace";
import { HrErrorState, HrLoading, hrmErrorMessage } from "../components/hr-feedback";
import { HrDataTable, type HrColumn } from "../components/hr-data-table";
import styles from "../hr.module.css";

interface Schedule { id: string; code: string; nameAr: string; nameEn: string; timezone: string; shiftStart: string; shiftEnd: string; breakMinutes: number; expectedMinutes: number; isOvernight: boolean; state: string; }

export default function SchedulesPage() {
  const { state, me } = useAuth();
  const { t, locale } = useI18n();
  const capabilities = me?.capabilities ?? [];
  const canAdmin = capabilities.includes("HRM.ATTENDANCE.ADMIN");

  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<unknown>(null);
  const [schedules, setSchedules] = useState<Schedule[]>([]);
  const [showForm, setShowForm] = useState(false);
  const [formCode, setFormCode] = useState("");
  const [formNameAr, setFormNameAr] = useState("");
  const [formNameEn, setFormNameEn] = useState("");
  const [formShiftStart, setFormShiftStart] = useState("09:00");
  const [formShiftEnd, setFormShiftEnd] = useState("17:00");
  const [formBreak, setFormBreak] = useState("60");
  const [busy, setBusy] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true); setError(null);
    try {
      setSchedules(await hrG2Api.listSchedules());
    } catch (err) { setError(err); } finally { setLoading(false); }
  }, []);

  useEffect(() => {
    if (state !== "AUTHENTICATED") return;
    const timer = window.setTimeout(() => void load(), 0);
    return () => window.clearTimeout(timer);
  }, [state, load]);

  async function createSchedule() {
    setBusy(true); setFormError(null);
    try {
      await hrG2Api.createSchedule({ code: formCode, nameAr: formNameAr, nameEn: formNameEn, timezone: "Asia/Riyadh", shiftStart: formShiftStart, shiftEnd: formShiftEnd, breakMinutes: Number(formBreak) });
      setShowForm(false); setFormCode(""); setFormNameAr(""); setFormNameEn("");
      await load();
    } catch (err) { setFormError(hrmErrorMessage(err).message); } finally { setBusy(false); }
  }

  if (["INITIALIZING","CHECKING_SESSION","REFRESHING"].includes(state)) return <AuthLoadingState phase="session" />;
  if (!canAdmin) return <HrWorkspace capabilities={capabilities} activeHref="/hr/schedules"><p role="alert" className={styles.kpiHint}>{t("hrm.recruitment.dashboard.permissionHint")}</p></HrWorkspace>;

  const columns: HrColumn<Schedule>[] = [
    { key: "code", header: "Code" },
    { key: "name", header: "Name", render: (r) => locale === "ar" ? r.nameAr : r.nameEn },
    { key: "shiftStart", header: "Shift", render: (r) => `${r.shiftStart}—${r.shiftEnd}${r.isOvernight ? " (overnight)" : ""}` },
    { key: "breakMinutes", header: "Break", align: "end", render: (r) => `${r.breakMinutes}m` },
    { key: "expectedMinutes", header: "Expected", align: "end", render: (r) => `${Math.floor(r.expectedMinutes/60)}h ${r.expectedMinutes%60}m` },
  ];

  return (
    <HrWorkspace capabilities={capabilities} activeHref="/hr/schedules">
      <header><h1>Work Schedules</h1></header>
      {canAdmin ? <button type="button" className={styles.linkButton} onClick={() => setShowForm(!showForm)}>Create Schedule</button> : null}
      {showForm ? (
        <form onSubmit={(e) => { e.preventDefault(); void createSchedule(); }}>
          <p><label className={styles.kpiLabel}>Code: <input className={styles.filterSelect} value={formCode} onChange={(e) => setFormCode(e.target.value)} required /></label></p>
          <p><label className={styles.kpiLabel}>Name (AR): <input className={styles.filterSelect} value={formNameAr} onChange={(e) => setFormNameAr(e.target.value)} required /></label></p>
          <p><label className={styles.kpiLabel}>Name (EN): <input className={styles.filterSelect} value={formNameEn} onChange={(e) => setFormNameEn(e.target.value)} required /></label></p>
          <p><label className={styles.kpiLabel}>Start: <input className={styles.filterSelect} type="time" value={formShiftStart} onChange={(e) => setFormShiftStart(e.target.value)} /></label></p>
          <p><label className={styles.kpiLabel}>End: <input className={styles.filterSelect} type="time" value={formShiftEnd} onChange={(e) => setFormShiftEnd(e.target.value)} /></label></p>
          <p><label className={styles.kpiLabel}>Break (min): <input className={styles.filterSelect} type="number" value={formBreak} onChange={(e) => setFormBreak(e.target.value)} /></label></p>
          {formError ? <p role="alert" className={styles.kpiHint} data-kind="error">{formError}</p> : null}
          <button type="submit" className={styles.linkButton} disabled={busy}>Create</button>
        </form>
      ) : null}
      {loading ? <HrLoading /> : error ? <HrErrorState error={error} onRetry={load} /> : (
        <HrDataTable<Schedule> caption="Work Schedules" columns={columns} rows={schedules} rowKey={(r) => r.id} emptyTitle="No schedules" />
      )}
    </HrWorkspace>
  );
}
