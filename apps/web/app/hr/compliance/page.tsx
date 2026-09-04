"use client";

/**
 * Compliance workspace — WS5 Task 10 (/hr/compliance).
 *
 * Shows canonical override requests (HRM.COMPLIANCE_OVERRIDE.REQUEST) and
 * the per-employment compliance context (HRM.EMPLOYEE.VIEW). Decision
 * actions (approve/reject/revoke) render ONLY with
 * HRM.COMPLIANCE_OVERRIDE.APPROVE — and remain four-eyes enforced by the
 * backend (the requester can never approve, whatever the UI shows).
 *
 * Hard statutory blocks: the UI offers no bypass. The controlled-exception
 * request path is the only override surface; the backend re-validates the
 * underlying rule (HARD rules are rejected server-side) so the UI cannot
 * weaken compliance by construction.
 */

import { useCallback, useEffect, useState } from "react";
import { AuthLoadingState } from "@/components/auth/auth-loading-state";
import { useAuth } from "@/lib/auth/auth-provider";
import {
  hrmV2Api,
  newIdempotencyKey,
  type OverrideRequestResponse,
} from "@/lib/api/hr-v2-api";
import { HRM_CAPABILITIES } from "@/lib/auth/capabilities";
import { HrWorkspace } from "../components/hr-workspace";
import { HrEmptyState, HrErrorState, HrLoading, hrmErrorMessage } from "../components/hr-feedback";
import { HrCommandDialog } from "../components/hr-command-dialog";
import { formatArabicDate } from "../hr-labels";
import styles from "../hr.module.css";

const OVERRIDE_STATUS_AR: Record<string, string> = {
  PENDING: "قيد المراجعة",
  APPROVED: "معتمد",
  REJECTED: "مرفوض",
  REVOKED: "ملغى",
  EXECUTED: "منفّذ",
};

function todayIso(): string {
  return new Date().toISOString().slice(0, 10);
}

