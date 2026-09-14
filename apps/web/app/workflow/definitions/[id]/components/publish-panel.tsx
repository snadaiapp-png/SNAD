"use client";

import { useState } from "react";
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
  latestValidation,
  simulation,
  onValidationChange,
  onSimulationChange,
  onInvalidateEvidence,
  onPublished,
  onReload,
  onActivity,
}: {
  definition: WorkflowDefinitionResponse;
  editable: boolean;
  latestValidation: WorkflowValidationResponse | null;
  simulation: WorkflowSimulationResponse | null;
  onValidationChange: (value: WorkflowValidationResponse | null) => void;
  onSimulationChange: (value: WorkflowSimulationResponse | null) => void;
  onInvalidateEvidence: () => void;
  onPublished: () => Promise<void>;
  onReload: () => Promise<void>;
  onActivity: (message: string) => void;
}) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const validate = async () => {
    setBusy(true);
    setError(null);
    onSimulationChange(null);
    try {
      const result = await workflowApi.validateDefinition(definition.id);
      onValidationChange(result);
      onActivity(result.valid ? "اكتمل تحقق الخادم بنجاح." : "اكتمل تحقق الخادم مع موانع نشر.");
    } catch (cause: unknown) {
      onValidationChange(null);
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
      const result = await workflowApi.simulateDefinition(definition.id);
      onSimulationChange(result);
      onActivity("اكتملت محاكاة غير إنتاجية للرسم الحالي.");
    } catch (cause: unknown) {
      setError(describeWorkflowError(cause, "فشلت محاكاة تعريف سير العمل"));
    } finally {
      setBusy(false);
    }
  };

  const publish = async () => {
    const canPublish = editable && latestValidation?.valid === true && !busy;
    if (!canPublish) return;
    setBusy(true);
    setError(null);
    try {
      await workflowApi.publishDefinition(definition.id, definition.versionLock);
      onInvalidateEvidence();
      onActivity("نُشر التعريف بنجاح وأصبحت أدلة المسودة السابقة غير صالحة.");
      await onPublished();
    } catch (cause: unknown) {
      const status = (cause as { status?: number })?.status;
      if (status === 409) {
        onInvalidateEvidence();
        setError("تغير التعريف بالتزامن. أُعيد تحميل النسخة الأحدث، وأصبح التحقق السابق غير صالح للنشر.");
        onActivity("تعارض 409: أُبطلت الأدلة وأُعيد تحميل حقيقة الخادم.");
        await onReload();
      } else {
        setError(describeWorkflowError(cause, "فشل نشر تعريف سير العمل"));
      }
    } finally {
      setBusy(false);
    }
  };

  const canPublish = editable && latestValidation?.valid === true && !busy;

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
          disabled={!canPublish}
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
            <strong>التحقق من الخادم: غير صالح — راجع مركز التشخيص.</strong>
          )}
        </div>
      )}

      {simulation && (
        <div role="status" style={simulationStyle}>
          <strong>محاكاة غير إنتاجية</strong>
          <p style={{ margin: "4px 0" }}>لا تنفذ آثارًا جانبية؛ التفاصيل متاحة في مركز التشخيص.</p>
          <span>الخطوات التي تمت زيارتها: {simulation.visitedStepIds.length}</span>
        </div>
      )}

      {!editable && (
        <p style={{ marginBottom: 0, color: "var(--snad-color-text-secondary)" }}>
          النسخة المنشورة أو المتقاعدة للقراءة فقط؛ أنشئ مسودة تالية قبل أي تعديل أو نشر جديد.
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
