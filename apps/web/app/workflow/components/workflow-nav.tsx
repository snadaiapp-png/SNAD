"use client";

import { useAuth } from "@/lib/auth/auth-provider";

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

/**
 * Y2 operational IA navigation (design decision AP3). The UI only routes —
 * every action is authorized server-side; hiding a tab is usability, not
 * an authorization boundary.
 */
export function WorkflowNav({
  value,
  onChange,
}: {
  value: WorkflowSection;
  onChange: (section: WorkflowSection) => void;
}) {
  const { user } = useAuth();
  return (
    <nav aria-label="أقسام سير العمل" role="tablist" dir="rtl"
         style={{ display: "flex", gap: 4, flexWrap: "wrap", marginBottom: 16 }}>
      {SECTIONS.map((section) => {
        const active = section.key === value;
        return (
          <button
            key={section.key}
            role="tab"
            aria-selected={active}
            onClick={() => onChange(section.key)}
            style={{
              padding: "8px 14px",
              borderRadius: 8,
              border: "1px solid " + (active ? "var(--snad-color-primary)" : "transparent"),
              background: active ? "var(--snad-color-primary)" : "transparent",
              color: active ? "#fff" : "var(--snad-color-text-secondary)",
              cursor: user ? "pointer" : "not-allowed",
              fontWeight: active ? 700 : 500,
            }}
          >
            {section.label}
          </button>
        );
      })}
    </nav>
  );
}
