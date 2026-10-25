"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { AuthLoadingState } from "@/components/auth/auth-loading-state";
import { hrG2Api, type G2MonthlyAttendanceReportRow } from "@/lib/api/hr-g2-api";
import { useAuth } from "@/lib/auth/auth-provider";
import { useI18n } from "@/lib/i18n/I18nProvider";
import { HrWorkspace } from "../../components/hr-workspace";
import { HrErrorState, HrLoading } from "../../components/hr-feedback";
import { HrDataTable, type HrColumn } from "../../components/hr-data-table";
import { HrActionBar, HrKpiCard, HrKpiGrid, HrOperationalPanel, HrProductHeader } from "../../components/hr-product-surface";
import styles from "../../hr.module.css";

const REFRESH_INTERVAL_MS = 30_000;
const STALE_AFTER_MS = 90_000;

export default function LiveHrAnalyticsPage() {
  const { state, me } = useAuth();
  const { t } = useI18n();
  const capabilities = me?.capabilities ?? [];
  const canAdmin = capabilities.includes("HRM.ATTENDANCE.ADMIN");
  const canTeamView = capabilities.includes("HRM.ATTENDANCE.TEAM_VIEW");
  const canView = canAdmin || canTeamView;
  // Initialize the period after mount so render remains pure.
  const [year, setYear] = useState(0);
  const [month, setMonth] = useState(0);
  const [rows, setRows] = useState<G2MonthlyAttendanceReportRow[]>([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState<unknown>(null);
  const [lastSuccessAt, setLastSuccessAt] = useState<number | null>(null);
  const [clock, setClock] = useState(0);
  const inFlight = useRef(false);
  const mounted = useRef(true);

  const load = useCallback(async (initial = false) => {
    if (inFlight.current) return;
    inFlight.current = true;
    if (initial) setLoading(true);
    else setRefreshing(true);
    setError(null);
    try {
      const result = canAdmin
        ? await hrG2Api.adminMonthlyAttendanceReport(year, month)
        : await hrG2Api.teamMonthlyAttendanceReport(year, month);
      if (!mounted.current) return;
      setRows(result);
      setLastSuccessAt(Date.now());
    } catch (err) {
      if (mounted.current) setError(err);
    } finally {
      inFlight.current = false;
      if (mounted.current) {
        setLoading(false);
        setRefreshing(false);
      }
    }
  }, [canAdmin, month, year]);

  useEffect(() => {
    mounted.current = true;
    return () => { mounted.current = false; };
  }, []);

  useEffect(() => {
    if (state !== "AUTHENTICATED" || !canView) return;
    const initialTimer = window.setTimeout(() => {
      const now = new Date();
      setYear((current) => current || now.getFullYear());
      setMonth((current) => current || now.getMonth() + 1);
    }, 0);
    const refreshTimer = window.setInterval(() => void load(false), REFRESH_INTERVAL_MS);
    const clockTimer = window.setInterval(() => setClock(Date.now()), 5_000);
    return () => {
      window.clearTimeout(initialTimer);
      window.clearInterval(refreshTimer);
      window.clearInterval(clockTimer);
    };
  }, [state, canView, load]);

  useEffect(() => {
    if (state !== "AUTHENTICATED" || !canView || year < 2000 || month < 1) return;
    const timer = window.setTimeout(() => void load(true), 0);
    return () => window.clearTimeout(timer);
  }, [state, canView, year, month, load]);

  if (["INITIALIZING", "CHECKING_SESSION", "REFRESHING"].includes(state)) {
    return <AuthLoadingState phase="session" />;
  }

  if (!canView) {
    return <HrWorkspace capabilities={capabilities} activeHref="/hr/analytics/live" translate={t}>
      <p role="alert" className={styles.kpiHint}>{t("hrm.recruitment.dashboard.permissionHint")}</p>
    </HrWorkspace>;
  }

  const stale = lastSuccessAt === null || clock - lastSuccessAt > STALE_AFTER_MS;
  const totalPeople = rows.length;
  const absentDays = rows.reduce((sum, row) => sum + row.absentDays, 0);
  const lateOccurrences = rows.reduce((sum, row) => sum + row.lateOccurrences, 0);
  const workedMinutes = rows.reduce((sum, row) => sum + row.workedMinutes, 0);
  const columns: HrColumn<G2MonthlyAttendanceReportRow>[] = [
    { key: "employmentId", header: t("hrm.attendanceReport.employee") },
    { key: "scheduledDays", header: t("hrm.attendanceReport.scheduledDays"), align: "end", render: (row) => row.scheduledDays ?? "—" },
    { key: "workedMinutes", header: t("hrm.attendanceReport.workedMinutes"), align: "end" },
    { key: "absentDays", header: t("hrm.attendanceReport.absent"), align: "end" },
    { key: "leaveDays", header: t("hrm.attendanceReport.leave"), align: "end" },
    { key: "lateOccurrences", header: t("hrm.attendanceReport.late"), align: "end" },
    { key: "earlyDepartures", header: t("hrm.attendanceReport.earlyDeparture"), align: "end" },
    { key: "missingPunches", header: t("hrm.attendanceReport.missingPunch"), align: "end" },
    { key: "attendanceStatus", header: t("hrm.attendanceReport.status") },
  ];

  return <HrWorkspace capabilities={capabilities} activeHref="/hr/analytics/live" translate={t}>
    <section data-testid="hr-live-analytics" aria-live="polite">
      <HrProductHeader
        eyebrow={t("hrm.g2.landing.hrOperations")}
        title="التحليلات الحية للموارد البشرية"
        subtitle="مراقبة مؤشرات الحضور الشهرية مع تحديث تلقائي كل 30 ثانية. هذه النسخة تعرض مؤشرات الحضور فقط؛ لا تدّعي وجود بث لحظي من الخادم."
      />
      <HrActionBar label="التحكم في فترة التحليل">
        <label htmlFor="live-hr-year" className={styles.kpiLabel}>السنة</label>
        <input id="live-hr-year" type="number" min={2000} max={2100} className={styles.filterSelect} value={year} onChange={(event) => setYear(Number(event.target.value))} />
        <label htmlFor="live-hr-month" className={styles.kpiLabel}>الشهر</label>
        <select id="live-hr-month" className={styles.filterSelect} value={month} onChange={(event) => setMonth(Number(event.target.value))}>
          {Array.from({ length: 12 }, (_, index) => <option key={index + 1} value={index + 1}>{index + 1}</option>)}
        </select>
        <button type="button" className={styles.filterSelect} onClick={() => void load(false)} disabled={refreshing}>
          {refreshing ? "جارٍ التحديث…" : "تحديث الآن"}
        </button>
      </HrActionBar>

      <div role="status" data-testid="hr-live-freshness" className={styles.kpiHint}>
        {stale ? "البيانات قديمة أو لم ينجح تحديثها بعد — لا تعتبرها حالة آنية." : "آخر جلب ناجح: " + new Date(lastSuccessAt as number).toLocaleString()}
        {" · "}
        {refreshing ? "جارٍ جلب البيانات" : "التحديث التلقائي كل 30 ثانية"}
      </div>

      <HrKpiGrid label="مؤشرات الحضور">
        <HrKpiCard label={t("hrm.attendanceReport.employee")} value={totalPeople} />
        <HrKpiCard label={t("hrm.attendanceReport.absent")} value={absentDays} tone={absentDays > 0 ? "attention" : "neutral"} />
        <HrKpiCard label={t("hrm.attendanceReport.late")} value={lateOccurrences} tone={lateOccurrences > 0 ? "attention" : "neutral"} />
        <HrKpiCard label={t("hrm.attendanceReport.workedMinutes")} value={workedMinutes} />
      </HrKpiGrid>

      {loading ? <HrLoading /> : error ? <HrErrorState error={error} onRetry={() => void load(true)} /> : (
        <HrOperationalPanel label="تفاصيل الحضور" title={`الحضور — ${year}/${month}`} description="البيانات من واجهة تقرير الحضور الحالية، مع تطبيق نطاق الفريق أو الإدارة حسب صلاحيات المستخدم.">
          <HrDataTable<G2MonthlyAttendanceReportRow>
            caption={`الحضور — ${year}/${month}`}
            columns={columns}
            rows={rows}
            rowKey={(row) => row.employmentId}
            emptyTitle={t("hrm.attendanceReport.empty")}
          />
        </HrOperationalPanel>
      )}

      <HrOperationalPanel label="تحليلات الذكاء الاصطناعي" title="الذكاء الاصطناعي — بانتظار ربط معتمد" description="لم تُفعّل استنتاجات مولّدة آليًا في هذه النسخة. يلزم اعتماد مزود النموذج وسياسة الخصوصية، ثم ربط كل استنتاج بمقاييس مصدرية قابلة للتحقق قبل عرضه.">
        <p className={styles.kpiHint}>لا تُعرض توقعات أو توصيات مولّدة على أنها حقائق، ولا تُتخذ قرارات تخص الموظفين آليًا.</p>
      </HrOperationalPanel>
    </section>
  </HrWorkspace>;
}
