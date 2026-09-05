"use client";

/**
 * HR compliance status badge — WS5 Task 8 / Task 9 Step 5.
 *
 * Displays the localized compliance mode with safe, honest semantics:
 * - LOCALIZED (with pack version) is the only mode rendered as "certified".
 * - GLOBAL_MODE must never show a green/compliant badge — it means local
 *   statutory compliance is NOT certified.
 * - CONTROLLED_EXCEPTION_REQUIRED / COMPLIANCE_BLOCKED communicate safe
 *   warning states without inventing legal meaning.
 */

import styles from "../hr.module.css";

export type HrComplianceMode = "LOCALIZED" | "GLOBAL_MODE" | "CONTROLLED_EXCEPTION_REQUIRED" | "COMPLIANCE_BLOCKED" | string;

export interface HrComplianceBadgeProps {
  mode: HrComplianceMode;
  packCode?: string | null;
  packVersion?: string | null;
}

export function HrComplianceBadge({ mode, packCode, packVersion }: HrComplianceBadgeProps) {
  const localized = mode === "LOCALIZED";
  const globalMode = mode === "GLOBAL_MODE";
  const exception = mode === "CONTROLLED_EXCEPTION_REQUIRED";
  const blocked = mode === "COMPLIANCE_BLOCKED";

  const label = localized
    ? "ملتزم محليًا"
    : globalMode
      ? "وضع عالمي — الالتزام المحلي غير معتمد"
      : exception
        ? "يتطلب تجاوزًا مضبوطًا"
        : blocked
          ? "محظور بموجب الالتزام"
          : `حالة التزام: ${mode}`;

  const variant = localized ? styles.badgeLocalized
    : globalMode ? styles.badgeGlobalMode
    : exception ? styles.badgeException
    : blocked ? styles.badgeBlocked
    : styles.badgeUnknown;

  return (
    <span
      className={`${styles.complianceBadge} ${variant}`}
      role="status"
      title={label}
    >
      <span aria-hidden="true" className={styles.badgeDot} />
      {label}
      {localized && packCode ? (
        <span className={styles.badgePack}>
          {packCode}{packVersion ? ` · ${packVersion}` : ""}
        </span>
      ) : null}
    </span>
  );
}
