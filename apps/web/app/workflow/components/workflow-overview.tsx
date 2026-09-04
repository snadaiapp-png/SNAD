"use client";

import { useCallback, useEffect, useState } from "react";
import {
  workflowApi,
  type WorkflowInstanceResponse,
  type WorkflowMonitoringHealthResponse,
} from "@/lib/api/workflow-api";

/**
 * Overview (design decision AP3): operational snapshot from the monitoring
 * health read model plus the latest instances. Read-only — commands live in
 * their sections.
 */
export function WorkflowOverview() {
  const [health, setHealth] = useState<WorkflowMonitoringHealthResponse | null>(null);
  const [instances, setInstances] = useState<WorkflowInstanceResponse[]>([]);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setError(null);
    try {
      const [h, i] = await Promise.all([
        workflowApi.getMonitoringHealth(),
        workflowApi.listInstances(10),
      ]);
      setHealth(h);
      setInstances(i);
    } catch (e: unknown) {
      setError((e as { message?: string })?.message ?? "تعذر تحميل النظرة العامة");
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  return (
    <div dir="rtl">
      {error && <p role="alert" style={{ color: "var(--snad-color-error)" }}>{error}</p>}
      <div style={{ display: "flex", gap: 16, flexWrap: "wrap", marginBottom: 24 }}>
        <Card label="الحالة العامة" value={health?.status ?? "—"} />
        <Card label="خطوات متأخرة" value={health?.overdueSteps ?? "—"} />
        <Card label="موافقات متأخرة" value={health?.overdueApprovals ?? "—"} />
        <Card label="انتهاكات SLA" value={health?.totalBreaches ?? "—"} />
      </div>
      <h3>أحدث المثيلات</h3>
      {instances.map((instance) => (
        <div key={instance.id} style={{ padding: "6px 0", borderBottom: "1px solid var(--snad-color-border-subtle)" }}>
          {instance.id.slice(0, 8)}… · {instance.status} · {instance.currentStepKey || "—"}
        </div>
      ))}
    </div>
  );
}

function Card({ label, value }: { label: string; value: string | number }) {
  return (
    <div style={{
      border: "1px solid var(--snad-color-border-default)", borderRadius: 10,
      padding: "12px 18px", minWidth: 140,
    }}>
      <div style={{ fontSize: 12, color: "var(--snad-color-text-secondary)" }}>{label}</div>
      <div style={{ fontSize: 22, fontWeight: 700 }}>{value}</div>
    </div>
  );
}
