"use client";

import type { WorkflowDefinitionResponse } from "@/lib/api/workflow-api";
import styles from "./workflow-designer.module.css";

export interface DesignerCommandBarProps {
  definition: WorkflowDefinitionResponse;
  busy: boolean;
  view: "canvas" | "table";
  onViewChange: (view: "canvas" | "table") => void;
  onRefresh: () => void;
  onCreateNextDraft: () => void;
}

export function DesignerCommandBar({
  definition,
  busy,
  view,
  onViewChange,
  onRefresh,
  onCreateNextDraft,
}: DesignerCommandBarProps) {
  const published = definition.publicationState === "PUBLISHED";
  const retired = definition.publicationState === "RETIRED";
  const stateLabel = published ? "منشور" : retired ? "متقاعد" : "مسودة";

  return (
    <header className={styles.commandBar} aria-label="شريط أوامر مصمم سير العمل">
      <div className={styles.commandIdentity}>
        <div className={styles.commandTitleRow}>
          <h1 className={styles.commandTitle}>{definition.name}</h1>
          <span className={styles.statusBadge}>{stateLabel}</span>
          <span className={styles.statusBadge}>v{definition.version}</span>
        </div>
        <p className={styles.commandMeta}>
          {definition.code} · {definition.engineGeneration} · البنية محفوظة في الخادم، ومواضع اللوحة محلية للعرض.
        </p>
      </div>

      <div className={styles.commandActions}>
        <div className={styles.segmentedControl} aria-label="طريقة العرض">
          <button
            type="button"
            aria-pressed={view === "canvas"}
            className={view === "canvas" ? styles.segmentActive : undefined}
            onClick={() => onViewChange("canvas")}
          >
            لوحة الرسم
          </button>
          <button
            type="button"
            aria-pressed={view === "table"}
            className={view === "table" ? styles.segmentActive : undefined}
            onClick={() => onViewChange("table")}
          >
            جدول البنية
          </button>
        </div>
        <button type="button" disabled={busy} onClick={onRefresh}>
          تحديث من الخادم
        </button>
        {definition.publicationState === "PUBLISHED" && (
          <button type="button" disabled={busy} onClick={onCreateNextDraft}>
            إنشاء مسودة جديدة
          </button>
        )}
      </div>
    </header>
  );
}
