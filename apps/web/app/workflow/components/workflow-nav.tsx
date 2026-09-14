"use client";

import { useRef, type KeyboardEvent } from "react";
import styles from "../workflow.module.css";

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
  const refs = useRef<Array<HTMLButtonElement | null>>([]);

  const moveFocus = (event: KeyboardEvent<HTMLButtonElement>, index: number) => {
    let nextIndex: number | null = null;
    if (event.key === "ArrowLeft") nextIndex = (index + 1) % SECTIONS.length;
    if (event.key === "ArrowRight") nextIndex = (index - 1 + SECTIONS.length) % SECTIONS.length;
    if (event.key === "Home") nextIndex = 0;
    if (event.key === "End") nextIndex = SECTIONS.length - 1;
    if (nextIndex === null) return;

    event.preventDefault();
    const next = SECTIONS[nextIndex];
    onChange(next.key);
    refs.current[nextIndex]?.focus();
  };

  return (
    <nav
      aria-label="أقسام سير العمل"
      role="tablist"
      aria-orientation="horizontal"
      dir="rtl"
      className={styles.nav}
    >
      {SECTIONS.map((section, index) => {
        const active = section.key === value;
        return (
          <button
            key={section.key}
            ref={(element) => { refs.current[index] = element; }}
            id={`workflow-tab-${section.key}`}
            type="button"
            role="tab"
            aria-selected={active}
            aria-controls={`workflow-panel-${section.key}`}
            tabIndex={active ? 0 : -1}
            onKeyDown={(event) => moveFocus(event, index)}
            onClick={() => onChange(section.key)}
            className={active ? `${styles.navTab} ${styles.navTabActive}` : styles.navTab}
          >
            {section.label}
          </button>
        );
      })}
    </nav>
  );
}
