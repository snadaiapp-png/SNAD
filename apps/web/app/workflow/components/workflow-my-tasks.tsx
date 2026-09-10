"use client";

import { useCallback, useEffect, useState } from "react";
import {
  workflowApi,
  type WorkflowWorkItemResponse,
} from "@/lib/api/workflow-api";
import { describeWorkflowError } from "@/lib/workflow/error-messages";

/**
 * R0.G4 — My Tasks partial-failure resilience.
 *
 * MINE (direct assignments) and POOL (work pool) are independent datasets
 * with independent loading/data/error state and independent retry. One
 * endpoint failing must never destroy or withhold the other dataset, and
 * the access state shown must be precise about WHICH dataset was denied.
 */
type DatasetState = {
  loading: boolean;
  data: WorkflowWorkItemResponse[];
  error: string | null;
  errorCode: number | null;
};

const DATASET_INITIAL: DatasetState = {
  loading: true,
  data: [],
  error: null,
  errorCode: null,
};

function DatasetErrorBanner({
  state,
  scope,
  testId,
  retryLabel,
  onRetry,
}: {
  state: DatasetState;
  scope: string;
  testId: string;
  retryLabel: string;
  onRetry: () => void;
}) {
  if (!state.error) return null;
  const denied = state.errorCode === 403;
  return (
    <div
      role="alert"
      data-testid={testId}
      style={{ display: "flex", gap: 12, alignItems: "center", flexWrap: "wrap" }}
    >
      <p style={{ color: "var(--snad-color-error)", margin: 0 }}>
        {denied
          ? `لا تملك صلاحية الوصول إلى ${scope}.`
          : state.error}
      </p>
      <button type="button" onClick={onRetry}>
        {retryLabel}
      </button>
    </div>
  );
}

export function WorkflowMyTasks() {
  const [mine, setMine] = useState<DatasetState>(DATASET_INITIAL);
  const [pool, setPool] = useState<DatasetState>(DATASET_INITIAL);
  const [conflict, setConflict] = useState<string | null>(null);
  const [commandError, setCommandError] = useState<string | null>(null);
  const [actioningId, setActioningId] = useState<string | null>(null);

  const loadMine = useCallback(async () => {
    setMine((previous) => ({ ...previous, loading: true, error: null, errorCode: null }));
    try {
      const data = await workflowApi.listMyWorkItems(50);
      setMine({ loading: false, data, error: null, errorCode: null });
    } catch (cause: unknown) {
      setMine({
        loading: false,
        data: [],
        error: describeWorkflowError(cause, "تعذر تحميل المهام المباشرة"),
        errorCode: (cause as { status?: number })?.status ?? null,
      });
    }
  }, []);

  const loadPool = useCallback(async () => {
    setPool((previous) => ({ ...previous, loading: true, error: null, errorCode: null }));
    try {
      const data = await workflowApi.listPoolWorkItems(50);
      setPool({ loading: false, data, error: null, errorCode: null });
    } catch (cause: unknown) {
      setPool({
        loading: false,
        data: [],
        error: describeWorkflowError(cause, "تعذر تحميل تجمع المهام"),
        errorCode: (cause as { status?: number })?.status ?? null,
      });
    }
  }, []);

  useEffect(() => {
    void loadMine();
    void loadPool();
  }, [loadMine, loadPool]);

  const runCommand = async (
    workItem: WorkflowWorkItemResponse,
    command: () => Promise<WorkflowWorkItemResponse>,
  ) => {
    setActioningId(workItem.id);
    setConflict(null);
    setCommandError(null);
    try {
      await command();
      await Promise.allSettled([loadMine(), loadPool()]);
    } catch (cause: unknown) {
      const status = (cause as { status?: number })?.status;
      if (status === 409) {
        setConflict(`تم تحديث المهمة ${workItem.id.slice(0, 8)}… من مستخدم آخر. أُعيد تحميل النسخة الأحدث.`);
        await Promise.allSettled([loadMine(), loadPool()]);
      } else if (status === 403) {
        setCommandError(describeWorkflowError(cause, "لا تملك صلاحية تنفيذ هذا الإجراء على المهمة"));
      } else {
        setCommandError(describeWorkflowError(cause, "فشل تنفيذ الإجراء"));
      }
    } finally {
      setActioningId(null);
    }
  };

  const claim = (workItem: WorkflowWorkItemResponse) =>
    runCommand(workItem, () => workflowApi.claimWorkItem(workItem.id, workItem.version));

  const release = (workItem: WorkflowWorkItemResponse) =>
    runCommand(workItem, () => workflowApi.releaseWorkItem(workItem.id, workItem.version));

  const complete = (workItem: WorkflowWorkItemResponse) =>
    runCommand(workItem, () => workflowApi.completeWorkItem(workItem.id, workItem.version));

  const bothDenied = mine.errorCode === 403 && pool.errorCode === 403;
  const anyLoading = mine.loading || pool.loading;

  return (
    <div dir="rtl">
      <h2 style={{ marginTop: 0, fontSize: 20 }}>مهامي</h2>

      {anyLoading && (
        <p role="status" data-testid="tasks-loading">
          {mine.loading && pool.loading
            ? "جارٍ التحميل…"
            : mine.loading
              ? "جارٍ تحميل المهام المباشرة…"
              : "جارٍ تحميل تجمع المهام…"}
        </p>
      )}

      {bothDenied && (
        <div role="alert" data-testid="both-denied">
          <p style={{ color: "var(--snad-color-error)", margin: 0 }}>
            حسابك لا يملك صلاحية الوصول إلى مهام سير العمل — تكلّم مع مالك النظام لمنح صلاحية
            WORKFLOW.TASK_EXECUTE.
          </p>
        </div>
      )}

      {conflict && <p role="alert" style={{ color: "var(--snad-color-warning)" }}>{conflict}</p>}
      {commandError && (
        <p role="alert" style={{ color: "var(--snad-color-error)" }}>
          {commandError}
        </p>
      )}

      <h3>مهامي المباشرة</h3>
      <DatasetErrorBanner
        state={mine}
        scope="المهام المباشرة"
        testId="error-mine"
        retryLabel="إعادة محاولة المهام المباشرة"
        onRetry={() => void loadMine()}
      />
      {!mine.loading && !mine.error && mine.data.length === 0 && <p>لا توجد مهام مباشرة.</p>}
      {mine.data.map((workItem) => (
        <WorkItemCard
          key={workItem.id}
          workItem={workItem}
          busy={actioningId === workItem.id}
          onComplete={() => void complete(workItem)}
          onRelease={() => void release(workItem)}
        />
      ))}

      <h3 style={{ marginTop: 24 }}>تجمع المهام (Work Pool)</h3>
      <DatasetErrorBanner
        state={pool}
        scope="تجمع المهام"
        testId="error-pool"
        retryLabel="إعادة محاولة تجمع المهام"
        onRetry={() => void loadPool()}
      />
      {!pool.loading && !pool.error && pool.data.length === 0 && <p>لا توجد مهام متاحة في التجمع.</p>}
      {pool.data.map((workItem) => (
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
        {workItem.status} · مرجع المزامنة #{workItem.version}
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
