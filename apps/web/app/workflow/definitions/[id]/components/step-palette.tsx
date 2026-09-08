"use client";

import type { WorkflowStepType } from "@/lib/api/workflow-api";

export const Y2_STEP_TYPES: { type: WorkflowStepType; label: string }[] = [
  { type: "START", label: "بداية" },
  { type: "HUMAN_TASK", label: "مهمة بشرية" },
  { type: "APPROVAL", label: "موافقة" },
  { type: "CONDITION", label: "شرط" },
  { type: "SYSTEM_ACTION", label: "إجراء نظامي" },
  { type: "PARALLEL_FORK", label: "تفريع متوازٍ" },
  { type: "PARALLEL_JOIN", label: "دمج متوازٍ" },
  { type: "CALL_WORKFLOW", label: "استدعاء سير عمل" },
  { type: "NOTIFICATION", label: "إشعار" },
  { type: "END", label: "نهاية" },
];

export function StepPalette({
  disabled = false,
  onAdd,
}: {
  disabled?: boolean;
  onAdd: (type: WorkflowStepType) => void;
}) {
  return (
    <aside aria-label="مكتبة الخطوات" style={panelStyle}>
      <h3 style={{ marginTop: 0, fontSize: 16 }}>مكتبة الخطوات</h3>
      <p style={hintStyle}>أضف عقدة إلى المسودة، ثم اضبطها واحفظها من المفتش.</p>
      <div style={{ display: "grid", gap: 8 }}>
        {Y2_STEP_TYPES.map((item) => (
          <button
            key={item.type}
            type="button"
            disabled={disabled}
            onClick={() => onAdd(item.type)}
            style={{ textAlign: "right", padding: "9px 10px" }}
          >
            <strong>{item.label}</strong>
            <span style={{ display: "block", fontSize: 11, opacity: 0.7 }}>{item.type}</span>
          </button>
        ))}
      </div>
    </aside>
  );
}

const panelStyle = {
  border: "1px solid var(--snad-color-border-default)",
  borderRadius: 10,
  padding: 12,
  minWidth: 180,
};

const hintStyle = {
  margin: "0 0 12px",
  fontSize: 12,
  color: "var(--snad-color-text-secondary)",
};
