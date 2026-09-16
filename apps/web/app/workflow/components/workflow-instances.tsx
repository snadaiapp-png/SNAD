"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import {
  workflowApi,
  type WorkflowInstanceResponse,
} from "@/lib/api/workflow-api";
import { describeWorkflowError } from "@/lib/workflow/error-messages";
import styles from "../workflow.module.css";
import {
  StatusBadge,
  WorkflowEmptyState,
  WorkflowSectionHeader,
} from "./workflow-ui";

export function WorkflowInstances() {
  const [instances, setInstances] = useState<WorkflowInstanceResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [search, setSearch] = useState("");
  const [statusFilter, setStatusFilter] = useState("");

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setInstances(await workflowApi.listInstances(50));
    } catch (cause: unknown) {
      setError(describeWorkflowError(cause, "تعذر تحميل مثيلات سير العمل"));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const statuses = useMemo(
    () => Array.from(new Set(instances.map((instance) => instance.status))).sort(),
    [instances],
  );

  const filtered = useMemo(() => {
    const needle = search.trim().toLowerCase();
    return instances.filter((instance) => {
      if (statusFilter && instance.status !== statusFilter) return false;
      if (!needle) return true;
      return [
        instance.businessEntityType,
        instance.businessEntityId,
        instance.currentStepKey,
        instance.id,
      ].some((value) => value?.toLowerCase().includes(needle));
    });
  }, [instances, search, statusFilter]);

  return (
    <div dir="rtl">
      <WorkflowSectionHeader
        eyebrow="التنفيذ"
        title="المثيلات"
        description="تتبّع كل عملية تشغيل جارية أو مكتملة، والخطوة التي وصلت إليها وإصدار التعريف المستخدم."
        actions={
          <button type="button" onClick={() => void load()} disabled={loading}>
            {loading ? "جارٍ التحديث…" : "تحديث"}
          </button>
        }
      />

      {error && (
        <div role="alert" className={`${styles.alert} ${styles.alertError}`}>
          <span>{error}</span>
          <button type="button" onClick={() => void load()}>إعادة المحاولة</button>
        </div>
      )}

      <div className={styles.toolbar}>
        <input
          className={styles.toolbarGrow}
          aria-label="بحث في المثيلات"
          placeholder="بحث بالكيان أو المعرّف أو الخطوة"
          value={search}
          onChange={(event) => setSearch(event.target.value)}
        />
        <select
          aria-label="تصفية حالة المثيلات"
          value={statusFilter}
          onChange={(event) => setStatusFilter(event.target.value)}
        >
          <option value="">كل الحالات</option>
          {statuses.map((status) => <option key={status} value={status}>{status}</option>)}
        </select>
        <span className={styles.count}>{filtered.length} من {instances.length}</span>
      </div>

      {!loading && !error && filtered.length === 0 ? (
        <WorkflowEmptyState
          title={instances.length === 0 ? "لا توجد مثيلات سير عمل" : "لا توجد نتائج مطابقة"}
          description={
            instances.length === 0
              ? "ستظهر المثيلات هنا عند بدء أول سير عمل."
              : "غيّر عبارة البحث أو مرشح الحالة لعرض نتائج أخرى."
          }
        />
      ) : filtered.length > 0 ? (
        <div className={styles.tableWrap}>
          <table className={styles.table}>
            <thead>
              <tr>
                <th>الكيان</th>
                <th>معرّف الكيان</th>
                <th>الخطوة الحالية</th>
                <th>الإصدار</th>
                <th>الحالة</th>
              </tr>
            </thead>
            <tbody>
              {filtered.map((instance) => (
                <tr key={instance.id}>
                  <td>{instance.businessEntityType || "—"}</td>
                  <td><span className={styles.mono}>{instance.businessEntityId.slice(0, 12)}…</span></td>
                  <td>{instance.currentStepKey || "—"}</td>
                  <td><span className={styles.mono}>v{instance.workflowVersion}</span></td>
                  <td><StatusBadge value={instance.status} /></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : null}
    </div>
  );
}
