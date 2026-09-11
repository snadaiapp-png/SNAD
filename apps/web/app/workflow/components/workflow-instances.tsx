"use client";

import { useCallback, useEffect, useState } from "react";
import { AuthLoadingState } from "@/components/auth/auth-loading-state";
import {
  workflowApi,
  type WorkflowInstanceResponse,
} from "@/lib/api/workflow-api";
import { describeWorkflowError } from "@/lib/workflow/error-messages";

export function WorkflowInstances() {
  const [instances, setInstances] = useState<WorkflowInstanceResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

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

  if (loading) return <AuthLoadingState />;

  return (
    <div dir="rtl">
      <h2 style={{ marginTop: 0, fontSize: 20 }}>المثيلات</h2>
      {error && (
        <div role="alert" style={{ display: "flex", alignItems: "center", gap: 12, flexWrap: "wrap" }}>
          <p style={{ color: "var(--snad-color-error)", margin: 0 }}>{error}</p>
          <button type="button" onClick={() => void load()}>إعادة المحاولة</button>
        </div>
      )}
      {!error && instances.length === 0 ? (
        <p>لا توجد مثيلات سير عمل.</p>
      ) : (
        <div style={{ overflowX: "auto" }}>
          <table style={{ width: "100%", borderCollapse: "collapse" }}>
            <thead>
              <tr style={{ textAlign: "right" }}>
                <th style={cellStyle}>الكيان</th>
                <th style={cellStyle}>معرّف الكيان</th>
                <th style={cellStyle}>الخطوة الحالية</th>
                <th style={cellStyle}>الإصدار</th>
                <th style={cellStyle}>الحالة</th>
              </tr>
            </thead>
            <tbody>
              {instances.map((instance) => (
                <tr key={instance.id}>
                  <td style={cellStyle}>{instance.businessEntityType}</td>
                  <td style={cellStyle}>{instance.businessEntityId.slice(0, 8)}…</td>
                  <td style={cellStyle}>{instance.currentStepKey || "—"}</td>
                  <td style={cellStyle}>v{instance.workflowVersion}</td>
                  <td style={cellStyle}>{instance.status}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}

const cellStyle = {
  padding: "10px 12px",
  borderBottom: "1px solid var(--snad-color-border-default)",
};
