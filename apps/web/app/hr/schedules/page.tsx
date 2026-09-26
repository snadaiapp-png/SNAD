"use client";

import { useCallback, useEffect, useState } from "react";
import { AuthLoadingState } from "@/components/auth/auth-loading-state";
import { hrG2Api } from "@/lib/api/hr-g2-api";
import { useAuth } from "@/lib/auth/auth-provider";
import { useI18n } from "@/lib/i18n/I18nProvider";
import { HrWorkspace } from "../components/hr-workspace";
import { HrG2ProductSurface } from "../components/hr-g2-product-surface";
import { HrErrorState, HrLoading, hrmErrorMessage } from "../components/hr-feedback";
import { HrDataTable, type HrColumn } from "../components/hr-data-table";
import styles from "../hr.module.css";
import visualStyles from "../components/hr-g2-visual.module.css";

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
  const [formCode, setFormCode] = useState(""); const [formNameAr, setFormNameAr] = useState(""); const [formNameEn, setFormNameEn] = useState("");
  const [formShiftStart, setFormShiftStart] = useState("09:00"); const [formShiftEnd, setFormShiftEnd] = useState("17:00"); const [formBreak, setFormBreak] = useState("60");
  const [busy, setBusy] = useState(false); const [formError, setFormError] = useState<string | null>(null);

  const load = useCallback(async () => { setLoading(true); setError(null); try { setSchedules(await hrG2Api.listSchedules()); } catch (err) { setError(err); } finally { setLoading(false); } }, []);
  useEffect(() => { if (state !== "AUTHENTICATED") return; const timer = window.setTimeout(() => void load(), 0); return () => window.clearTimeout(timer); }, [state, load]);

  async function createSchedule() {
    setBusy(true); setFormError(null);
    try { await hrG2Api.createSchedule({ code: formCode, nameAr: formNameAr, nameEn: formNameEn, timezone: "Asia/Riyadh", shiftStart: formShiftStart, shiftEnd: formShiftEnd, breakMinutes: Number(formBreak) }); setShowForm(false); setFormCode(""); setFormNameAr(""); setFormNameEn(""); await load(); }
    catch (err) { setFormError(hrmErrorMessage(err).message); } finally { setBusy(false); }
  }

  if (["INITIALIZING","CHECKING_SESSION","REFRESHING"].includes(state)) return <AuthLoadingState phase="session" />;
  if (!canAdmin) return <HrWorkspace capabilities={capabilities} activeHref="/hr/schedules" translate={t}><p role="alert" className={styles.kpiHint}>{t("hrm.recruitment.dashboard.permissionHint")}</p></HrWorkspace>;

  const columns: HrColumn<Schedule>[] = [
    { key: "code", header: t("hrm.schedules.code") },
    { key: "name", header: t("hrm.schedules.name"), render: (r) => locale === "ar" ? r.nameAr : r.nameEn },
    { key: "shiftStart", header: t("hrm.schedules.shift"), render: (r) => `${r.shiftStart}—${r.shiftEnd}${r.isOvernight ? ` (${t("hrm.schedules.overnight")})` : ""}` },
    { key: "breakMinutes", header: t("hrm.schedules.break"), align: "end", render: (r) => `${r.breakMinutes}` },
    { key: "expectedMinutes", header: t("hrm.schedules.expected"), align: "end", render: (r) => `${Math.floor(r.expectedMinutes/60)}:${String(r.expectedMinutes%60).padStart(2,"0")}` },
  ];
  const activeCount = schedules.filter((schedule) => schedule.state === "ACTIVE").length;
  const overnightCount = schedules.filter((schedule) => schedule.isOvernight).length;

  return (
    <HrWorkspace capabilities={capabilities} activeHref="/hr/schedules" translate={t}>
      <HrG2ProductSurface eyebrow={t("hrm.g2.landing.hrOperations")} title={t("hrm.schedules.title")} subtitle={t("hrm.schedules.subtitle")}
        metrics={[
          { label: t("hrm.schedules.caption"), value: schedules.length },
          { label: t("hrm.schedules.expected"), value: activeCount },
          { label: t("hrm.schedules.overnight"), value: overnightCount },
        ]}
        primaryAction={<button type="button" onClick={() => setShowForm(!showForm)}>{t("hrm.schedules.create")}</button>}
      >
        {showForm ? (
          <form className={visualStyles.g2FormGrid} onSubmit={(e) => { e.preventDefault(); void createSchedule(); }}>
            <label className={styles.kpiLabel}>{t("hrm.schedules.code")}<input className={styles.filterSelect} value={formCode} onChange={(e) => setFormCode(e.target.value)} required /></label>
            <label className={styles.kpiLabel}>{t("hrm.schedules.nameAr")}<input className={styles.filterSelect} value={formNameAr} onChange={(e) => setFormNameAr(e.target.value)} required /></label>
            <label className={styles.kpiLabel}>{t("hrm.schedules.nameEn")}<input className={styles.filterSelect} value={formNameEn} onChange={(e) => setFormNameEn(e.target.value)} required /></label>
            <label className={styles.kpiLabel}>{t("hrm.schedules.start")}<input className={styles.filterSelect} type="time" value={formShiftStart} onChange={(e) => setFormShiftStart(e.target.value)} /></label>
            <label className={styles.kpiLabel}>{t("hrm.schedules.end")}<input className={styles.filterSelect} type="time" value={formShiftEnd} onChange={(e) => setFormShiftEnd(e.target.value)} /></label>
            <label className={styles.kpiLabel}>{t("hrm.schedules.breakMinutes")}<input className={styles.filterSelect} type="number" min="0" value={formBreak} onChange={(e) => setFormBreak(e.target.value)} /></label>
            {formError ? <p role="alert" className={styles.kpiHint} data-kind="error">{formError}</p> : null}
            <button type="submit" className={styles.linkButton} disabled={busy}>{t("hrm.schedules.create")}</button>
          </form>
        ) : null}
        <section className={visualStyles.productPanel} data-testid="schedule-operations-panel">
          {loading ? <HrLoading /> : error ? <HrErrorState error={error} onRetry={load} /> : <div data-testid="schedules-ready"><HrDataTable<Schedule> caption={t("hrm.schedules.caption")} columns={columns} rows={schedules} rowKey={(r) => r.id} emptyTitle={t("hrm.schedules.empty")} /></div>}
        </section>
      </HrG2ProductSurface>
    </HrWorkspace>
  );
}
