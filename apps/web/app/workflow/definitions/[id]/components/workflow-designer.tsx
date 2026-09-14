"use client";

import { useCallback, useEffect, useMemo, useState, type CSSProperties } from "react";
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
import { DesignerCommandBar } from "./designer-command-bar";
import { StepPalette } from "./step-palette";
import { StepInspector, type DesignerStepDraft } from "./step-inspector";
import { PublishPanel } from "./publish-panel";
import { WorkflowCanvas } from "./workflow-canvas";
import styles from "./workflow-designer.module.css";

interface NodePosition { x: number; y: number }

export function WorkflowDesigner({ definitionId }: { definitionId: string }) {
  const router = useRouter();
  const [definition, setDefinition] = useState<WorkflowDefinitionResponse | null>(null);
  const [steps, setSteps] = useState<WorkflowStepResponse[]>([]);
  const [transitions, setTransitions] = useState<WorkflowTransitionResponse[]>([]);
  const [draft, setDraft] = useState<DesignerStepDraft | null>(null);
  const [selectedStepId, setSelectedStepId] = useState<string | null>(null);
  const [selectedTransitionId, setSelectedTransitionId] = useState<string | null>(null);
  const [positions, setPositions] = useState<Record<string, NodePosition>>({});
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
      if (selectedTransitionId && !nextTransitions.some((transition) => transition.id === selectedTransitionId)) {
        setSelectedTransitionId(null);
      }
    } catch (cause: unknown) {
      setError(describeWorkflowError(cause, "تعذر تحميل مصمم سير العمل"));
    } finally {
      setLoading(false);
    }
  }, [definitionId, selectedStepId, selectedTransitionId]);

  useEffect(() => {
    void load();
  }, [load]);

  const editable = definition?.publicationState === "DRAFT";
  const selectedStep = useMemo(
    () => steps.find((step) => step.id === selectedStepId) ?? null,
    [selectedStepId, steps],
  );
  const selectedTransition = useMemo(
    () => transitions.find((transition) => transition.id === selectedTransitionId) ?? null,
    [selectedTransitionId, transitions],
  );
  const progressContext = useMemo(() => ({
    visitedStepIds: [] as string[],
    currentStepId: null as string | null,
    activeTransitionIds: [] as string[],
  }), []);

  const addLocalDraft = (stepType: WorkflowStepType) => {
    if (!editable || draft) return;
    const ordinal = steps.length + 1;
    setSelectedStepId(null);
    setSelectedTransitionId(null);
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
      setSelectedTransitionId(null);
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

  return (
    <div dir="rtl" className={styles.studio}>
      <DesignerCommandBar
        definition={definition}
        busy={busy}
        view={view}
        onViewChange={setView}
        onRefresh={() => void load()}
        onCreateNextDraft={() => void createNextDraft()}
      />

      {conflict && <p role="alert" style={{ color: "var(--snad-color-warning)" }}>{conflict}</p>}
      {error && <p role="alert" style={{ color: "var(--snad-color-error)" }}>{error}</p>}

      {!editable && (
        <div role="status" style={readOnlyStyle}>
          هذه النسخة {published ? "منشورة" : "غير قابلة للتحرير"} وهي للقراءة فقط. لا تُعدّل العقد أو الانتقالات في مكانها.
        </div>
      )}

      {draft && editable && (
        <div>
          <button type="button" onClick={() => setDraft(null)}>
            حذف خطوة غير محفوظة
          </button>
        </div>
      )}

      <div className={styles.workspaceGrid}>
        <StepPalette disabled={!editable || Boolean(draft) || busy} onAdd={addLocalDraft} />

        <section aria-label="لوحة تصميم سير العمل" className={styles.canvasRegion}>
          {view === "canvas" ? (
            <WorkflowCanvas
              steps={steps}
              transitions={transitions}
              positions={positions}
              selectedStepId={selectedStepId}
              selectedTransitionId={selectedTransitionId}
              editable={Boolean(editable)}
              progressContext={progressContext}
              onSelectStep={(id) => {
                if (id) setDraft(null);
                setSelectedStepId(id);
              }}
              onSelectTransition={setSelectedTransitionId}
              onMoveStep={(id, position) => {
                // Presentation-only state: moving nodes never mutates the server,
                // increments graphRevision, or invalidates authoritative evidence.
                setPositions((current) => ({ ...current, [id]: position }));
              }}
            />
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
          selectedTransition={selectedTransition}
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
    next[step.id] = current[step.id] ?? {
      x: 24 + (3 - (index % 4)) * 190,
      y: 24 + Math.floor(index / 4) * 104,
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
