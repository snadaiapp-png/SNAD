"use client";

import { useCallback, useEffect, useMemo, useState, type ReactNode } from "react";
import {
  workflowApi,
  type WorkflowMonitoringHealthResponse,
} from "@/lib/api/workflow-api";
import { describeWorkflowError } from "@/lib/workflow/error-messages";
import styles from "../workflow.module.css";
import {
  StatusBadge,
  WorkflowEmptyState,
  WorkflowSectionHeader,
} from "./workflow-ui";

export function WorkflowMonitoring() {
  const [health, setHealth] = useState<WorkflowMonitoringHealthResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [autoRefresh, setAutoRefresh] = useState(false);
  const [lastUpdatedAt, setLastUpdatedAt] = useState<Date | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setHealth(await workflowApi.getMonitoringHealth());
      setLastUpdatedAt(new Date());
    } catch (cause: unknown) {
      setError(describeWorkflowError(cause, "تعذر تحميل حالة مراقبة سير العمل"));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  useEffect(() => {
    if (!autoRefresh) return;
    const timer = window.setInterval(() => {
      if (document.visibilityState === "visible") void load();
    }, 60_000);
    return () => window.clearInterval(timer);
  }, [autoRefresh, load]);

  const totalAttention = useMemo(
    () => (health?.overdueSteps ?? 0) + (health?.overdueApprovals ?? 0) + (health?.totalBreaches ?? 0),
    [health],
  );

  return (
    <div dir="rtl">
      <WorkflowSectionHeader
        eyebrow="الموثوقية"
        title="المراقبة"
        description="صحة محرك سير العمل ومؤشرات التأخير ومستوى الخدمة في شاشة تشغيلية واحدة."
        actions={
          <>
            <label className={styles.cardMeta}>
              <input
                type="checkbox"
                checked={autoRefresh}
                onChange={(event) => setAutoRefresh(event.target.checked)}
              />
              تحديث تلقائي كل دقيقة
            </label>
            <button type="button" onClick={() => void load()} disabled={loading}>
              {loading ? "جارٍ التحديث…" : "تحديث الآن"}
            </button>
          </>
        }
      />

      {error && (
        <div role="alert" className={`${styles.alert} ${styles.alertError}`}>
          <span>{error}</span>
          <button type="button" onClick={() => void load()}>إعادة المحاولة</button>
        </div>
      )}

      {!error && health ? (
        <>
          <div className={styles.metricsGrid}>
            <Metric label="الحالة" value={<StatusBadge value={health.status} />} hint="صحة الخدمة الحالية" />
            <Metric label="الخطوات المتأخرة" value={health.overdueSteps} hint="خطوات تجاوزت وقتها" />
            <Metric label="الموافقات المتأخرة" value={health.overdueApprovals} hint="قرارات تحتاج تصعيدًا" />
            <Metric label="مخالفات SLA" value={health.totalBreaches} hint="إجمالي المخالفات المرصودة" />
          </div>

          <div className={styles.insight}>
            <span className={styles.insightDot} aria-hidden="true" />
            <div className={styles.insightText}>
              <span className={styles.insightTitle}>
                {totalAttention === 0
                  ? "الوضع التشغيلي مستقر وفق المؤشرات المتاحة."
                  : `يلزم فحص ${totalAttention} مؤشرًا متأخرًا أو مخالفًا.`}
              </span>
              <span className={styles.insightDescription}>
                آخر تحديث: {lastUpdatedAt
                  ? new Intl.DateTimeFormat("ar-SA", { dateStyle: "medium", timeStyle: "medium" }).format(lastUpdatedAt)
                  : "—"}
              </span>
            </div>
          </div>
        </>
      ) : !loading && !error ? (
        <WorkflowEmptyState
          title="لا تتوفر بيانات مراقبة"
          description="تعذر الحصول على قراءة تشغيلية في الوقت الحالي."
        />
      ) : null}
    </div>
  );
}

function Metric({
  label,
  value,
  hint,
}: {
  label: string;
  value: ReactNode;
  hint: string;
}) {
  return (
    <div className={styles.metricCard}>
      <span className={styles.metricLabel}>{label}</span>
      <div className={styles.metricValue}>{value}</div>
      <span className={styles.metricHint}>{hint}</span>
    </div>
  );
}
