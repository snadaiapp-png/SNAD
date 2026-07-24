"use client";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import {
  cancelCrmWorkflow,
  confirmCrmAiRecommendation,
  dispatchCrmWorkflow,
  getCrmIntegrationStatus,
  getCrmWorkflowStatus,
  rejectCrmAiRecommendation,
  requestCrmAiInsight,
  type CrmIntegrationRequestStatus,
} from "@/lib/api/crm-integration";
import { toUserFacingError } from "@/lib/api/user-facing-errors";
import { useI18n } from "@/lib/i18n/I18nProvider";
import styles from "./crm-integrations.module.css";

const TERMINAL = new Set([
  "COMPLETED",
  "EXECUTED",
  "EXECUTION_REJECTED",
  "REJECTED",
  "POLICY_DENIED",
  "UNSAFE_OUTPUT",
  "TIMED_OUT",
  "UNAVAILABLE",
  "CANCELLED",
  "EXPIRED",
]);

type DialogAction = "confirm" | "reject" | null;

const copy = {
  en: {
    title: "Workflow & AI integrations",
    description: "Governed CRM recommendations and workflows with human control.",
    entityType: "Entity type",
    entityId: "Entity ID",
    version: "Entity version",
    aiCapability: "AI capability",
    workflowType: "Workflow type",
    requestAi: "Request AI insight",
    dispatchWorkflow: "Dispatch workflow",
    refresh: "Refresh status",
    confirm: "Confirm recommendation",
    reject: "Reject recommendation",
    cancel: "Cancel workflow",
    close: "Close",
    confirmPrompt: "Execute this recommendation?",
    rejectPrompt: "Reject this recommendation?",
    rejectionReason: "Rejection reason",
    customerSummary: "Customer Summary",
    nextBestAction: "Next Best Action",
    scoring: "Scoring Explanation",
    execution: "Execution Status",
    workflow: "Workflow Status",
    timeline: "Approval Timeline",
    reminder: "Reminder / Escalation Status",
    evidence: "Evidence References",
    noData: "No integration result yet.",
    requestId: "Request ID",
    correlationId: "Correlation ID",
    status: "Status",
    error: "Error",
    pending: "Submitting…",
  },
  ar: {
    title: "تكاملات سير العمل والذكاء الاصطناعي",
    description: "توصيات وسير عمل CRM محكومة مع إبقاء القرار البشري.",
    entityType: "نوع الكيان",
    entityId: "معرّف الكيان",
    version: "إصدار الكيان",
    aiCapability: "قدرة الذكاء الاصطناعي",
    workflowType: "نوع سير العمل",
    requestAi: "طلب تحليل ذكي",
    dispatchWorkflow: "إرسال سير العمل",
    refresh: "تحديث الحالة",
    confirm: "تأكيد التوصية",
    reject: "رفض التوصية",
    cancel: "إلغاء سير العمل",
    close: "إغلاق",
    confirmPrompt: "هل تريد تنفيذ هذه التوصية؟",
    rejectPrompt: "هل تريد رفض هذه التوصية؟",
    rejectionReason: "سبب الرفض",
    customerSummary: "ملخص العميل",
    nextBestAction: "أفضل إجراء تالٍ",
    scoring: "تفسير التقييم",
    execution: "حالة التنفيذ",
    workflow: "حالة سير العمل",
    timeline: "الخط الزمني للموافقة",
    reminder: "حالة التذكير / التصعيد",
    evidence: "مراجع الأدلة",
    noData: "لا توجد نتيجة تكامل حتى الآن.",
    requestId: "معرّف الطلب",
    correlationId: "معرّف الارتباط",
    status: "الحالة",
    error: "الخطأ",
    pending: "جارٍ الإرسال…",
  },
} as const;

function valueAt(payload: Record<string, unknown> | null | undefined, key: string): unknown {
  return payload?.[key];
}

function display(value: unknown): string {
  if (value === null || value === undefined || value === "") return "—";
  if (typeof value === "string" || typeof value === "number" || typeof value === "boolean") {
    return String(value);
  }
  return JSON.stringify(value, null, 2);
}

