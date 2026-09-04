"use client";

import { useCallback, useEffect, useState } from "react";
import {
  workflowApi,
  type WorkflowWorkItemResponse,
} from "@/lib/api/workflow-api";
import { describeWorkflowError } from "@/lib/workflow/error-messages";

/**
 * My Tasks (design decisions C3/L3/T3): direct and pool work are shown
 * separately, every command sends the current `version` as expectedVersion,
 * and a 409 conflict reloads the item and surfaces a conflict message.
 * The server remains the authorization boundary.
 */
export function WorkflowMyTasks() {
  const [mine, setMine] = useState<WorkflowWorkItemResponse[]>([]);
  const [pool, setPool] = useState<WorkflowWorkItemResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [conflict, setConflict] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const [myItems, poolItems] = await Promise.all([
        workflowApi.listMyWorkItems(50),
        workflowApi.listPoolWorkItems(50),
      ]);
      setMine(myItems);
      setPool(poolItems);
    } catch (e: unknown) {
      // User-facing message is always the mapped Arabic guidance — raw
      // transport details stay in the console (Y2 hotfix contract).
      setError(describeWorkflowError(e, "تعذر تحميل المهام"));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const runCommand = async (
    command: () => Promise<WorkflowWorkItemResponse>,
    itemId: string,
  ) => {
    setConflict(null);
    setError(null);
    try {
      await command();
      await load();
    } catch (e: unknown) {
      const err = e as { status?: number; message?: string };
      if (err?.status === 409) {
        setConflict(`تعارض إصدار في المهمة ${itemId}: تم تحديثها من قبل مستخدم آخر — أعيد التحميل.`);
        await load();
      } else {
        setError(describeWorkflowError(e, "فشل تنفيذ الإجراء"));
      }
    }
  };

  const claim = (item: WorkflowWorkItemResponse) =>
    runCommand(() => workflowApi.claimWorkItem(item.id, item.version), item.id);
  const release = (item: WorkflowWorkItemResponse) =>
    runCommand(() => workflowApi.releaseWorkItem(item.id, item.version), item.id);
  const complete = (item: WorkflowWorkItemResponse) =>
    runCommand(() => workflowApi.completeWorkItem(item.id, item.version), item.id);

  if (loading) return <p>جارٍ التحميل…</p>;

  return (
    <div dir="rtl">
      {conflict && (
        <p role="alert" style={{ color: "var(--snad-color-warning)" }}>{conflict}</p>
      )}
      {error && (
        <div role="alert" style={{ display: "flex", gap: 12, alignItems: "center", flexWrap: "wrap" }}>
          <p style={{ color: "var(--snad-color-error)", margin: 0 }}>{error}</p>
          <button onClick={() => void load()}>إعادة المحاولة</button>
        </div>
      )}

      <h3>مهامي المباشرة</h3>
      {!error && mine.length === 0 && <p>لا توجد مهام مباشرة.</p>}
      {mine.map((item) => (
        <div key={item.id} style={{ border: "1px solid var(--snad-color-border-default)", borderRadius: 8, padding: 12, marginBottom: 8 }}>
          <strong>{item.title}</strong>{" "}
          <span style={{ fontSize: 12, color: "var(--snad-color-text-secondary)" }}>
            {item.status} · إصدار {item.version}
          </span>
          <div style={{ marginTop: 8, display: "flex", gap: 8 }}>
            {item.status === "CLAIMED" && (
              <>
                <button onClick={() => void complete(item)}>إكمال</button>
                <button onClick={() => void release(item)}>إفلات</button>
              </>
            )}
            {item.status === "ASSIGNEE_UNAVAILABLE" && (
              <span style={{ color: "var(--snad-color-warning)" }}>
                غير متاحة — يلزم إعادة تعيين مصرّح بها
              </span>
            )}
          </div>
        </div>
      ))}

      <h3 style={{ marginTop: 24 }}>تجمع المهام (Work Pool)</h3>
      {!error && pool.length === 0 && <p>لا توجد مهام متاحة في التجمع.</p>}
      {pool.map((item) => (
        <div key={item.id} style={{ border: "1px dashed var(--snad-color-border-default)", borderRadius: 8, padding: 12, marginBottom: 8 }}>
          <strong>{item.title}</strong>{" "}
          <span style={{ fontSize: 12, color: "var(--snad-color-text-secondary)" }}>متاحة · إصدار {item.version}</span>
          <div style={{ marginTop: 8 }}>
            <button onClick={() => void claim(item)}>استلام (Claim)</button>
          </div>
        </div>
      ))}
    </div>
  );
}
