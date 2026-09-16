"use client";

import { useCallback, useEffect, useState } from "react";
import {
  workflowApi,
  type WorkflowApprovalResponse,
} from "@/lib/api/workflow-api";
import { describeWorkflowError } from "@/lib/workflow/error-messages";
import styles from "../workflow.module.css";
import {
  StatusBadge,
  WorkflowEmptyState,
  WorkflowSectionHeader,
} from "./workflow-ui";

export function WorkflowApprovals() {
  const [approvals, setApprovals] = useState<WorkflowApprovalResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [conflict, setConflict] = useState<string | null>(null);
  const [actioningId, setActioningId] = useState<string | null>(null);
  const [rejectionReasons, setRejectionReasons] = useState<Record<string, string>>({});

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setApprovals(await workflowApi.listMyPendingApprovals(50));
    } catch (cause: unknown) {
      setError(describeWorkflowError(cause, "تعذر تحميل طلبات الموافقة"));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const runDecision = async (
    approval: WorkflowApprovalResponse,
    decision: "approve" | "reject",
    comments: string,
  ) => {
    setActioningId(approval.id);
    setError(null);
    setConflict(null);
    try {
      if (decision === "approve") {
        await workflowApi.approveRequest(approval.id, approval.version, comments);
      } else {
        await workflowApi.rejectRequest(approval.id, approval.version, comments);
      }
      await load();
    } catch (cause: unknown) {
      const status = (cause as { status?: number })?.status;
      if (status === 409) {
        setConflict("تم اتخاذ قرار على طلب الموافقة أو تحديثه من مستخدم آخر. أُعيد تحميل النسخة الأحدث.");
        await load();
      } else if (status === 403) {
        setError(describeWorkflowError(cause, "لا تملك صلاحية اتخاذ قرار على هذا الطلب"));
      } else {
        setError(describeWorkflowError(cause, decision === "approve" ? "فشلت الموافقة" : "فشل الرفض"));
      }
    } finally {
      setActioningId(null);
    }
  };

  const reject = async (approval: WorkflowApprovalResponse) => {
    const reason = (rejectionReasons[approval.id] ?? "").trim();
    if (!reason) {
      setError("سبب الرفض مطلوب");
      return;
    }
    await runDecision(approval, "reject", reason);
  };

  return (
    <div dir="rtl">
      <WorkflowSectionHeader
        eyebrow="صندوق القرارات"
        title="الموافقات"
        description="طلبات تنتظر قرارك. يتحقق الخادم من أهلية صاحب القرار وسياسة الموافقة قبل قبول أي إجراء."
        actions={
          <button type="button" onClick={() => void load()} disabled={loading}>
            {loading ? "جارٍ التحديث…" : "تحديث"}
          </button>
        }
      />

      {conflict && (
        <div role="alert" className={`${styles.alert} ${styles.alertWarning}`}>{conflict}</div>
      )}
      {error && (
        <div role="alert" className={`${styles.alert} ${styles.alertError}`}>
          <span>{error}</span>
          <button type="button" onClick={() => void load()}>إعادة التحميل</button>
        </div>
      )}

      {!loading && !error && approvals.length === 0 ? (
        <WorkflowEmptyState
          title="لا توجد موافقات معلّقة"
          description="عند وصول طلب يتطلب قرارك سيظهر هنا مع مرجع المثيل وحالة الطلب."
        />
      ) : (
        <div className={styles.cardsGrid}>
          {approvals.map((approval) => {
            const busy = actioningId === approval.id;
            return (
              <article key={approval.id} className={styles.approvalCard}>
                <div className={styles.cardHeader}>
                  <div>
                    <h3 className={styles.cardTitle}>طلب موافقة</h3>
                    <div className={styles.cardMeta}>
                      <StatusBadge value={approval.status} />
                      <span>مرجع المزامنة #{approval.version}</span>
                    </div>
                  </div>
                  <span className={styles.mono}>#{approval.id.slice(0, 8)}…</span>
                </div>

                <div className={styles.cardMeta}>
                  <span>المثيل: <span className={styles.mono}>{approval.workflowInstanceId.slice(0, 8)}…</span></span>
                  {approval.requestedFromEmployeeId ? (
                    <span>الموظف: <span className={styles.mono}>{approval.requestedFromEmployeeId.slice(0, 8)}…</span></span>
                  ) : null}
                </div>

                <div className={styles.cardActions}>
                  <button
                    type="button"
                    disabled={busy}
                    onClick={() => void runDecision(approval, "approve", "تمت الموافقة")}
                  >
                    {busy ? "جارٍ التنفيذ…" : "موافقة"}
                  </button>
                </div>

                <div className={styles.toolbar}>
                  <input
                    className={styles.toolbarGrow}
                    aria-label={`سبب رفض الطلب ${approval.id}`}
                    placeholder="سبب الرفض (إلزامي)"
                    value={rejectionReasons[approval.id] ?? ""}
                    onChange={(event) =>
                      setRejectionReasons((current) => ({
                        ...current,
                        [approval.id]: event.target.value,
                      }))
                    }
                  />
                  <button type="button" disabled={busy} onClick={() => void reject(approval)}>
                    رفض
                  </button>
                </div>
              </article>
            );
          })}
        </div>
      )}
    </div>
  );
}
