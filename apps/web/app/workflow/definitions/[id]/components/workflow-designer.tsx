"use client";

import { useCallback, useEffect, useMemo, useState, type CSSProperties, type DragEvent } from "react";
import { useRouter } from "next/navigation";
import {
  workflowApi,
  type CreateWorkflowStepRequest,
  type CreateWorkflowTransitionRequest,
  type WorkflowDefinitionResponse,
  type WorkflowStepResponse,
  type WorkflowStepType,
  type WorkflowTransitionResponse,
} from "@/lib/api/workflow-api";
import { describeWorkflowError } from "@/lib/workflow/error-messages";
import { StepPalette } from "./step-palette";
import { StepInspector, type DesignerStepDraft } from "./step-inspector";
import { PublishPanel } from "./publish-panel";

interface NodePosition { x: number; y: number }

export function WorkflowDesigner({ definitionId }: { definitionId: string }) {
  const router = useRouter();
  const [definition, setDefinition] = useState<WorkflowDefinitionResponse | null>(null);
  const [steps, setSteps] = useState<WorkflowStepResponse[]>([]);
  const [transitions, setTransitions] = useState<WorkflowTransitionResponse[]>([]);
  const [draft, setDraft] = useState<DesignerStepDraft | null>(null);
  const [selectedStepId, setSelectedStepId] = useState<string | null>(null);
  const [positions, setPositions] = useState<Record<string, NodePosition>>({});
  const [draggedStepId, setDraggedStepId] = useState<string | null>(null);
  const [view, setView] = useState<"canvas" | "table">("canvas");
  const [graphRevision, setGraphRevision] = useState(0);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [conflict, setConflict] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const [nextDefinition, nextSteps, nextTransitions] = await Promise.all([
        workflowApi.getDefinition(definitionId),
        workflowApi.getDefinitionSteps(definitionId),
        workflowApi.getDefinitionTransitions(definitionId),
      ]);
      setDefinition(nextDefinition);
      setSteps(nextSteps);
      setTransitions(nextTransitions);
      setPositions((current) => buildPositions(nextSteps, current));
      if (selectedStepId && !nextSteps.some((step) => step.id === selectedStepId)) {
        setSelectedStepId(null);
      }
    } catch (cause: unknown) {
      setError(describeWorkflowError(cause, "تعذر تحميل مصمم سير العمل"));
    } finally {
      setLoading(false);
    }
  }, [definitionId, selectedStepId]);

  useEffect(() => {
    void load();
  }, [load]);

  const editable = definition?.publicationState === "DRAFT";
  const selectedStep = useMemo(
    () => steps.find((step) => step.id === selectedStepId) ?? null,
    [selectedStepId, steps],
  );

  const addLocalDraft = (stepType: WorkflowStepType) => {
    if (!editable || draft) return;
    const ordinal = steps.length + 1;
    setSelectedStepId(null);
    setDraft({
      localId: `local-${Date.now()}`,
      stepKey: `${stepType.toLowerCase()}_${ordinal}`,
      name: defaultName(stepType),
      stepType,
      sequenceOrder: ordinal,
      assignmentRule: { type: "EMPLOYEE", target: "" },
      approvalPolicy: "ANY_ONE",
      selfApproval: "DENY",
      slaMode: "NONE",
      slaHours: 0,
      requiredCapability: "",
      requiredRole: "",
    });
  };

  const handleMutationFailure = async (cause: unknown, fallback: string) => {
    const status = (cause as { status?: number })?.status;
    if (status === 409) {
      setConflict("تغير الرسم بالتزامن. أُعيد تحميل النسخة الأحدث وأُلغي اعتماد أي تحقق سابق.");
      setGraphRevision((value) => value + 1);
      await load();
      return;
    }
    setError(describeWorkflowError(cause, fallback));
  };

  const saveStep = async (request: CreateWorkflowStepRequest) => {
    if (!editable) return;
    setBusy(true);
    setError(null);
    setConflict(null);
    try {
      const saved = await workflowApi.addDefinitionStep(definitionId, request);
      setDraft(null);
      setSelectedStepId(saved.id);
      setGraphRevision((value) => value + 1);
      await load();
    } catch (cause: unknown) {
      await handleMutationFailure(cause, "فشل حفظ خطوة سير العمل");
    } finally {
      setBusy(false);
    }
  };

  const createTransition = async (request: CreateWorkflowTransitionRequest) => {
    if (!editable) return;
    setBusy(true);
    setError(null);
    setConflict(null);
    try {
      await workflowApi.createDefinitionTransition(definitionId, request);
      setGraphRevision((value) => value + 1);
      const nextTransitions = await workflowApi.getDefinitionTransitions(definitionId);
      setTransitions(nextTransitions);
    } catch (cause: unknown) {
      await handleMutationFailure(cause, "فشل حفظ انتقال سير العمل");
    } finally {
      setBusy(false);
    }
  };

  const createNextDraft = async () => {
    if (!definition || definition.publicationState !== "PUBLISHED") return;
    setBusy(true);
    setError(null);
    try {
      const next = await workflowApi.createNextDraft(definition.id);
      router.push(`/workflow/definitions/${next.id}`);
    } catch (cause: unknown) {
      setError(describeWorkflowError(cause, "فشل إنشاء المسودة التالية"));
    } finally {
      setBusy(false);
    }
  };

  const dropNode = (event: DragEvent<HTMLDivElement>) => {
    if (!editable || !draggedStepId) return;
    event.preventDefault();
    const rect = event.currentTarget.getBoundingClientRect();
    const x = Math.max(8, Math.min(rect.width - 150, event.clientX - rect.left - 70));
    const y = Math.max(8, Math.min(rect.height - 64, event.clientY - rect.top - 28));
    setPositions((current) => ({ ...current, [draggedStepId]: { x, y } }));
    setDraggedStepId(null);
  };

  if (loading && !definition) return <p dir="rtl">جارٍ تحميل المصمم…</p>;
  if (!definition) {
    return (
      <div dir="rtl">
        {error && <p role="alert" style={{ color: "var(--snad-color-error)" }}>{error}</p>}
        <button type="button" onClick={() => void load()}>إعادة المحاولة</button>
      </div>
    );
  }

  const published = definition.publicationState === "PUBLISHED";
  const retired = definition.publicationState === "RETIRED";

  return (
    <div dir="rtl">
      <header style={{ display: "flex", justifyContent: "space-between", gap: 12, alignItems: "center", flexWrap: "wrap", marginBottom: 16 }}>
        <div>
          <div style={{ display: "flex", gap: 8, alignItems: "center", flexWrap: "wrap" }}>
            <h1 style={{ margin: 0, fontSize: 24 }}>{definition.name}</h1>
            <span style={badgeStyle}>
              {published ? "منشور" : retired ? "متقاعد" : "مسودة"}
            </span>
            <span style={badgeStyle}>v{definition.version}</span>
          </div>
          <p style={{ margin: "5px 0 0", color: "var(--snad-color-text-secondary)" }}>
            {definition.code} · {definition.engineGeneration} · مواضع العقد في اللوحة محلية للعرض، أما البنية فتحفظ في الخادم.
          </p>
        </div>
        {published && (
          <button type="button" disabled={busy} onClick={() => void createNextDraft()}>
            إنشاء مسودة جديدة
          </button>
        )}
      </header>

      {conflict && <p role="alert" style={{ color: "var(--snad-color-warning)" }}>{conflict}</p>}
      {error && <p role="alert" style={{ color: "var(--snad-color-error)" }}>{error}</p>}

      {!editable && (
        <div role="status" style={readOnlyStyle}>
          هذه النسخة {published ? "منشورة" : "غير قابلة للتحرير"} وهي للقراءة فقط. لا تُعدّل العقد أو الانتقالات في مكانها.
        </div>
      )}

      <div style={{ display: "flex", gap: 8, flexWrap: "wrap", margin: "14px 0" }}>
        <button type="button" onClick={() => setView("canvas")} disabled={view === "canvas"}>لوحة الرسم</button>
        <button type="button" onClick={() => setView("table")} disabled={view === "table"}>جدول البنية</button>
        <button type="button" onClick={() => void load()}>تحديث من الخادم</button>
        {draft && editable && (
          <button type="button" onClick={() => setDraft(null)}>
            حذف خطوة غير محفوظة
          </button>
        )}
      </div>

      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(280px, 1fr))", gap: 12, alignItems: "start" }}>
        <StepPalette disabled={!editable || Boolean(draft) || busy} onAdd={addLocalDraft} />

        <section aria-label="لوحة تصميم سير العمل" style={{ minWidth: 0 }}>
          {view === "canvas" ? (
            <div
              onDragOver={(event) => { if (editable) event.preventDefault(); }}
              onDrop={dropNode}
              style={canvasStyle}
            >
              <svg aria-hidden style={{ position: "absolute", inset: 0, width: "100%", height: "100%", pointerEvents: "none" }}>
                <defs>
                  <marker id="workflow-arrow" markerWidth="8" markerHeight="8" refX="8" refY="4" orient="auto">
                    <path d="M0,0 L8,4 L0,8 z" fill="currentColor" />
                  </marker>
                </defs>
                {transitions.map((transition) => {
                  const from = positions[transition.fromStepId];
                  const to = positions[transition.toStepId];
                  if (!from || !to) return null;
                  const labelX = (from.x + 140 + to.x) / 2;
                  const labelY = (from.y + 28 + to.y + 28) / 2 - 8;
                  return (
                    <g key={transition.id}>
                      <line
                        x1={from.x + 140}
                        y1={from.y + 28}
                        x2={to.x}
                        y2={to.y + 28}
                        stroke="currentColor"
                        strokeWidth="1.5"
                        markerEnd="url(#workflow-arrow)"
                      />
                      {/* R0.G6 — a transition must visibly communicate
                          SOURCE -> OUTCOME/CONDITION -> DESTINATION; no
                          ambiguous decorative lines. */}
                      <text
                        data-testid={`edge-label-${transition.transitionKey}`}
                        x={labelX}
                        y={labelY}
                        textAnchor="middle"
                        fontSize="11"
                        fill="currentColor"
                        stroke="var(--snad-color-background-default)"
                        strokeWidth="3"
                        paintOrder="stroke"
                      >
                        {transition.outcome || transition.transitionKey}
                      </text>
                    </g>
                  );
                })}
              </svg>

              {steps.map((step) => {
                const position = positions[step.id] ?? { x: 12, y: 12 };
                return (
                  <button
                    key={step.id}
                    type="button"
                    draggable={editable}
                    onDragStart={() => setDraggedStepId(step.id)}
                    onDragEnd={() => setDraggedStepId(null)}
                    onClick={() => { setDraft(null); setSelectedStepId(step.id); }}
                    style={{
                      ...nodeStyle,
                      ...(step.stepType === "PARALLEL_FORK" || step.stepType === "PARALLEL_JOIN"
                        ? { borderStyle: "dashed", borderWidth: 3, borderColor: "var(--snad-color-info)" }
                        : {}),
                      left: position.x,
                      top: position.y,
                      outline: selectedStepId === step.id ? "3px solid var(--snad-color-info)" : undefined,
                      cursor: editable ? "grab" : "pointer",
                    }}
                  >
                    <strong>{step.name}</strong>
                    <span style={{ display: "block", fontSize: 11, opacity: 0.72 }}>
                      {step.stepType}
                      {(step.stepType === "PARALLEL_FORK" || step.stepType === "PARALLEL_JOIN") && " ∥"}
                    </span>
                  </button>
                );
              })}

              {draft && editable && (
                <div style={{ ...nodeStyle, left: 12, bottom: 12, borderStyle: "dashed", top: "auto" }}>
                  <strong>{draft.name}</strong>
                  <span style={{ display: "block", fontSize: 11 }}>{draft.stepType} · غير محفوظة</span>
                </div>
              )}

              {steps.length === 0 && !draft && (
                <p style={{ padding: 24, color: "var(--snad-color-text-secondary)" }}>
                  لا توجد خطوات بعد. أضف أول عقدة من المكتبة.
                </p>
              )}
            </div>
          ) : (
            <div style={{ overflowX: "auto" }}>
              <table style={{ width: "100%", borderCollapse: "collapse" }}>
                <thead>
                  <tr><th style={cellStyle}>الخطوة</th><th style={cellStyle}>النوع</th><th style={cellStyle}>الترتيب</th><th style={cellStyle}>الانتقالات</th></tr>
                </thead>
                <tbody>
                  {steps.map((step) => (
                    <tr key={step.id}>
                      <td style={cellStyle}>{step.stepKey}</td>
                      <td style={cellStyle}>{step.stepType}</td>
                      <td style={cellStyle}>{step.sequenceOrder}</td>
                      <td style={cellStyle}>
                        {transitions.filter((item) => item.fromStepId === step.id).map((item) => `${item.outcome} → ${stepLabel(item.toStepId, steps)}`).join("، ") || "—"}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </section>

        <StepInspector
          draft={draft}
          selectedStep={selectedStep}
          steps={steps}
          editable={Boolean(editable)}
          busy={busy}
          onDraftChange={setDraft}
          onSaveDraft={saveStep}
          onCreateTransition={createTransition}
        />
      </div>

      <PublishPanel
        definition={definition}
        editable={Boolean(editable)}
        graphRevision={graphRevision}
        onPublished={load}
        onReload={load}
      />
    </div>
  );
}

function buildPositions(steps: WorkflowStepResponse[], current: Record<string, NodePosition>) {
  const next: Record<string, NodePosition> = {};
  steps.forEach((step, index) => {
    // R0.G6 — RTL presentation: later steps flow right-to-left, matching
    // the reading direction of the surrounding interface.
    next[step.id] = current[step.id] ?? {
      x: 24 + (3 - (index % 4)) * 170,
      y: 24 + Math.floor(index / 4) * 92,
    };
  });
  return next;
}

function stepLabel(id: string, steps: WorkflowStepResponse[]) {
  return steps.find((step) => step.id === id)?.stepKey ?? id.slice(0, 8);
}

function defaultName(type: WorkflowStepType) {
  const labels: Record<WorkflowStepType, string> = {
    START: "البداية",
    HUMAN_TASK: "مهمة بشرية",
    APPROVAL: "موافقة",
    CONDITION: "شرط",
    SYSTEM_ACTION: "إجراء نظامي",
    PARALLEL_FORK: "تفريع متوازٍ",
    PARALLEL_JOIN: "دمج متوازٍ",
    CALL_WORKFLOW: "استدعاء سير عمل",
    NOTIFICATION: "إشعار",
    END: "النهاية",
  };
  return labels[type];
}

const canvasStyle: CSSProperties = {
  position: "relative",
  minHeight: 440,
  overflow: "auto",
  border: "1px solid var(--snad-color-border-default)",
  borderRadius: 10,
  background: "var(--snad-color-background-default)",
};

const nodeStyle: CSSProperties = {
  position: "absolute",
  width: 140,
  minHeight: 56,
  padding: "8px 10px",
  border: "2px solid var(--snad-color-border-default)",
  borderRadius: 9,
  background: "var(--snad-color-background-default)",
  textAlign: "right",
};

const badgeStyle: CSSProperties = {
  padding: "3px 8px",
  border: "1px solid var(--snad-color-border-default)",
  borderRadius: 999,
  fontSize: 12,
};

const readOnlyStyle: CSSProperties = {
  padding: 10,
  border: "1px solid var(--snad-color-border-default)",
  borderRadius: 8,
  marginBottom: 10,
};

const cellStyle: CSSProperties = {
  padding: "9px 10px",
  borderBottom: "1px solid var(--snad-color-border-default)",
  textAlign: "right",
};
