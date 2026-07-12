"use client";
import type { ReactNode } from "react";
import { useCrmI18n } from "./crm-i18n";
import styles from "./crm-command-center.module.css";

interface EmptyStateProps {
  icon?: ReactNode; titleKey?: string; subtitleKey?: string; hintKey?: string;
}

export function CrmEmptyState({
  icon, titleKey = "empty.title", subtitleKey = "empty.subtitle", hintKey = "empty.checkBoard",
}: EmptyStateProps) {
  const { t } = useCrmI18n();
  return (
    <div className={styles.emptyState}>
      <div className={styles.emptyIcon}>
        {icon ?? (
          <svg viewBox="0 0 64 64" fill="none" aria-hidden="true">
            <rect x="8" y="12" width="48" height="40" rx="4" stroke="currentColor" strokeWidth="2" />
            <line x1="8" y1="22" x2="56" y2="22" stroke="currentColor" strokeWidth="2" />
            <circle cx="14" cy="17" r="1.5" fill="currentColor" />
            <circle cx="19" cy="17" r="1.5" fill="currentColor" />
            <line x1="20" y1="34" x2="44" y2="34" stroke="currentColor" strokeWidth="2" strokeLinecap="round" />
            <line x1="20" y1="40" x2="38" y2="40" stroke="currentColor" strokeWidth="2" strokeLinecap="round" />
            <circle cx="48" cy="46" r="8" fill="var(--snad-surface-primary)" stroke="var(--snad-warning)" strokeWidth="2" />
            <line x1="48" y1="43" x2="48" y2="49" stroke="var(--snad-warning)" strokeWidth="2" strokeLinecap="round" />
            <circle cx="48" cy="51" r="0.8" fill="var(--snad-warning)" />
          </svg>
        )}
      </div>
      <h3 className={styles.emptyTitle}>{t(titleKey)}</h3>
      <p className={styles.emptySubtitle}>{t(subtitleKey)}</p>
      <p className={styles.emptyHint}>{t(hintKey)}</p>
    </div>
  );
}
