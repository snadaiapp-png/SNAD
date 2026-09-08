"use client";

export type WorkflowConditionOperator =
  | "EQ"
  | "NEQ"
  | "GT"
  | "GTE"
  | "LT"
  | "LTE"
  | "IN"
  | "NOT_IN"
  | "EXISTS";

export interface WorkflowConditionClause {
  field: string;
  operator: WorkflowConditionOperator;
  value: string;
}

export interface WorkflowConditionAst {
  type: "AND" | "OR";
  conditions: WorkflowConditionClause[];
}

const OPERATORS: WorkflowConditionOperator[] = [
  "EQ", "NEQ", "GT", "GTE", "LT", "LTE", "IN", "NOT_IN", "EXISTS",
];

export function ExpressionRuleEditor({
  value,
  disabled = false,
  onChange,
}: {
  value: WorkflowConditionAst;
  disabled?: boolean;
  onChange: (value: WorkflowConditionAst) => void;
}) {
  const updateClause = (index: number, patch: Partial<WorkflowConditionClause>) => {
    const conditions = value.conditions.map((condition, currentIndex) =>
      currentIndex === index ? { ...condition, ...patch } : condition,
    );
    onChange({ ...value, conditions });
  };

  return (
    <fieldset disabled={disabled} style={fieldsetStyle}>
      <legend>شرط انتقال آمن</legend>
      <label style={fieldStyle}>
        <span>طريقة التجميع</span>
        <select
          aria-label="طريقة تجميع الشروط"
          value={value.type}
          onChange={(event) => onChange({ ...value, type: event.target.value as "AND" | "OR" })}
        >
          <option value="AND">كل الشروط AND</option>
          <option value="OR">أي شرط OR</option>
        </select>
      </label>

      {value.conditions.map((condition, index) => (
        <div key={index} style={{ display: "grid", gridTemplateColumns: "1fr 110px 1fr auto", gap: 6 }}>
          <input
            aria-label={`حقل الشرط ${index + 1}`}
            placeholder="payload.amount"
            value={condition.field}
            onChange={(event) => updateClause(index, { field: event.target.value })}
          />
          <select
            aria-label={`operator ${index + 1}`}
            value={condition.operator}
            onChange={(event) => updateClause(index, { operator: event.target.value as WorkflowConditionOperator })}
          >
            {OPERATORS.map((operator) => <option key={operator}>{operator}</option>)}
          </select>
          <input
            aria-label={`قيمة الشرط ${index + 1}`}
            placeholder="value"
            disabled={disabled || condition.operator === "EXISTS"}
            value={condition.operator === "EXISTS" ? "" : condition.value}
            onChange={(event) => updateClause(index, { value: event.target.value })}
          />
          <button
            type="button"
            aria-label={`حذف الشرط ${index + 1}`}
            onClick={() => onChange({ ...value, conditions: value.conditions.filter((_, i) => i !== index) })}
          >
            ×
          </button>
        </div>
      ))}

      <button
        type="button"
        onClick={() => onChange({
          ...value,
          conditions: [...value.conditions, { field: "", operator: "EQ", value: "" }],
        })}
      >
        + شرط
      </button>
      <small style={{ color: "var(--snad-color-text-secondary)" }}>
        يُحفظ الشرط كـ AST منظم ويُقيّم فقط بمحرك التعبيرات المقيد في الخادم.
      </small>
    </fieldset>
  );
}

const fieldsetStyle = {
  display: "grid",
  gap: 10,
  border: "1px solid var(--snad-color-border-default)",
  borderRadius: 8,
  padding: 10,
};

const fieldStyle = { display: "grid", gap: 4, fontSize: 13 };
