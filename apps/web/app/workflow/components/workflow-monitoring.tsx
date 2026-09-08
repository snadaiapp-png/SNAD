"use client";

import { useCallback, useEffect, useState } from "react";
import {
  workflowApi,
  type WorkflowMonitoringHealthResponse,
} from "@/lib/api/workflow-api";
import { describeWorkflowError } from "@/lib/workflow/error-messages";

export function WorkflowMonitoring() {
  const [health, setHealth] = useState<WorkflowMonitoringHealthResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setHealth(await workflowApi.getMonitoringHealth());
    } catch (cause: unknown) {
      setError(describeWorkflowError(cause, "تعذر تحميل حالة مراقبة سير العمل"));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  if (loading) return <p>جارٍ تحميل مؤشرات المراقبة…</p>;

  return (
    <div dir="rtl">
      <div style={{ display: "flex", justifyContent: "space-between", gap: 12, alignItems: "center", flexWrap: "wrap" }}>
        <h2 style={{ marginTop: 0, fontSize: 20 }}>المراقبة</h2>
        <button type="button" onClick={() => void load()}>تحديث</button>
      </div>
      {error && <p role="alert" style={{ color: "var(--snad-color-error)" }}>{error}</p>}
      {!error && health && (
        <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(180px, 1fr))", gap: 12 }}>
          <Metric label="الحالة" value={health.status} />
          <Metric label="الخطوات المتأخرة" value={health.overdueSteps} />
          <Metric label="الموافقات المتأخرة" value={health.overdueApprovals} />
          <Metric label="إجمالي مخالفات SLA" value={health.totalBreaches} />
        </div>
      )}
      {!error && !health && <p>لا تتوفر بيانات مراقبة حاليًا.</p>}
    </div>
  );
}

function Metric({ label, value }: { label: string; value: string | number }) {
  return (
    <div style={{ padding: 14, border: "1px solid var(--snad-color-border-default)", borderRadius: 10 }}>
      <div style={{ fontSize: 12, color: "var(--snad-color-text-secondary)" }}>{label}</div>
      <strong style={{ display: "block", marginTop: 6, fontSize: 24 }}>{value}</strong>
    </div>
  );
}
