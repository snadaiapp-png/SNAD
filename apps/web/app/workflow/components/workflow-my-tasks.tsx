"use client";

import { useCallback, useEffect, useState } from "react";
import {
  workflowApi,
  type WorkflowWorkItemResponse,
} from "@/lib/api/workflow-api";
import { describeWorkflowError } from "@/lib/workflow/error-messages";

export function WorkflowMyTasks() {
  const [mine, setMine] = useState<WorkflowWorkItemResponse[]>([]);
  const [pool, setPool] = useState<WorkflowWorkItemResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [conflict, setConflict] = useState<string | null>(null);
  const [actioningId, setActioningId] = useState<string | null>(null);

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
    } catch (cause: unknown) {
      setError(describeWorkflowError(cause, "تعذر تحميل المهام"));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const runCommand = async (
    workItem: WorkflowWorkItemResponse,
    command: () => Promise<WorkflowWorkItemResponse>,
  ) => {
    setActioningId(workItem.id);
    setConflict(null);
    setError(null);
    try {
      await command();
      await load();
    } catch (cause: unknown) {
      const status = (cause as { status?: number })?.status;
      if (status === 409) {
        setConflict(`تم تحديث المهمة ${workItem.id.slice(0, 8)}… من مستخدم آخر. أُعيد تحميل النسخة الأحدث.`);
        await load();
      } else if (status === 403) {
        setError(describeWorkflowError(cause, "لا تملك صلاحية تنفيذ هذا الإجراء على المهمة"));
      } else {
        setError(describeWorkflowError(cause, "فشل تنفيذ الإجراء"));
      }
    } finally {
      setActioningId(null);
    }
  };

  const claim = (workItem: WorkflowWorkItemResponse) =>
    runCommand(
      workItem,
      () => workflowApi.claimWorkItem(workItem.id, workItem.version),
    );

  const release = (workItem: WorkflowWorkItemResponse) =>
    runCommand(
      workItem,
      () => workflowApi.releaseWorkItem(workItem.id, workItem.version),
    );

  const complete = (workItem: WorkflowWorkItemResponse) =>
    runCommand(
      workItem,
      () => workflowApi.completeWorkItem(workItem.id, workItem.version),
    );

  if (loading) return <p>جارٍ التحميل…</p>;

  return (
    <div dir="rtl">
      <h2 style={{ marginTop: 0, fontSize: 20 }}>مهامي</h2>
      {conflict && <p role="alert" style={{ color: "var(--snad-color-warning)" }}>{conflict}</p>}
      {error && (
        <div role="alert" style={{ display: "flex", gap: 12, alignItems: "center", flexWrap: "wrap" }}>
          <p style={{ color: "var(--snad-color-error)", margin: 0 }}>{error}</p>
          <button type="button" onClick={() => void load()}>إعادة المحاولة</button>
        </div>
      )}

      <h3>مهامي المباشرة</h3>
      {!error && mine.length === 0 && <p>لا توجد مهام مباشرة.</p>}
      {mine.map((workItem) => (
        <WorkItemCard
          key={workItem.id}
          workItem={workItem}
          busy={actioningId === workItem.id}
          onComplete={() => void complete(workItem)}
          onRelease={() => void release(workItem)}
        />
      ))}

      <h3 style={{ marginTop: 24 }}>تجمع المهام (Work Pool)</h3>
      {!error && pool.length === 0 && <p>لا توجد مهام متاحة في التجمع.</p>}
      {pool.map((workItem) => (
        <WorkItemCard
          key={workItem.id}
          workItem={workItem}
          busy={actioningId === workItem.id}
          onClaim={() => void claim(workItem)}
        />
      ))}
    </div>
  );
}

function WorkItemCard({
  workItem,
  busy,
  onClaim,
  onComplete,
  onRelease,
}: {
  workItem: WorkflowWorkItemResponse;
  busy: boolean;
  onClaim?: () => void;
  onComplete?: () => void;
  onRelease?: () => void;
}) {
  return (
    <article style={{ border: "1px solid var(--snad-color-border-default)", borderRadius: 8, padding: 12, marginBottom: 8 }}>
      <strong>{workItem.title}</strong>{" "}
      <span style={{ fontSize: 12, color: "var(--snad-color-text-secondary)" }}>
        {workItem.status} · إصدار {workItem.version}
      </span>
      <div style={{ marginTop: 8, display: "flex", gap: 8, flexWrap: "wrap" }}>
        {onClaim && <button type="button" disabled={busy} onClick={onClaim}>استلام</button>}
        {onComplete && <button type="button" disabled={busy} onClick={onComplete}>إكمال</button>}
        {onRelease && <button type="button" disabled={busy} onClick={onRelease}>إفلات</button>}
        {workItem.status === "ASSIGNEE_UNAVAILABLE" && (
          <span style={{ color: "var(--snad-color-warning)" }}>غير متاحة — يلزم إعادة تعيين مصرّح بها</span>
        )}
      </div>
    </article>
  );
}
