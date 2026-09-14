"use client";

import { useMemo, useState } from "react";
import type { WorkflowStepType } from "@/lib/api/workflow-api";

type StepCategory = "FLOW" | "HUMAN" | "CONTROL" | "SYSTEM";
type StepCategoryFilter = "ALL" | StepCategory;

export const Y2_STEP_TYPES = [
  { type: "START", label: "بداية", category: "FLOW" },
  { type: "HUMAN_TASK", label: "مهمة بشرية", category: "HUMAN" },
  { type: "APPROVAL", label: "موافقة", category: "HUMAN" },
  { type: "CONDITION", label: "شرط", category: "CONTROL" },
  { type: "PARALLEL_FORK", label: "تفريع متوازٍ", category: "CONTROL" },
  { type: "PARALLEL_JOIN", label: "دمج متوازٍ", category: "CONTROL" },
  { type: "SYSTEM_ACTION", label: "إجراء نظامي", category: "SYSTEM" },
  { type: "CALL_WORKFLOW", label: "استدعاء سير عمل", category: "SYSTEM" },
  { type: "NOTIFICATION", label: "إشعار", category: "SYSTEM" },
  { type: "END", label: "نهاية", category: "FLOW" },
] satisfies { type: WorkflowStepType; label: string; category: StepCategory }[];

const CATEGORY_OPTIONS: { value: StepCategoryFilter; label: string }[] = [
  { value: "ALL", label: "كل الفئات" },
  { value: "FLOW", label: "التدفق" },
  { value: "HUMAN", label: "بشرية" },
  { value: "CONTROL", label: "تحكم" },
  { value: "SYSTEM", label: "نظام" },
];

export function StepPalette({
  disabled = false,
  onAdd,
}: {
  disabled?: boolean;
  onAdd: (type: WorkflowStepType) => void;
}) {
  const [searchQuery, setSearchQuery] = useState("");
  const [category, setCategory] = useState<StepCategoryFilter>("ALL");

  const filteredSteps = useMemo(() => {
    const query = searchQuery.trim().toLocaleLowerCase("ar");
    return Y2_STEP_TYPES.filter((item) => {
      const categoryMatches = category === "ALL" || item.category === category;
      const queryMatches = !query
        || item.label.toLocaleLowerCase("ar").includes(query)
        || item.type.toLowerCase().includes(query);
      return categoryMatches && queryMatches;
    });
  }, [category, searchQuery]);

  return (
    <aside aria-label="مكتبة الخطوات" style={panelStyle}>
      <h3 style={{ marginTop: 0, fontSize: 16 }}>مكتبة الخطوات</h3>
      <p style={hintStyle}>ابحث حسب الاسم أو النوع، ثم أضف الخطوة بالزر أو لوحة المفاتيح.</p>

      <div style={filtersStyle}>
        <label style={fieldStyle}>
          <span>بحث</span>
          <input
            aria-label="بحث في مكتبة الخطوات"
            value={searchQuery}
            onChange={(event) => setSearchQuery(event.target.value)}
            placeholder="اسم أو نوع الخطوة"
          />
        </label>
        <label style={fieldStyle}>
          <span>الفئة</span>
          <select
            aria-label="تصفية فئة الخطوات"
            value={category}
            onChange={(event) => setCategory(event.target.value as StepCategoryFilter)}
          >
            {CATEGORY_OPTIONS.map((option) => (
              <option key={option.value} value={option.value}>{option.label}</option>
            ))}
          </select>
        </label>
      </div>

      <div style={{ display: "grid", gap: 8 }}>
        {filteredSteps.map((item) => (
          <button
            key={item.type}
            type="button"
            disabled={disabled}
            onClick={() => onAdd(item.type)}
            style={{ textAlign: "right", padding: "9px 10px" }}
          >
            <strong>{item.label}</strong>
            <span style={{ display: "block", fontSize: 11, opacity: 0.72 }}>
              {item.type} · {item.category}
            </span>
          </button>
        ))}
      </div>

      {filteredSteps.length === 0 && (
        <p role="status" style={emptyStyle}>لا توجد خطوات مطابقة للبحث أو الفئة المحددة.</p>
      )}
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

const filtersStyle = {
  display: "grid",
  gap: 8,
  marginBottom: 12,
};

const fieldStyle = {
  display: "grid",
  gap: 4,
  fontSize: 12,
};

const emptyStyle = {
  margin: "12px 0 0",
  fontSize: 12,
  color: "var(--snad-color-text-secondary)",
};
