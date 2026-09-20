"use client";

/**
 * HR state badge — shared component for canonical HRM v2 status vocabularies.
 * G1-T11 refactor: shared badge used by recruitment + onboarding screens.
 *
 * Design:
 *   - Color coding is NON-PRIMARY-only for state semantics (WCAG): success /
 *     warning / danger / neutral. Overdue emphasis uses font-weight + icon,
 *     never color alone (spec §14: "overdue emphasis non-color-coded").
 *   - Logical CSS only (no physical left/right); RTL-aware via dir=auto on
 *     the inner span so badge values from the backend always render in the
 *     correct direction regardless of page direction.
 *   - Unknown states fall back to the raw code (never invented).
 *   - No tooltips — the visible label IS the accessible name.
 */

import styles from "../hr.module.css";
import type { ReactNode } from "react";

export type BadgeTone = "neutral" | "success" | "warning" | "danger" | "info";

const TONE_CLASS: Record<BadgeTone, string> = {
  neutral: styles.badgeNeutral,
  success: styles.badgeSuccess,
  warning: styles.badgeWarning,
  danger: styles.badgeDanger,
  info: styles.badgeInfo,
};

interface HrStateBadgeProps {
  /** Translated label (already i18n-resolved by the caller). */
  label: string;
  /** Raw backend code, surfaced as data-status for tests + a11y. */
  code: string;
  /** Tone — caller decides based on state semantics. */
  tone?: BadgeTone;
  /** Optional icon (must be aria-hidden). */
  icon?: ReactNode;
}

export function HrStateBadge({ label, code, tone = "neutral", icon }: HrStateBadgeProps) {
  return (
    <span
      className={`${styles.stateBadge} ${TONE_CLASS[tone]}`}
      data-status={code}
      role="status"
    >
      {icon ? <span aria-hidden="true" className={styles.stateBadgeIcon}>{icon}</span> : null}
      <span dir="auto">{label}</span>
    </span>
  );
}

// ---------------------------------------------------------------------------
// Tone resolver helpers — callers pass a status code, get a tone back.
// These are pure functions, exported for unit testing.
// ---------------------------------------------------------------------------

const SUCCESS_STATES = new Set([
  "ACTIVE", "OPEN", "COMPLETED", "APPROVED", "ACCEPTED", "HIRED", "SUCCEEDED",
]);
const WARNING_STATES = new Set([
  "DRAFT", "PENDING", "IN_APPROVAL", "PAUSED", "PLANNED", "SCHEDULED", "IN_PROGRESS", "SCREENING",
]);
const DANGER_STATES = new Set([
  "REJECTED", "CANCELLED", "WITHDRAWN", "ARCHIVED", "EXPIRED", "DECLINED", "TERMINATED", "VOID", "FAILED",
]);
const INFO_STATES = new Set([
  "INTERVIEW", "OFFERED", "EXTENDED", "ONBOARDING", "SUBMITTED",
]);

export function toneForState(code: string): BadgeTone {
  if (SUCCESS_STATES.has(code)) return "success";
  if (WARNING_STATES.has(code)) return "warning";
  if (DANGER_STATES.has(code)) return "danger";
  if (INFO_STATES.has(code)) return "info";
  return "neutral";
}
