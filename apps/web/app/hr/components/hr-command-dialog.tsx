"use client";

/**
 * Shared HR command confirmation dialog — WS5 Task 10.
 *
 * Accessible modal (role=dialog, labelled, keyboard-focusable controls) used
 * by structure/assignment/compliance command flows. Sends nothing itself —
 * pages own their API calls, Idempotency-Key generation and expectedVersion.
 */

import type { ReactNode } from "react";
import styles from "../hr.module.css";

export interface HrDialogField {
  label: string;
  type: "date" | "text" | "textarea";
  value: string;
  onChange: (v: string) => void;
  required?: boolean;
  placeholder?: string;
}

export interface HrCommandDialogProps {
  title: string;
  description?: string;
  fields?: HrDialogField[];
  busy?: boolean;
  error?: string | null;
  confirmLabel?: string;
  cancelLabel?: string;
  onConfirm: () => void;
  onCancel: () => void;
  children?: ReactNode;
}

export function HrCommandDialog({
  title,
  description,
  fields = [],
  busy = false,
  error,
  confirmLabel = "تأكيد",
  cancelLabel = "إلغاء",
  onConfirm,
  onCancel,
  children,
}: HrCommandDialogProps) {
  return (
    <div role="dialog" aria-modal="true" aria-label={title} className={styles.dialogOverlay}>
      <div className={styles.dialog}>
        <h3>{title}</h3>
        {description ? <p className={styles.mutedNote}>{description}</p> : null}
        {error ? <p role="alert" className={styles.dialogError}>{error}</p> : null}
        {children}
        {fields.map((f) => (
          <label key={f.label} className={styles.dialogField}>
            {f.label}
            {f.type === "textarea" ? (
              <textarea
                value={f.value}
                required={f.required}
                placeholder={f.placeholder}
                onChange={(e) => f.onChange(e.target.value)}
                rows={3}
              />
            ) : (
              <input
                type={f.type}
                value={f.value}
                required={f.required}
                placeholder={f.placeholder}
                onChange={(e) => f.onChange(e.target.value)}
              />
            )}
          </label>
        ))}
        <div className={styles.actionRow}>
          <button type="button" className={styles.actionButton} onClick={onConfirm} disabled={busy}>
            {confirmLabel}
          </button>
          <button type="button" className={styles.cancelButton} onClick={onCancel}>
            {cancelLabel}
          </button>
        </div>
      </div>
    </div>
  );
}
