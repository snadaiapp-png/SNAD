"use client";

import { useCallback, useEffect, useMemo, useState, type ReactNode } from "react";
import {
  workflowApi,
  type WorkflowInstanceResponse,
  type WorkflowMonitoringHealthResponse,
} from "@/lib/api/workflow-api";
import { describeWorkflowError } from "@/lib/workflow/error-messages";
import styles from "../workflow.module.css";
import {
  StatusBadge,
  WorkflowEmptyState,
  WorkflowSectionHeader,
  formatWorkflowStatus,
} from "./workflow-ui";

/**
 * Operational snapshot from the monitoring health read model plus the latest
 * workflow instances. Commands remain in their dedicated sections.
 */
export function WorkflowOverview() {
  const [health, setHealth] = useState<WorkflowMonitoringHealthResponse | null>(null);
  const [instances, setInstances] = useState<WorkflowInstanceResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [lastUpdatedAt, setLastUpdatedAt] = useState<Date | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const [h, i] = await Promise.all([
        workflowApi.getMonitoringHealth(),
        workflowApi.listInstances(10),
      ]);
      setHealth(h);
      setInstances(i);
      setLastUpdatedAt(new Date());
    } catch (e: unknown) {
      setError(describeWorkflowError(e, "تعذر تحميل النظرة العامة"));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const attentionCount = useMemo(
    () => (health?.overdueSteps ?? 0) + (health?.overdueApprovals ?? 0) + (health?.totalBreaches ?? 0),
    [health],
  );

  return (
    <div dir="rtl">
      <WorkflowSectionHeader
        eyebrow="مركز القيادة"
        title="نظرة تشغيلية"
        description="ملخص فوري لصحة المحرك، الالتزامات المتأخرة، وآخر عمليات التنفيذ."
        actions={
          <button type="button" onClick={() => void load()} disabled={loading}>
            {loading ? "جارٍ التحديث…" : "تحديث البيانات"}
          </button>
        }
      />

      {error && (
        <div role="alert" className={`${styles.alert} ${styles.alertError}`}>
          <span>{error}</span>
          <button type="button" onClick={() => void load()}>إعادة المحاولة</button>
        </div>
      )}

      <div className={styles.metricsGrid} aria-busy={loading}>
        <Metric
          label="الحالة العامة"
          value={health ? <StatusBadge value={health.status} /> : "—"}
          hint={health ? formatWorkflowStatus(health.status) : "بانتظار البيانات"}
        />
        <Metric
          label="الخطوات المتأخرة"
          value={health?.overdueSteps ?? "—"}
          hint="تحتاج متابعة تنفيذية"
        />
        <Metric
          label="الموافقات المتأخرة"
          value={health?.overdueApprovals ?? "—"}
          hint="قرارات تجاوزت الوقت المستهدف"
        />
        <Metric
          label="انتهاكات SLA"
          value={health?.totalBreaches ?? "—"}
          hint="إجمالي مؤشرات مستوى الخدمة"
        />
      </div>

      {!error && health && (
        <div className={styles.insight} role="status">
          <span className={styles.insightDot} aria-hidden="true" />
          <div className={styles.insightText}>
            <span className={styles.insightTitle}>
              {attentionCount === 0
                ? "لا توجد مؤشرات تشغيلية متأخرة حاليًا."
                : `هناك ${attentionCount} مؤشرًا يحتاج متابعة.`}
            </span>
            <span className={styles.insightDescription}>
              آخر تحديث: {lastUpdatedAt
                ? new Intl.DateTimeFormat("ar-SA", { timeStyle: "medium" }).format(lastUpdatedAt)
                : "—"}
            </span>
          </div>
        </div>
      )}

      <section className={styles.subsection} aria-labelledby="workflow-latest-instances">
        <div className={styles.subsectionTitleRow}>
          <h3 id="workflow-latest-instances" className={styles.subsectionTitle}>أحدث المثيلات</h3>
          <span className={styles.count}>{instances.length} عنصر</span>
        </div>

        {!loading && !error && instances.length === 0 ? (
          <WorkflowEmptyState
            title="لا توجد مثيلات حديثة"
            description="ستظهر هنا أحدث عمليات سير العمل بمجرد بدء تنفيذها."
          />
        ) : instances.length > 0 ? (
          <div className={styles.tableWrap}>
            <table className={styles.table}>
              <thead>
                <tr>
                  <th>الكيان</th>
                  <th>الخطوة الحالية</th>
                  <th>الإصدار</th>
                  <th>الحالة</th>
                  <th>المعرّف</th>
                </tr>
              </thead>
              <tbody>
                {instances.map((instance) => (
                  <tr key={instance.id}>
                    <td>{instance.businessEntityType || "—"}</td>
                    <td>{instance.currentStepKey || "—"}</td>
                    <td><span className={styles.mono}>v{instance.workflowVersion}</span></td>
                    <td><StatusBadge value={instance.status} /></td>
                    <td><span className={styles.mono}>{instance.id.slice(0, 8)}…</span></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : null}
      </section>
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
