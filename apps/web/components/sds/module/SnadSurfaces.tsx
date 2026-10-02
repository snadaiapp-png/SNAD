"use client";

/**
 * SNAD shared module surface primitives — page header, KPI, panels, states.
 * Generalized from the CRM reference anatomy; consumed by every module.
 * All styles come from snad-module.module.css (SDS tokens only).
 */

import type { ReactNode } from "react";
import styles from "./snad-module.module.css";

/* ============================== Page header ============================== */

export function SnadPageHeader({
  title,
  subtitle,
  eyebrow,
  trailing,
}: {
  title: string;
  subtitle?: string;
  eyebrow?: string;
  trailing?: ReactNode;
}) {
  return (
    <header className={styles.pageHeader}>
      <div className={styles.pageHeaderCopy}>
        {eyebrow ? <span className={styles.pageEyebrow}>{eyebrow}</span> : null}
        <h1 className={styles.pageTitle}>{title}</h1>
        {subtitle ? <p className={styles.pageDescription}>{subtitle}</p> : null}
      </div>
      {trailing ? <div className={styles.pageHeaderTrailing}>{trailing}</div> : null}
    </header>
  );
}

/* ============================== KPI ====================================== */

export function SnadKpiGrid({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div role="group" aria-label={label} className={styles.kpiGrid}>
      {children}
    </div>
  );
}

export function SnadKpiCard({
  label,
  value,
  hint,
  tone = "neutral",
}: {
  label: string;
  value: ReactNode;
  hint?: string;
  tone?: "neutral" | "positive" | "attention";
}) {
  const toneClass =
    tone === "positive" ? styles.kpiCardTonePositive : tone === "attention" ? styles.kpiCardToneAttention : "";
  return (
    <div className={`${styles.kpiCard} ${toneClass}`}>
      <span className={styles.kpiLabel}>{label}</span>
      <span className={styles.kpiValue}>{value}</span>
      {hint ? (
        <span className={styles.kpiHint}>
          <span className={styles.kpiHintDot} />
          {hint}
        </span>
      ) : null}
    </div>
  );
}

/* ============================== Panels =================================== */

export function SnadOperationalPanel({
  label,
  title,
  description,
  children,
}: {
  label: string;
  title: string;
  description?: string;
  children: ReactNode;
}) {
  return (
    <section aria-label={label} className={styles.sectionCard}>
      <div>
        <h2 className={styles.sectionCardTitle}>{title}</h2>
        {description ? <p className={styles.sectionCardDescription}>{description}</p> : null}
      </div>
      {children}
    </section>
  );
}

export function SnadActionBar({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div role="group" aria-label={label} className={styles.actionBar}>
      {children}
    </div>
  );
}

/* ============================== Status badge ============================= */

export type SnadBadgeTone = "neutral" | "success" | "warning" | "danger" | "info";

const TONE_CLASS: Record<SnadBadgeTone, string> = {
  neutral: styles.statusBadgeNeutral,
  success: styles.statusBadgeSuccess,
  warning: styles.statusBadgeWarning,
  danger: styles.statusBadgeDanger,
  info: styles.statusBadgeInfo,
};

export function SnadStatusBadge({
  label,
  tone = "neutral",
  status,
  source,
  icon,
}: {
  label: string;
  tone?: SnadBadgeTone;
  status?: string;
  source?: string;
  icon?: ReactNode;
}) {
  return (
    <span
      className={`${styles.statusBadge} ${TONE_CLASS[tone]}`}
      data-status={status}
      data-source={source}
    >
      {icon ? <span aria-hidden="true">{icon}</span> : null}
      {label}
    </span>
  );
}

/* ============================== States =================================== */

export function SnadLoadingState({ label }: { label: string }) {
  return (
    <div role="status" aria-busy="true" className={styles.loadingState}>
      <span className={styles.loadingSpinner} aria-hidden="true" />
      {label}
    </div>
  );
}

export function SnadEmptyState({
  title,
  description,
  hint,
  action,
  icon,
}: {
  title: string;
  description?: string;
  hint?: string;
  action?: ReactNode;
  icon?: ReactNode;
}) {
  return (
    <div className={styles.emptyState}>
      {icon ? <span className={styles.emptyIcon} aria-hidden="true">{icon}</span> : null}
      <p className={styles.emptyTitle}>{title}</p>
      {description ? <p className={styles.emptySubtitle}>{description}</p> : null}
      {hint ? <span className={styles.kpiHint}>{hint}</span> : null}
      {action ? <div>{action}</div> : null}
    </div>
  );
}

export function SnadErrorState({
  message,
  kind = "generic",
  retryLabel,
  onRetry,
}: {
  /** Localized error message (caller maps the error via its own helper). */
  message: string;
  /** Error classification surfaced for tests/styling (e.g. forbidden/server). */
  kind?: string;
  retryLabel?: string;
  onRetry?: () => void;
}) {
  return (
    <div role="alert" data-kind={kind} className={styles.errorState}>
      <p className={styles.errorStateTitle}>{message}</p>
      {onRetry ? (
        <button type="button" className={styles.retryButton} onClick={onRetry}>
          {retryLabel ?? "إعادة المحاولة"}
        </button>
      ) : null}
    </div>
  );
}

export function SnadForbiddenState({ description }: { description: string }) {
  return (
    <div role="alert" data-kind="forbidden" className={styles.errorState}>
      <p className={styles.errorStateTitle}>{description}</p>
    </div>
  );
}

export function SnadSuccessNotice({ children }: { children: ReactNode }) {
  return (
    <p role="status" className={styles.successNotice}>
      {children}
    </p>
  );
}

/* ============================== Mobile records =========================== */

export function SnadMobileRecordList({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div role="list" aria-label={label} className={styles.mobileRecords} data-testid="g2-mobile-record-list">
      {children}
    </div>
  );
}

export function SnadMobileRecordCard({ children }: { children: ReactNode }) {
  return (
    <article role="listitem" className={styles.mobileRecordCard}>
      {children}
    </article>
  );
}
