"use client";

import { useState } from "react";
import type {
  CreateWorkflowStepRequest,
  CreateWorkflowTransitionRequest,
  WorkflowStepResponse,
  WorkflowStepType,
} from "@/lib/api/workflow-api";
import {
  AssignmentRuleEditor,
  type WorkflowAssignmentRuleDraft,
} from "./assignment-rule-editor";
import {
  ExpressionRuleEditor,
  type WorkflowConditionAst,
} from "./expression-rule-editor";

export interface DesignerStepDraft {
  localId: string;
  stepKey: string;
  name: string;
  stepType: WorkflowStepType;
  sequenceOrder: number;
  assignmentRule: WorkflowAssignmentRuleDraft;
  approvalPolicy: "ANY_ONE" | "ALL";
  selfApproval: "DENY" | "ALLOW";
  slaMode: "NONE" | "HOURS";
  slaHours: number;
  requiredCapability: string;
  requiredRole: string;
}

const Y2_TYPES: WorkflowStepType[] = [
  "START",
  "HUMAN_TASK",
  "APPROVAL",
  "CONDITION",
  "SYSTEM_ACTION",
  "PARALLEL_FORK",
  "PARALLEL_JOIN",
  "CALL_WORKFLOW",
  "NOTIFICATION",
  "END",
];

const OUTCOMES = ["SUCCESS", "APPROVE", "REJECT", "TRUE", "FALSE", "COMPLETE", "TIMEOUT"];

/** R0.G6 — outcome vocabulary is filtered by the source step type so a
 * CONDITION can only offer TRUE/FALSE and an APPROVAL only APPROVE/REJECT;
 * TIMEOUT is a first-class outcome for deadline routing (TIMEOUT != SUCCESS). */
function outcomeOptionsFor(stepType: WorkflowStepType | string | undefined): string[] {
  switch (stepType) {
    case "CONDITION":
      return ["TRUE", "FALSE"];
    case "APPROVAL":
      return ["APPROVE", "REJECT"];
    case "START":
    case "END":
      return ["SUCCESS"];
    default:
      return OUTCOMES;
  }
}

