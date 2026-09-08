"use client";

export type WorkflowAssignmentRuleType =
  | "EMPLOYEE"
  | "MANAGER"
  | "POSITION"
  | "DEPARTMENT"
  | "ROLE"
  | "PERMISSION";

export interface WorkflowAssignmentRuleDraft {
  type: WorkflowAssignmentRuleType;
  target: string;
}

const RULE_LABELS: Record<WorkflowAssignmentRuleType, string> = {
  EMPLOYEE: "موظف محدد",
  MANAGER: "مدير الموظف",
  POSITION: "منصب",
  DEPARTMENT: "إدارة",
  ROLE: "دور",
  PERMISSION: "صلاحية",
};

export function AssignmentRuleEditor({
  value,
  disabled = false,
  onChange,
}: {
  value: WorkflowAssignmentRuleDraft;
  disabled?: boolean;
  onChange: (value: WorkflowAssignmentRuleDraft) => void;
}) {
  return (
    <fieldset disabled={disabled} style={fieldsetStyle}>
      <legend>قاعدة الإسناد</legend>
      <label style={fieldStyle}>
        <span>نوع قاعدة الإسناد</span>
        <select
          aria-label="نوع قاعدة الإسناد"
          value={value.type}
          onChange={(event) =>
            onChange({ type: event.target.value as WorkflowAssignmentRuleType, target: "" })
          }
        >
          {(Object.keys(RULE_LABELS) as WorkflowAssignmentRuleType[]).map((type) => (
            <option key={type} value={type}>
              {RULE_LABELS[type]} — {type}
            </option>
          ))}
        </select>
      </label>
      <label style={fieldStyle}>
        <span>{targetLabel(value.type)}</span>
        <input
          aria-label="قيمة قاعدة الإسناد"
          value={value.target}
          onChange={(event) => onChange({ ...value, target: event.target.value })}
          placeholder={targetPlaceholder(value.type)}
        />
      </label>
      <small style={{ color: "var(--snad-color-text-secondary)" }}>
        الإسناد يحدد مرشحين فقط؛ التفويض والصلاحيات يعاد التحقق منهما في الخادم وقت التنفيذ.
      </small>
    </fieldset>
  );
}

function targetLabel(type: WorkflowAssignmentRuleType) {
  switch (type) {
    case "EMPLOYEE": return "معرّف الموظف";
    case "MANAGER": return "معرّف الموظف المرجعي";
    case "POSITION": return "معرّف المنصب";
    case "DEPARTMENT": return "معرّف الإدارة";
    case "ROLE": return "رمز الدور";
    case "PERMISSION": return "رمز الصلاحية";
  }
}

function targetPlaceholder(type: WorkflowAssignmentRuleType) {
  return type === "ROLE" || type === "PERMISSION" ? "CODE" : "UUID";
}

const fieldsetStyle = {
  display: "grid",
  gap: 10,
  border: "1px solid var(--snad-color-border-default)",
  borderRadius: 8,
  padding: 10,
};

const fieldStyle = { display: "grid", gap: 4, fontSize: 13 };
