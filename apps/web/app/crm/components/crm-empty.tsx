"use client";

import type { ReactNode } from "react";
import { useI18n } from "@/lib/i18n/I18nProvider";
import styles from "../crm.module.css";

interface CrmEmptyProps {
  /** Optional rich icon node. */
  icon?: ReactNode;
  /** Optional custom title. */
  title?: string;
  /** Optional custom hint shown beneath the title. */
  hint?: string;
  /** Optional CTA node (button, link, etc.) rendered beneath the hint. */
  action?: ReactNode;
}

/**
 * CrmEmpty — Friendly empty state for CRM list/dashboard surfaces.
 *
 * Renders a centered, low-density block so the user can clearly see that
 * the underlying collection has zero rows (and not, e.g., a loading bug).
 */
export function CrmEmpty({ icon, title, hint, action }: CrmEmptyProps) {
  const { t } = useI18n();
  const heading = title ?? t("crm.state.empty");
  const subtitle = hint ?? t("crm.state.emptyHint");
  return (
    <div className={styles.root}>
      <div className={styles.emptyState} role="status">
        <div aria-hidden="true">
          {icon ?? (
            <svg viewBox="0 0 64 64" fill="none" width="48" height="48">
              <rect x="8" y="12" width="48" height="40" rx="4" stroke="currentColor" strokeWidth="2" />
              <line x1="8" y1="22" x2="56" y2="22" stroke="currentColor" strokeWidth="2" />
              <circle cx="14" cy="17" r="1.5" fill="currentColor" />
              <circle cx="19" cy="17" r="1.5" fill="currentColor" />
              <line x1="20" y1="34" x2="44" y2="34" stroke="currentColor" strokeWidth="2" strokeLinecap="round" />
              <line x1="20" y1="40" x2="38" y2="40" stroke="currentColor" strokeWidth="2" strokeLinecap="round" />
            </svg>
          )}
        </div>
        <h3>{heading}</h3>
        <p>{subtitle}</p>
        {action ?? null}
      </div>
    </div>
  );
}
