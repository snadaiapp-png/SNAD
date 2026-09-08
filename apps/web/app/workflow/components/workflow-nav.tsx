"use client";

export type WorkflowSection =
  | "overview"
  | "definitions"
  | "my-tasks"
  | "approvals"
  | "instances"
  | "incidents"
  | "monitoring"
  | "settings";

const SECTIONS: { key: WorkflowSection; label: string }[] = [
  { key: "overview", label: "نظرة عامة" },
  { key: "definitions", label: "التعريفات" },
  { key: "my-tasks", label: "مهامي" },
  { key: "approvals", label: "الموافقات" },
  { key: "instances", label: "المثيلات" },
  { key: "incidents", label: "الحوادث" },
  { key: "monitoring", label: "المراقبة" },
  { key: "settings", label: "الإعدادات" },
];

/** Routing only. Authorization remains server-authoritative. */
export function WorkflowNav({
  value,
  onChange,
}: {
  value: WorkflowSection;
  onChange: (section: WorkflowSection) => void;
}) {
  return (
    <nav
      aria-label="أقسام سير العمل"
      role="tablist"
      dir="rtl"
      style={{
        display: "flex",
        gap: 4,
        overflowX: "auto",
        borderBottom: "1px solid var(--snad-color-border-default)",
        marginBottom: 20,
      }}
    >
      {SECTIONS.map((section) => {
        const active = section.key === value;
        return (
          <button
            key={section.key}
            type="button"
            role="tab"
            aria-selected={active}
            aria-controls={`workflow-panel-${section.key}`}
            onClick={() => onChange(section.key)}
            style={{
              padding: "10px 14px",
              border: 0,
              borderBottom: active
                ? "2px solid var(--snad-color-primary)"
                : "2px solid transparent",
              background: "transparent",
              color: active
                ? "var(--snad-color-primary)"
                : "var(--snad-color-text-secondary)",
              cursor: "pointer",
              fontWeight: active ? 700 : 500,
              whiteSpace: "nowrap",
            }}
          >
            {section.label}
          </button>
        );
      })}
    </nav>
  );
}
