"use client";

import { useCallback, useEffect, useState } from "react";
import { AuthLoadingState } from "@/components/auth/auth-loading-state";
import {
  workflowApi,
  type WorkflowApprovalResponse,
} from "@/lib/api/workflow-api";
import { describeWorkflowError } from "@/lib/workflow/error-messages";

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
      setApprovals(await workflowApi.listPendingApprovals(50));
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

  if (loading) return <AuthLoadingState />;

  return (
    <div dir="rtl">
      <h2 style={{ marginTop: 0, fontSize: 20 }}>الموافقات</h2>
      <p style={{ color: "var(--snad-color-text-secondary)" }}>
        القرار النهائي وصلاحية المنفذ يتحقق منهما الخادم؛ الواجهة لا تمنح صلاحيات.
      </p>

      {conflict && <p role="alert" style={{ color: "var(--snad-color-warning)" }}>{conflict}</p>}
      {error && (
        <div role="alert" style={{ display: "flex", alignItems: "center", gap: 12, flexWrap: "wrap", marginBottom: 12 }}>
          <p style={{ color: "var(--snad-color-error)", margin: 0 }}>{error}</p>
          <button type="button" onClick={() => void load()}>إعادة التحميل</button>
        </div>
      )}

      {!error && approvals.length === 0 && <p>لا توجد طلبات موافقة معلّقة.</p>}
      <div style={{ display: "grid", gap: 12 }}>
        {approvals.map((approval) => {
          const busy = actioningId === approval.id;
          return (
            <article
              key={approval.id}
              style={{ border: "1px solid var(--snad-color-border-default)", borderRadius: 10, padding: 14 }}
            >
              <div style={{ display: "flex", justifyContent: "space-between", gap: 12, flexWrap: "wrap" }}>
                <div>
                  <strong>طلب #{approval.id.slice(0, 8)}…</strong>
                  <div style={{ marginTop: 4, fontSize: 12, color: "var(--snad-color-text-secondary)" }}>
                    المثيل {approval.workflowInstanceId.slice(0, 8)}… · {approval.status} · مرجع المزامنة #{approval.version}
                  </div>
                </div>
                <button
                  type="button"
                  disabled={busy}
                  onClick={() => void runDecision(approval, "approve", "تمت الموافقة")}
                >
                  {busy ? "جارٍ التنفيذ…" : "موافقة"}
                </button>
              </div>

              <div style={{ display: "flex", gap: 8, alignItems: "center", flexWrap: "wrap", marginTop: 12 }}>
                <input
                  aria-label={`سبب رفض الطلب ${approval.id}`}
                  placeholder="سبب الرفض (إلزامي)"
                  value={rejectionReasons[approval.id] ?? ""}
                  onChange={(event) =>
                    setRejectionReasons((current) => ({
                      ...current,
                      [approval.id]: event.target.value,
                    }))
                  }
                  style={{ minWidth: 240, flex: "1 1 280px" }}
                />
                <button type="button" disabled={busy} onClick={() => void reject(approval)}>
                  رفض
                </button>
              </div>
            </article>
          );
        })}
      </div>
    </div>
  );
}