export default function CrmIntegrationsPage() {
  const { locale, direction } = useI18n();
  const text = locale === "ar" ? copy.ar : copy.en;
  const [entityType, setEntityType] = useState("ACCOUNT");
  const [entityId, setEntityId] = useState("");
  const [entityVersion, setEntityVersion] = useState("0");
  const [capability, setCapability] = useState<"CUSTOMER_SUMMARY" | "NEXT_BEST_ACTION" | "SCORING">("CUSTOMER_SUMMARY");
  const [workflowType, setWorkflowType] = useState<"ASSIGNMENT" | "OPPORTUNITY_APPROVAL" | "REMINDER" | "ESCALATION">("ASSIGNMENT");
  const [request, setRequest] = useState<CrmIntegrationRequestStatus | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [dialog, setDialog] = useState<DialogAction>(null);
  const [reason, setReason] = useState("");
  const dialogHeading = useRef<HTMLHeadingElement>(null);

  const validEntity = entityId.trim().length > 0 && Number(entityVersion) >= 0;
  const actionable = request?.status === "RECOMMENDATION_AVAILABLE";
  const cancellable = request?.integrationType === "WORKFLOW" && ["ACCEPTED", "RUNNING"].includes(request.status);

  const refresh = useCallback(async () => {
    if (!request) return;
    try {
      const next = request.integrationType === "WORKFLOW"
        ? await getCrmWorkflowStatus(request.id)
        : await getCrmIntegrationStatus(request.id);
      setRequest(next);
      setError("");
    } catch (cause) {
      setError(toUserFacingError(cause).message);
    }
  }, [request]);

  useEffect(() => {
    if (!request || TERMINAL.has(request.status)) return;
    const timer = window.setInterval(() => void refresh(), 2500);
    return () => window.clearInterval(timer);
  }, [request, refresh]);

  useEffect(() => {
    if (dialog) dialogHeading.current?.focus();
  }, [dialog]);

  async function submitAi() {
    if (!validEntity) return;
    setBusy(true);
    setError("");
    try {
      const next = await requestCrmAiInsight(
        {
          capability,
          sourceEntityType: entityType,
          sourceEntityId: entityId.trim(),
          sourceEntityVersion: Number(entityVersion),
        },
        crypto.randomUUID(),
      );
      setRequest(next);
    } catch (cause) {
      setError(toUserFacingError(cause).message);
    } finally {
      setBusy(false);
    }
  }

  async function submitWorkflow() {
    if (!validEntity) return;
    setBusy(true);
    setError("");
    try {
      const next = await dispatchCrmWorkflow(
        {
          workflowType,
          sourceEntityType: entityType,
          sourceEntityId: entityId.trim(),
          sourceEntityVersion: Number(entityVersion),
          payload: { requestedFrom: "crm-integration-workspace" },
        },
        crypto.randomUUID(),
      );
      setRequest(next);
    } catch (cause) {
      setError(toUserFacingError(cause).message);
    } finally {
      setBusy(false);
    }
  }

  async function applyDecision(action: Exclude<DialogAction, null>) {
    if (!request) return;
    setBusy(true);
    setError("");
    try {
      const etag = `"${request.version}"`;
      const next = action === "confirm"
        ? await confirmCrmAiRecommendation(
            request.id,
            crypto.randomUUID(),
            etag,
            request.sourceEntityVersion ?? Number(entityVersion),
          )
        : await rejectCrmAiRecommendation(
            request.id,
            crypto.randomUUID(),
            etag,
            reason || undefined,
          );
      setRequest(next);
      setDialog(null);
      setReason("");
    } catch (cause) {
      setError(toUserFacingError(cause).message);
    } finally {
      setBusy(false);
    }
  }

  async function cancelWorkflow() {
    if (!request) return;
    setBusy(true);
    setError("");
    try {
      setRequest(await cancelCrmWorkflow(request.id, `"${request.version}"`, "Cancelled from CRM workspace"));
    } catch (cause) {
      setError(toUserFacingError(cause).message);
    } finally {
      setBusy(false);
    }
  }

  const result = request?.resultPayload;
  const references = useMemo(() => {
    const candidate = valueAt(result, "sourceReferences") ?? valueAt(result, "references");
    return Array.isArray(candidate) ? candidate.map(String) : [];
  }, [result]);

  const timeline = useMemo(() => {
    if (!request) return [];
    const items = [{ label: "REQUESTED", at: request.requestedAt }];
    if (request.status !== "PENDING") items.push({ label: request.status, at: new Date().toISOString() });
    return items;
  }, [request]);

  return (
    <div className={styles.page} dir={direction}>
      <header className={styles.header}>
        <div>
          <h1>{text.title}</h1>
          <p>{text.description}</p>
        </div>
        <button type="button" onClick={() => void refresh()} disabled={!request || busy}>
          {text.refresh}
        </button>
      </header>

      {error ? <div className={styles.error} role="alert">{error}</div> : null}

      <section className={styles.controlPanel} aria-label={text.title}>
        <label>{text.entityType}<select value={entityType} onChange={(event) => setEntityType(event.target.value)}>
          <option>ACCOUNT</option><option>CONTACT</option><option>LEAD</option><option>OPPORTUNITY</option>
        </select></label>
        <label>{text.entityId}<input value={entityId} onChange={(event) => setEntityId(event.target.value)} required /></label>
        <label>{text.version}<input type="number" min="0" value={entityVersion} onChange={(event) => setEntityVersion(event.target.value)} /></label>
        <label>{text.aiCapability}<select value={capability} onChange={(event) => setCapability(event.target.value as typeof capability)}>
          <option value="CUSTOMER_SUMMARY">CUSTOMER_SUMMARY</option>
          <option value="NEXT_BEST_ACTION">NEXT_BEST_ACTION</option>
          <option value="SCORING">SCORING</option>
        </select></label>
        <button type="button" disabled={!validEntity || busy} onClick={() => void submitAi()}>{busy ? text.pending : text.requestAi}</button>
        <label>{text.workflowType}<select value={workflowType} onChange={(event) => setWorkflowType(event.target.value as typeof workflowType)}>
          <option value="ASSIGNMENT">ASSIGNMENT</option>
          <option value="OPPORTUNITY_APPROVAL">OPPORTUNITY_APPROVAL</option>
          <option value="REMINDER">REMINDER</option>
          <option value="ESCALATION">ESCALATION</option>
        </select></label>
        <button type="button" disabled={!validEntity || busy} onClick={() => void submitWorkflow()}>{busy ? text.pending : text.dispatchWorkflow}</button>
      </section>

      <section className={styles.actions} aria-label={text.execution}>
        {actionable ? <>
          <button type="button" onClick={() => setDialog("confirm")}>{text.confirm}</button>
          <button type="button" className={styles.secondary} onClick={() => setDialog("reject")}>{text.reject}</button>
        </> : null}
        {cancellable ? <button type="button" className={styles.secondary} onClick={() => void cancelWorkflow()}>{text.cancel}</button> : null}
      </section>

      <div className={styles.grid} aria-live="polite">
        <Panel title={text.customerSummary} value={valueAt(result, "generatedText") ?? valueAt(result, "summary")} empty={text.noData} />
        <Panel title={text.nextBestAction} value={valueAt(result, "actionCode") ?? valueAt(result, "nextBestAction")} empty={text.noData} />
        <Panel title={text.scoring} value={{ confidence: valueAt(result, "confidence"), explanation: valueAt(result, "explanation") }} empty={text.noData} />
        <Panel title={text.execution} value={request ? { status: request.status, requestId: request.id, errorCode: request.errorCode } : null} empty={text.noData} />
        <Panel title={text.workflow} value={request?.integrationType === "WORKFLOW" ? { status: request.status, workflowRunId: request.externalReference } : null} empty={text.noData} />
        <article className={styles.panel}><h2>{text.timeline}</h2>{timeline.length ? <ol className={styles.timeline}>{timeline.map((item, index) => <li key={`${item.label}-${index}`}><strong>{item.label}</strong><time>{new Date(item.at).toLocaleString(locale)}</time></li>)}</ol> : <p>{text.noData}</p>}</article>
        <Panel title={text.reminder} value={request?.integrationType === "WORKFLOW" ? { workflowType: valueAt(request.payload, "workflowType"), status: request.status } : null} empty={text.noData} />
        <article className={styles.panel}><h2>{text.evidence}</h2>{references.length ? <ul>{references.map((reference) => <li key={reference}>{reference}</li>)}</ul> : <p>{text.noData}</p>}</article>
      </div>

      {request ? <footer className={styles.metadata}>
        <span>{text.requestId}: <code>{request.id}</code></span>
        <span>{text.correlationId}: <code>{request.correlationId}</code></span>
        <span>{text.status}: <strong>{request.status}</strong></span>
        {request.errorCode ? <span>{text.error}: <strong>{request.errorCode}</strong></span> : null}
      </footer> : null}

      {dialog ? <div className={styles.backdrop} role="presentation" onMouseDown={() => setDialog(null)}>
        <section className={styles.dialog} role="dialog" aria-modal="true" aria-labelledby="decision-dialog-title" onMouseDown={(event) => event.stopPropagation()} onKeyDown={(event) => { if (event.key === "Escape") setDialog(null); }}>
          <h2 id="decision-dialog-title" ref={dialogHeading} tabIndex={-1}>{dialog === "confirm" ? text.confirmPrompt : text.rejectPrompt}</h2>
          {dialog === "reject" ? <label>{text.rejectionReason}<textarea value={reason} onChange={(event) => setReason(event.target.value)} /></label> : null}
          <div className={styles.dialogActions}>
            <button type="button" onClick={() => void applyDecision(dialog)} disabled={busy}>{dialog === "confirm" ? text.confirm : text.reject}</button>
            <button type="button" className={styles.secondary} onClick={() => setDialog(null)}>{text.close}</button>
          </div>
        </section>
      </div> : null}
    </div>
  );
}

function Panel({ title, value, empty }: { title: string; value: unknown; empty: string }) {
  const missing = value === null || value === undefined || (typeof value === "object" && Object.values(value as object).every((item) => item === null || item === undefined));
  return <article className={styles.panel}><h2>{title}</h2>{missing ? <p>{empty}</p> : <pre>{display(value)}</pre>}</article>;
}
