"use client";

import { useCallback, useEffect, useState } from "react";
import {
  workflowApi,
  type WorkflowWorkItemResponse,
} from "@/lib/api/workflow-api";
import { describeWorkflowError } from "@/lib/workflow/error-messages";
import styles from "../workflow.module.css";
import {
  StatusBadge,
  WorkflowEmptyState,
  WorkflowSectionHeader,
  formatPriority,
  formatWorkflowDate,
} from "./workflow-ui";

/**
 * R0.G4 — My Tasks partial-failure resilience.
 *
 * MINE (direct assignments) and POOL (work pool) are independent datasets
 * with independent loading/data/error state and independent retry.
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
      className={`${styles.alert} ${styles.alertError}`}
    >
      <span>
        {denied
          ? `لا تملك صلاحية الوصول إلى ${scope}.`
          : state.error}
      </span>
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
      <WorkflowSectionHeader
        eyebrow="صندوق العمل"
        title="مهامي"
        description="مهامك المباشرة والمهام المتاحة للاستلام من تجمع العمل، مع الأولوية والموعد والحالة."
        actions={
          <button
            type="button"
            disabled={anyLoading}
            onClick={() => void Promise.allSettled([loadMine(), loadPool()])}
          >
            {anyLoading ? "جارٍ التحديث…" : "تحديث المهام"}
          </button>
        }
      />

      {anyLoading && (
        <p role="status" data-testid="tasks-loading" className={styles.cardMeta}>
          {mine.loading && pool.loading
            ? "جارٍ تحميل المهام…"
            : mine.loading
              ? "جارٍ تحميل المهام المباشرة…"
              : "جارٍ تحميل تجمع المهام…"}
        </p>
      )}

      {bothDenied && (
        <div role="alert" data-testid="both-denied" className={`${styles.alert} ${styles.alertWarning}`}>
          <div>
            <strong>لا يملك صلاحية الوصول إلى مهام سير العمل.</strong>
            <div>
              اطلب من مسؤول المؤسسة منح صلاحية <code>WORKFLOW.TASK_EXECUTE</code>.
              لم تُخفَ بيانات متاحة؛ الخادم رفض مجموعتي المهام مباشرة.
            </div>
          </div>
        </div>
      )}

      {conflict && (
        <div role="alert" className={`${styles.alert} ${styles.alertWarning}`}>{conflict}</div>
      )}
      {commandError && (
        <div role="alert" className={`${styles.alert} ${styles.alertError}`}>{commandError}</div>
      )}

      <div className={styles.cardsGrid}>
        <section className={styles.subsection} aria-labelledby="direct-tasks-title">
          <div className={styles.subsectionTitleRow}>
            <h3 id="direct-tasks-title" className={styles.subsectionTitle}>مهامي المباشرة</h3>
            <span className={styles.count}>{mine.data.length}</span>
          </div>
          <DatasetErrorBanner
              state={mine}
              scope="المهام المباشرة"
              testId="error-mine"
              retryLabel="إعادة محاولة المهام المباشرة"
              onRetry={() => void loadMine()}
            />

          {!mine.loading && !mine.error && mine.data.length === 0 ? (
            <WorkflowEmptyState
              title="لا توجد مهام مباشرة."
              description="أي مهمة تُسند إليك مباشرة ستظهر هنا مع موعدها وأولويتها."
            />
          ) : (
            mine.data.map((workItem) => (
              <WorkItemCard
                key={workItem.id}
                workItem={workItem}
                busy={actioningId === workItem.id}
                onComplete={() => void complete(workItem)}
                onRelease={() => void release(workItem)}
              />
            ))
          )}
        </section>

        <section className={styles.subsection} aria-labelledby="pool-tasks-title">
          <div className={styles.subsectionTitleRow}>
            <h3 id="pool-tasks-title" className={styles.subsectionTitle}>تجمع المهام</h3>
            <span className={styles.count}>{pool.data.length}</span>
          </div>
          <DatasetErrorBanner
              state={pool}
              scope="تجمع المهام"
              testId="error-pool"
              retryLabel="إعادة محاولة تجمع المهام"
              onRetry={() => void loadPool()}
            />

          {!pool.loading && !pool.error && pool.data.length === 0 ? (
            <WorkflowEmptyState
              title="لا توجد مهام متاحة في التجمع."
              description="المهام المشتركة المؤهلة لك ستظهر هنا ويمكن استلامها بشكل ذري من الخادم."
            />
          ) : (
            pool.data.map((workItem) => (
              <WorkItemCard
                key={workItem.id}
                workItem={workItem}
                busy={actioningId === workItem.id}
                onClaim={() => void claim(workItem)}
              />
            ))
          )}
        </section>
      </div>
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
    <article className={styles.taskCard}>
      <div className={styles.cardHeader}>
        <div>
          <h4 className={styles.cardTitle}>{workItem.title}</h4>
          <div className={styles.cardMeta}>
            <StatusBadge value={workItem.status} />
            <span>الأولوية: {formatPriority(workItem.priority)}</span>
            <span>مرجع المزامنة #{workItem.version}</span>
          </div>
        </div>
        <span className={styles.mono}>{workItem.id.slice(0, 8)}…</span>
      </div>

      <div className={styles.cardMeta}>
        <span>الاستحقاق: {formatWorkflowDate(workItem.dueAt)}</span>
        <span>النمط: {workItem.assignmentMode === "WORK_POOL" ? "تجمع عمل" : "إسناد مباشر"}</span>
      </div>

      {workItem.status === "ASSIGNEE_UNAVAILABLE" && (
        <div className={`${styles.alert} ${styles.alertWarning}`}>
          غير متاحة — يلزم إعادة تعيين مصرّح بها
        </div>
      )}

      <div className={styles.cardActions}>
        {onClaim && <button type="button" disabled={busy} onClick={onClaim}>استلام</button>}
        {onComplete && <button type="button" disabled={busy} onClick={onComplete}>إكمال</button>}
        {onRelease && <button type="button" disabled={busy} onClick={onRelease}>إعادة إلى التجمع</button>}
      </div>
    </article>
  );
}
