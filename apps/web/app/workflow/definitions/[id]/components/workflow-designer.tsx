"use client";

import { useCallback, useEffect, useState } from "react";
import {
  workflowApi,
  type WorkflowDefinitionResponse,
  type WorkflowValidationResponse,
  type WorkflowSimulationResponse,
} from "@/lib/api/workflow-api";

interface StepView {
  id: string;
  stepKey: string;
  name: string;
  stepType: string;
  sequenceOrder: number;
}

interface TransitionView {
  id: string;
  fromStepId: string;
  toStepId: string;
  transitionKey: string;
  outcome: string;
  priority: number;
}

/**
 * Versioned workflow designer (design decisions H3/I3/AN3):
 *  - DRAFT versions are editable; PUBLISHED versions render read-only with
 *    "إنشاء مسودة جديدة" as the only mutation.
 *  - The canvas is DOM/CSS positioned nodes with SVG edges (no new graph
 *    dependency); a structured table view always mirrors the graph.
 *  - Publish stays disabled until the latest server validation is valid, and
 *    the simulation result is explicitly marked non-production.
 */
export function WorkflowDesigner({ definitionId }: { definitionId: string }) {
  const [definition, setDefinition] = useState<WorkflowDefinitionResponse | null>(null);
  const [steps, setSteps] = useState<StepView[]>([]);
  const [transitions, setTransitions] = useState<TransitionView[]>([]);
  const [validation, setValidation] = useState<WorkflowValidationResponse | null>(null);
  const [simulation, setSimulation] = useState<WorkflowSimulationResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [view, setView] = useState<"canvas" | "table">("canvas");
  const [busy, setBusy] = useState(false);

  const load = useCallback(async () => {
    setError(null);
    try {
      const [def, stepList, transitionList] = await Promise.all([
        workflowApi.getDefinition(definitionId),
        workflowApi.getDefinitionSteps(definitionId),
        workflowApi.getDefinitionTransitions(definitionId),
      ]);
      setDefinition(def);
      setSteps(stepList as StepView[]);
      setTransitions(transitionList as TransitionView[]);
    } catch (e: unknown) {
      setError((e as { message?: string })?.message ?? "تعذر تحميل التعريف");
    }
  }, [definitionId]);

  useEffect(() => {
    void load();
  }, [load]);

  const published = definition?.status === "ACTIVE";
  // The Y2 publication state rides additively on the definition payload.
  const publicationState =
    (definition as unknown as { publicationState?: string })?.publicationState ?? "DRAFT";

  const runValidate = async () => {
    setBusy(true);
    setError(null);
    try {
      setValidation(await workflowApi.validateDefinition(definitionId));
    } catch (e: unknown) {
      setError((e as { message?: string })?.message ?? "فشل التحقق");
    } finally {
      setBusy(false);
    }
  };

  const runSimulate = async () => {
    setBusy(true);
    setError(null);
    try {
      setSimulation(await workflowApi.simulateDefinition(definitionId));
    } catch (e: unknown) {
      setError((e as { message?: string })?.message ?? "فشل المحاكاة");
    } finally {
      setBusy(false);
    }
  };

  const publish = async () => {
    setBusy(true);
    setError(null);
    try {
      await workflowApi.publishDefinition(definitionId, definition?.versionLock ?? 0);
      await load();
    } catch (e: unknown) {
      const err = e as { status?: number; message?: string };
      setError(err?.status === 409 ? "تعارض نشر: قُدّم التعريف للتو" : err?.message ?? "فشل النشر");
    } finally {
      setBusy(false);
    }
  };

  const nextDraft = async () => {
    setBusy(true);
    setError(null);
    try {
      await workflowApi.createNextDraft(definitionId);
      await load();
    } catch (e: unknown) {
      setError((e as { message?: string })?.message ?? "فشل إنشاء المسودة");
    } finally {
      setBusy(false);
    }
  };

  if (!definition && !error) return <p dir="rtl">جارٍ التحميل…</p>;

  const positions = layout(steps);

  return (
    <div dir="rtl">
      {error && <p role="alert" style={{ color: "var(--snad-color-error)" }}>{error}</p>}
      <header style={{ display: "flex", alignItems: "center", gap: 12, marginBottom: 16 }}>
        <h2 style={{ margin: 0 }}>{definition?.name}</h2>
        <span data-testid="publication-state">
          {publicationState === "PUBLISHED" ? "منشور" : publicationState === "RETIRED" ? "مُهمَل" : "مسودة"}
        </span>
      </header>

      <div style={{ display: "flex", gap: 8, marginBottom: 12 }}>
        <button onClick={() => setView("canvas")} disabled={view === "canvas"}>لوحة الرسم</button>
        <button onClick={() => setView("table")} disabled={view === "table"}>جدول البنية</button>
        <button onClick={() => void runValidate()} disabled={busy}>تحقق</button>
        <button onClick={() => void runSimulate()} disabled={busy}>محاكاة</button>
        <button
          onClick={() => void publish()}
          disabled={busy || !validation?.valid || published || publicationState !== "DRAFT"}
          title={validation && !validation.valid ? "لا يُنشر قبل اجتياز التحقق" : undefined}
        >
          نشر
        </button>
        {published || publicationState === "PUBLISHED" ? (
          <button onClick={() => void nextDraft()} disabled={busy}>إنشاء مسودة جديدة</button>
        ) : null}
      </div>

      {validation && (
        <p role="status">
          {validation.valid
            ? "التحقق: سليم ✓"
            : "التحقق: أخفق — " + validation.errors.map((e) => e.code).join("، ")}
        </p>
      )}
      {simulation && (
        <p role="status">
          المحاكاة (غير إنتاجية — لا آثار جانبية حقيقية): {simulation.simulated ? "نُفذت على " : ""}
          {simulation.visitedStepIds.length} خطوة
        </p>
      )}

      {view === "canvas" ? (
        <div style={{ position: "relative", height: 320, border: "1px solid var(--snad-color-border-default)", borderRadius: 8, overflow: "hidden" }}>
          <svg style={{ position: "absolute", inset: 0, width: "100%", height: "100%" }} aria-hidden>
            {transitions.map((t) => {
              const from = positions.get(t.fromStepId);
              const to = positions.get(t.toStepId);
              if (!from || !to) return null;
              return (
                <line key={t.id} x1={from.x + 90} y1={from.y + 24} x2={to.x} y2={to.y + 24}
                      stroke="var(--snad-color-info)" strokeWidth={2} markerEnd="url(#arrow)" />
              );
            })}
            <defs>
              <marker id="arrow" markerWidth="8" markerHeight="8" refX="8" refY="4" orient="auto">
                <path d="M0,0 L8,4 L0,8 z" fill="var(--snad-color-info)" />
              </marker>
            </defs>
          </svg>
          {steps.map((step) => {
            const pos = positions.get(step.id);
            if (!pos) return null;
            return (
              <div key={step.id} style={{
                position: "absolute", left: pos.x, top: pos.y, width: 90,
                padding: "8px 6px", borderRadius: 8, textAlign: "center", fontSize: 12,
                border: "2px solid var(--snad-color-primary)", background: "var(--snad-color-background-default)",
                opacity: published || publicationState === "PUBLISHED" ? 0.85 : 1,
                pointerEvents: published || publicationState === "PUBLISHED" ? "none" : "auto",
                cursor: published || publicationState === "PUBLISHED" ? "default" : "move",
              }}>
                <strong>{step.stepKey}</strong>
                <div style={{ color: "var(--snad-color-text-secondary)" }}>{step.stepType}</div>
              </div>
            );
          })}
        </div>
      ) : (
        <table style={{ borderCollapse: "collapse", width: "100%" }}>
          <thead>
            <tr>
              <th style={cell}>الخطوة</th>
              <th style={cell}>النوع</th>
              <th style={cell}>الانتقالات الصادرة</th>
            </tr>
          </thead>
          <tbody>
            {steps.map((step) => (
              <tr key={step.id}>
                <td style={cell}>{step.stepKey}</td>
                <td style={cell}>{step.stepType}</td>
                <td style={cell}>
                  {transitions
                    .filter((t) => t.fromStepId === step.id)
                    .map((t) => `${t.transitionKey}→${labelOf(t.toStepId)}`)
                    .join("، ") || "—"}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  );

  function labelOf(stepId: string) {
    return steps.find((s) => s.id === stepId)?.stepKey ?? stepId.slice(0, 6);
  }
}

const cell: React.CSSProperties = {
  border: "1px solid var(--snad-color-border-default)", padding: 8, textAlign: "right",
};

/** Deterministic two-column layout keyed off sequence order. */
function layout(steps: StepView[]): Map<string, { x: number; y: number }> {
  const positions = new Map<string, { x: number; y: number }>();
  steps.forEach((step, index) => {
    positions.set(step.id, { x: 24 + (index % 4) * 150, y: 24 + Math.floor(index / 4) * 90 });
  });
  return positions;
}