export function StepInspector({
  draft,
  selectedStep,
  steps,
  editable,
  busy,
  onDraftChange,
  onSaveDraft,
  onCreateTransition,
}: {
  draft: DesignerStepDraft | null;
  selectedStep: WorkflowStepResponse | null;
  steps: WorkflowStepResponse[];
  editable: boolean;
  busy: boolean;
  onDraftChange: (draft: DesignerStepDraft) => void;
  onSaveDraft: (request: CreateWorkflowStepRequest) => Promise<void>;
  onCreateTransition: (request: CreateWorkflowTransitionRequest) => Promise<void>;
}) {
  const [fromStepId, setFromStepId] = useState("");
  const [toStepId, setToStepId] = useState("");
  const [transitionKey, setTransitionKey] = useState("");
  const [outcome, setOutcome] = useState("SUCCESS");
  const [priority, setPriority] = useState(0);
  const [conditionAst, setConditionAst] = useState<WorkflowConditionAst>({
    type: "AND",
    conditions: [],
  });
  const [localError, setLocalError] = useState<string | null>(null);

  const saveDraft = async () => {
    if (!draft) return;
    if (!draft.stepKey.trim() || !draft.name.trim()) {
      setLocalError("مفتاح الخطوة واسمها مطلوبان");
      return;
    }
    setLocalError(null);
    const configuration = JSON.stringify({
      assignmentRule: draft.assignmentRule.target.trim() ? draft.assignmentRule : null,
      approvalPolicy: draft.approvalPolicy,
      selfApproval: draft.selfApproval,
      slaMode: draft.slaMode,
    });
    await onSaveDraft({
      stepKey: draft.stepKey.trim(),
      name: draft.name.trim(),
      stepType: draft.stepType,
      sequenceOrder: draft.sequenceOrder,
      configuration,
      slaHours: draft.slaMode === "HOURS" ? Math.max(0, draft.slaHours) : 0,
      requiredCapability: draft.requiredCapability.trim(),
      requiredRole: draft.requiredRole.trim(),
    });
  };

  const createTransition = async () => {
    if (!fromStepId || !toStepId || fromStepId === toStepId || !transitionKey.trim()) {
      setLocalError("اختر خطوتين مختلفتين وأدخل مفتاح الانتقال");
      return;
    }
    setLocalError(null);
    await onCreateTransition({
      fromStepId,
      toStepId,
      transitionKey: transitionKey.trim(),
      outcome,
      conditionAst: JSON.stringify(conditionAst),
      priority,
      metadata: "{}",
    });
    setTransitionKey("");
    setConditionAst({ type: "AND", conditions: [] });
  };

  return (
    <aside aria-label="مفتش سير العمل" style={panelStyle}>
      <h3 style={{ marginTop: 0, fontSize: 16 }}>المفتش</h3>
      {localError && <p role="alert" style={{ color: "var(--snad-color-error)" }}>{localError}</p>}

      {draft && editable ? (
        <section style={{ display: "grid", gap: 10 }}>
          <strong>خطوة جديدة غير محفوظة</strong>
          <label style={fieldStyle}>
            <span>نوع الخطوة</span>
            <select
              value={draft.stepType}
              onChange={(event) => onDraftChange({ ...draft, stepType: event.target.value as WorkflowStepType })}
            >
              {Y2_TYPES.map((type) => <option key={type}>{type}</option>)}
            </select>
          </label>
          <label style={fieldStyle}>
            <span>مفتاح الخطوة</span>
            <input value={draft.stepKey} onChange={(event) => onDraftChange({ ...draft, stepKey: event.target.value })} />
          </label>
          <label style={fieldStyle}>
            <span>الاسم</span>
            <input value={draft.name} onChange={(event) => onDraftChange({ ...draft, name: event.target.value })} />
          </label>
          <label style={fieldStyle}>
            <span>الترتيب</span>
            <input
              type="number"
              min={0}
              value={draft.sequenceOrder}
              onChange={(event) => onDraftChange({ ...draft, sequenceOrder: Number(event.target.value) })}
            />
          </label>

          {(draft.stepType === "HUMAN_TASK" || draft.stepType === "APPROVAL") && (
            <AssignmentRuleEditor
              value={draft.assignmentRule}
              onChange={(assignmentRule) => onDraftChange({ ...draft, assignmentRule })}
            />
          )}

          {draft.stepType === "APPROVAL" && (
            <fieldset style={fieldsetStyle}>
              <legend>سياسة الموافقة</legend>
              <label style={fieldStyle}>
                <span>approvalPolicy</span>
                <select
                  value={draft.approvalPolicy}
                  onChange={(event) => onDraftChange({ ...draft, approvalPolicy: event.target.value as "ANY_ONE" | "ALL" })}
                >
                  <option value="ANY_ONE">ANY_ONE — موافقة واحدة تكفي</option>
                  <option value="ALL">ALL — موافقة الجميع</option>
                </select>
              </label>
              <label style={fieldStyle}>
                <span>الموافقة الذاتية</span>
                <select
                  value={draft.selfApproval}
                  onChange={(event) => onDraftChange({ ...draft, selfApproval: event.target.value as "DENY" | "ALLOW" })}
                >
                  <option value="DENY">DENY — ممنوعة</option>
                  <option value="ALLOW">ALLOW — مسموحة</option>
                </select>
              </label>
            </fieldset>
          )}

          <fieldset style={fieldsetStyle}>
            <legend>SLA</legend>
            <label style={fieldStyle}>
              <span>slaMode</span>
              <select
                value={draft.slaMode}
                onChange={(event) => onDraftChange({ ...draft, slaMode: event.target.value as "NONE" | "HOURS" })}
              >
                <option value="NONE">NONE</option>
                <option value="HOURS">HOURS</option>
              </select>
            </label>
            {draft.slaMode === "HOURS" && (
              <input
                aria-label="ساعات SLA"
                type="number"
                min={0}
                value={draft.slaHours}
                onChange={(event) => onDraftChange({ ...draft, slaHours: Number(event.target.value) })}
              />
            )}
          </fieldset>

          <label style={fieldStyle}>
            <span>القدرة المطلوبة</span>
            <input value={draft.requiredCapability} onChange={(event) => onDraftChange({ ...draft, requiredCapability: event.target.value })} />
          </label>
          <label style={fieldStyle}>
            <span>الدور المطلوب</span>
            <input value={draft.requiredRole} onChange={(event) => onDraftChange({ ...draft, requiredRole: event.target.value })} />
          </label>
          <button type="button" disabled={busy} onClick={() => void saveDraft()}>حفظ الخطوة في الخادم</button>
        </section>
      ) : selectedStep ? (
        <section aria-label="تفاصيل الخطوة المحفوظة" style={{ display: "grid", gap: 6 }}>
          <strong>{selectedStep.name}</strong>
          <span>{selectedStep.stepKey}</span>
          <span>{selectedStep.stepType}</span>
          <span>مرجع المزامنة #{selectedStep.version}</span>
          <small style={{ color: "var(--snad-color-text-secondary)" }}>
            الخطوة المحفوظة للقراءة هنا؛ لا توجد API لتعديلها أو حذفها ضمن عقد Task 18.
          </small>
        </section>
      ) : (
        <p style={hintStyle}>اختر عقدة محفوظة لعرضها، أو أضف خطوة جديدة من المكتبة.</p>
      )}

      <hr style={{ margin: "16px 0", border: 0, borderTop: "1px solid var(--snad-color-border-default)" }} />

      <section style={{ display: "grid", gap: 9 }}>
        <strong>انتقال جديد</strong>
        {!editable && <small style={hintStyle}>الإصدار المنشور للقراءة فقط.</small>}
        <label style={fieldStyle}>
          <span>من</span>
          <select disabled={!editable || busy} value={fromStepId} onChange={(event) => setFromStepId(event.target.value)}>
            <option value="">اختر</option>
            {steps.map((step) => <option key={step.id} value={step.id}>{step.stepKey}</option>)}
          </select>
        </label>
        <label style={fieldStyle}>
          <span>إلى</span>
          <select disabled={!editable || busy} value={toStepId} onChange={(event) => setToStepId(event.target.value)}>
            <option value="">اختر</option>
            {steps.map((step) => <option key={step.id} value={step.id}>{step.stepKey}</option>)}
          </select>
        </label>
        <label style={fieldStyle}>
          <span>مفتاح الانتقال</span>
          <input disabled={!editable || busy} value={transitionKey} onChange={(event) => setTransitionKey(event.target.value)} />
        </label>
        <label style={fieldStyle}>
          <span>outcome</span>
          <select disabled={!editable || busy} value={outcome} onChange={(event) => setOutcome(event.target.value)}>
            {outcomeOptionsFor(steps.find((step) => step.id === fromStepId)?.stepType)
              .map((item) => <option key={item}>{item}</option>)}
          </select>
        </label>
        <label style={fieldStyle}>
          <span>الأولوية</span>
          <input
            disabled={!editable || busy}
            type="number"
            value={priority}
            onChange={(event) => setPriority(Number(event.target.value))}
          />
        </label>
        <ExpressionRuleEditor value={conditionAst} disabled={!editable || busy} onChange={setConditionAst} />
        <button type="button" disabled={!editable || busy || steps.length < 2} onClick={() => void createTransition()}>
          حفظ الانتقال
        </button>
      </section>
    </aside>
  );
}

const panelStyle = {
  border: "1px solid var(--snad-color-border-default)",
  borderRadius: 10,
  padding: 12,
  minWidth: 240,
};

const fieldsetStyle = {
  display: "grid",
  gap: 8,
  border: "1px solid var(--snad-color-border-default)",
  borderRadius: 8,
  padding: 10,
};

const fieldStyle = { display: "grid", gap: 4, fontSize: 13 };
const hintStyle = { color: "var(--snad-color-text-secondary)", fontSize: 12, margin: 0 };
