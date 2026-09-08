"use client";

import { useEffect, useState } from "react";
import {
  workflowApi,
  type WorkflowDefinitionResponse,
  type WorkflowSimulationResponse,
  type WorkflowValidationResponse,
} from "@/lib/api/workflow-api";
import { describeWorkflowError } from "@/lib/workflow/error-messages";

export function PublishPanel({
  definition,
  editable,
  graphRevision,
  onPublished,
  onReload,
}: {
  definition: WorkflowDefinitionResponse;
  editable: boolean;
  graphRevision: number;
  onPublished: () => Promise<void>;
  onReload: () => Promise<void>;
}) {
  const [latestValidation, setLatestValidation] = useState<WorkflowValidationResponse | null>(null);
  const [simulation, setSimulation] = useState<WorkflowSimulationResponse | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    setLatestValidation(null);
    setSimulation(null);
  }, [definition.id, graphRevision]);

  const validate = async () => {
    setBusy(true);
    setError(null);
    setSimulation(null);
    try {
      setLatestValidation(await workflowApi.validateDefinition(definition.id));
    } catch (cause: unknown) {
      setLatestValidation(null);
      setError(describeWorkflowError(cause, "فشل التحقق من تعريف سير العمل"));
    } finally {
      setBusy(false);
    }
  };

  const simulate = async () => {
    if (!latestValidation?.valid) return;
    setBusy(true);
    setError(null);
    try {
      setSimulation(await workflowApi.simulateDefinition(definition.id));
    } catch (cause: unknown) {
      setError(describeWorkflowError(cause, "فشلت محاكاة تعريف سير العمل"));
    } finally {
      setBusy(false);
    }
  };

  const publish = async () => {
    if (!latestValidation?.valid || !editable) return;
    setBusy(true);
    setError(null);
    try {
      await workflowApi.publishDefinition(definition.id, definition.versionLock);
      setLatestValidation(null);
      setSimulation(null);
      await onPublished();
    } catch (cause: unknown) {
      const status = (cause as { status?: number })?.status;
      if (status === 409) {
        setLatestValidation(null);
        setSimulation(null);
        setError("تغير التعريف بالتزامن. أُعيد تحميل النسخة الأحدث، وأصبح التحقق السابق غير صالح للنشر.");
        await onReload();
      } else {
        setError(describeWorkflowError(cause, "فشل نشر تعريف سير العمل"));
      }
    } finally {
      setBusy(false);
    }
  };

  return (
    <section aria-label="التحقق والمحاكاة والنشر" style={panelStyle}>
      <div style={{ display: "flex", gap: 8, flexWrap: "wrap", alignItems: "center" }}>
        <button type="button" disabled={busy} onClick={() => void validate()}>
          1. تحقق
        </button>
        <button type="button" disabled={!latestValidation?.valid || busy} onClick={() => void simulate()}>
          2. محاكاة
        </button>
        <button
          type="button"
          disabled={!latestValidation?.valid || busy || !editable}
          onClick={() => void publish()}
          title={!latestValidation?.valid ? "النشر يتطلب آخر تحقق صالح من الخادم" : undefined}
        >
          3. نشر
        </button>
      </div>

      {error && <p role="alert" style={{ color: "var(--snad-color-error)" }}>{error}</p>}

      {latestValidation && (
        <div role="status" style={{ marginTop: 10 }}>
          {latestValidation.valid ? (
            <strong>التحقق من الخادم: صالح للنشر ✓</strong>
          ) : (
            <>
              <strong>التحقق من الخادم: غير صالح</strong>
              <ul>
                {latestValidation.errors.map((item, index) => (
                  <li key={`${item.code}-${item.stepId}-${index}`}>{item.code}: {item.message}</li>
                ))}
              </ul>
            </>
          )}
        </div>
      )}

      {simulation && (
        <div role="status" style={simulationStyle}>
          <strong>محاكاة غير إنتاجية</strong>
          <p style={{ margin: "4px 0" }}>
            هذه المعاينة لا تنفذ آثارًا جانبية في الوحدات المصدرية ولا تستبدل التفويض وقت التنفيذ.
          </p>
          <span>الخطوات التي تمت زيارتها: {simulation.visitedStepIds.length}</span>
          {simulation.notes.length > 0 && (
            <ul>{simulation.notes.map((note, index) => <li key={`${index}-${note}`}>{note}</li>)}</ul>
          )}
        </div>
      )}

      {!editable && (
        <p style={{ marginBottom: 0, color: "var(--snad-color-text-secondary)" }}>
          النسخة المنشورة للقراءة فقط؛ أنشئ مسودة تالية قبل أي تعديل أو نشر جديد.
        </p>
      )}
    </section>
  );
}

const panelStyle = {
  border: "1px solid var(--snad-color-border-default)",
  borderRadius: 10,
  padding: 12,
  marginTop: 16,
};

const simulationStyle = {
  marginTop: 10,
  padding: 10,
  border: "1px solid var(--snad-color-border-default)",
  borderRadius: 8,
};