export default function CompliancePage() {
  const { state, me } = useAuth();
  const capabilities = me?.capabilities ?? [];
  const canRequest = capabilities.includes(HRM_CAPABILITIES.COMPLIANCE_OVERRIDE_REQUEST);
  const canApprove = capabilities.includes(HRM_CAPABILITIES.COMPLIANCE_OVERRIDE_APPROVE);

  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<unknown>(null);
  const [overrides, setOverrides] = useState<OverrideRequestResponse[]>([]);
  const [notice, setNotice] = useState<string | null>(null);

  // Request form state (controlled exception path).
  const [showRequest, setShowRequest] = useState(false);
  const [reqRuleId, setReqRuleId] = useState("");
  const [reqResourceType, setReqResourceType] = useState("EMPLOYMENT");
  const [reqResourceId, setReqResourceId] = useState("");
  const [reqJustification, setReqJustification] = useState("");
  const [reqEvidence, setReqEvidence] = useState("");
  const [reqValidFrom, setReqValidFrom] = useState(todayIso());
  const [reqValidUntil, setReqValidUntil] = useState("");
  const [busy, setBusy] = useState(false);
  const [dialogError, setDialogError] = useState<string | null>(null);

  // Decision dialog state.
  const [decision, setDecision] = useState<{ id: string; kind: "approve" | "reject" | "revoke" } | null>(null);
  const [decisionComment, setDecisionComment] = useState("");

  const load = useCallback(async () => {
    try {
      setOverrides(await hrmV2Api.listComplianceOverrides());
      setError(null);
    } catch (err) {
      setError(err);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    if (state !== "AUTHENTICATED") return;
    const timer = window.setTimeout(() => void load(), 0);
    return () => window.clearTimeout(timer);
  }, [state, load]);

  async function submitRequest() {
    setBusy(true);
    setDialogError(null);
    try {
      await hrmV2Api.requestComplianceOverride(
        {
          complianceRuleId: reqRuleId.trim(),
          resourceType: reqResourceType,
          resourceId: reqResourceId.trim(),
          justification: reqJustification.trim(),
          ...(reqEvidence.trim() ? { evidenceReference: reqEvidence.trim() } : {}),
          validFrom: reqValidFrom || undefined,
          ...(reqValidUntil ? { validUntil: reqValidUntil } : {}),
        },
        newIdempotencyKey(),
      );
      setShowRequest(false);
      setReqRuleId(""); setReqResourceId(""); setReqJustification(""); setReqEvidence("");
      setNotice("تم إرسال طلب التجاوز — بانتظار موافقة مستقل");
      await load();
    } catch (err) {
      setDialogError(hrmErrorMessage(err).message);
    } finally {
      setBusy(false);
    }
  }

  async function submitDecision() {
    if (!decision) return;
    setBusy(true);
    setDialogError(null);
    try {
      await hrmV2Api.decideComplianceOverride(
        decision.id, decision.kind, { comment: decisionComment.trim() }, newIdempotencyKey(),
      );
      setDecision(null);
      setDecisionComment("");
      setNotice(decision.kind === "approve" ? "تم اعتماد التجاوز" : decision.kind === "reject" ? "تم رفض التجاوز" : "تم إلغاء التجاوز");
      await load();
    } catch (err) {
      setDialogError(hrmErrorMessage(err).message);
    } finally {
      setBusy(false);
    }
  }

  if (["INITIALIZING", "CHECKING_SESSION", "REFRESHING"].includes(state))
    return <AuthLoadingState phase="session" />;

  return (
    <HrWorkspace capabilities={capabilities} activeHref="/hr/compliance">
      {notice ? <p role="status" className={styles.notice}>{notice}</p> : null}

      <div className={styles.toolbar}>
        {canRequest ? (
          <button type="button" className={styles.actionButton} onClick={() => { setShowRequest(true); setDialogError(null); }}>
            طلب تجاوز مضبوط
          </button>
        ) : null}
      </div>

      {loading ? (
        <HrLoading />
      ) : error ? (
        <HrErrorState error={error} onRetry={load} />
      ) : overrides.length === 0 ? (
        <HrEmptyState
          title="لا توجد طلبات تجاوز"
          description="لم تُسجَّل طلبات تجاوز التزام في هذا المستأجر."
        />
      ) : (
        <div className={styles.hrTableWrap}>
          <table className={styles.hrTable}>
            <caption>طلبات تجاوز الالتزام</caption>
            <thead>
              <tr>
                <th scope="col">المرجع</th>
                <th scope="col">نوع السجل</th>
                <th scope="col">المبرر</th>
                <th scope="col">من</th>
                <th scope="col">إلى</th>
                <th scope="col">الحالة</th>
                {canApprove ? <th scope="col">قرار</th> : null}
              </tr>
            </thead>
            <tbody>
              {overrides.map((o) => (
                <tr key={o.requestId}>
                  <td title={o.complianceRuleId}>{o.complianceRuleId.slice(0, 8)}…</td>
                  <td>{o.resourceType}</td>
                  <td>{o.justification}</td>
                  <td>{o.validFrom ? formatArabicDate(o.validFrom) : "—"}</td>
                  <td>{o.validUntil ? formatArabicDate(o.validUntil) : "—"}</td>
                  <td>{OVERRIDE_STATUS_AR[o.status] ?? o.status}</td>
                  {canApprove ? (
                    <td>
                      {o.status === "PENDING" ? (
                        <span className={styles.actionRow}>
                          <button type="button" className={styles.linkButton}
                                  onClick={() => { setDecision({ id: o.requestId, kind: "approve" }); setDecisionComment(""); setDialogError(null); }}>
                            اعتماد
                          </button>
                          <button type="button" className={styles.linkButton}
                                  onClick={() => { setDecision({ id: o.requestId, kind: "reject" }); setDecisionComment(""); setDialogError(null); }}>
                            رفض
                          </button>
                        </span>
                      ) : o.status === "APPROVED" ? (
                        <button type="button" className={styles.linkButton}
                                onClick={() => { setDecision({ id: o.requestId, kind: "revoke" }); setDecisionComment(""); setDialogError(null); }}>
                          إلغاء
                        </button>
                      ) : "—"}
                    </td>
                  ) : null}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {canRequest ? (
        <p className={styles.mutedNote}>
          ملاحظة: القواعد القانونية الصارمة لا تقبل التجاوز — أي طلب على قاعدة صارمة سيرفضه النظام.
        </p>
      ) : null}

      {showRequest ? (
        <HrCommandDialog
          title="طلب تجاوز التزام (مسار مضبوط)"
          description="يتطلب مرجع القاعدة من سياسة الالتزام أو رسالة الحجب. الموافقة تتم من موافقٍ مستقل."
          busy={busy}
          error={dialogError}
          onConfirm={submitRequest}
          onCancel={() => setShowRequest(false)}
          fields={[
            { label: "مرجع قاعدة الالتزام (المعرّف)", type: "text", value: reqRuleId, onChange: setReqRuleId, required: true },
            { label: "نوع السجل", type: "text", value: reqResourceType, onChange: setReqResourceType, required: true },
            { label: "معرّف السجل", type: "text", value: reqResourceId, onChange: setReqResourceId, required: true },
            { label: "المبرر", type: "textarea", value: reqJustification, onChange: setReqJustification, required: true },
            { label: "مرجع الأدلة (اختياري)", type: "text", value: reqEvidence, onChange: setReqEvidence },
            { label: "سريان من", type: "date", value: reqValidFrom, onChange: setReqValidFrom },
            { label: "سريان إلى (اختياري)", type: "date", value: reqValidUntil, onChange: setReqValidUntil },
          ]}
        />
      ) : null}

      {decision ? (
        <HrCommandDialog
          title={decision.kind === "approve" ? "اعتماد التجاوز" : decision.kind === "reject" ? "رفض التجاوز" : "إلغاء التجاوز"}
          description="سيُسجَّل القرار مع هويتك في التدقيق. لا يمكن للطالب اعتماد طلبه."
          busy={busy}
          error={dialogError}
          onConfirm={submitDecision}
          onCancel={() => setDecision(null)}
          fields={[
            { label: "تعليق القرار", type: "textarea", value: decisionComment, onChange: setDecisionComment, required: true },
          ]}
        />
      ) : null}
    </HrWorkspace>
  );
}
