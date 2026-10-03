"use client";

import type { ReactNode } from "react";
import styles from "./snad-page-state.module.css";

export function SnadPageHeader({
  title, subtitle, eyebrow, trailing,
}: {
  title: string; subtitle?: string; eyebrow?: string; trailing?: ReactNode;
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

export function SnadPageFrame({
  title, subtitle, eyebrow, trailing, children,
}: {
  title: string; subtitle?: string; eyebrow?: string; trailing?: ReactNode; children: ReactNode;
}) {
  return (
    <div className={styles.contentInner}>
      <SnadPageHeader title={title} subtitle={subtitle} eyebrow={eyebrow} trailing={trailing} />
      {children}
    </div>
  );
}

export type SnadBadgeTone = "neutral" | "success" | "warning" | "danger" | "info";

const TONE_CLASS: Record<SnadBadgeTone, string> = {
  neutral: styles.statusBadgeNeutral,
  success: styles.statusBadgeSuccess,
  warning: styles.statusBadgeWarning,
  danger: styles.statusBadgeDanger,
  info: styles.statusBadgeInfo,
};

export function SnadStatusBadge({
  label, tone = "neutral", status, source, icon,
}: {
  label: string; tone?: SnadBadgeTone; status?: string; source?: string; icon?: ReactNode;
}) {
  return (
    <span className={`${styles.statusBadge} ${TONE_CLASS[tone]}`} data-status={status} data-source={source}>
      {icon ? <span aria-hidden="true">{icon}</span> : null}
      {label}
    </span>
  );
}

export function SnadLoadingState({ label }: { label: string }) {
  return (
    <div role="status" aria-busy="true" className={styles.loadingState}>
      <span className={styles.loadingSpinner} aria-hidden="true" />
      {label}
    </div>
  );
}

export function SnadEmptyState({
  title, description, hint, action, icon,
}: {
  title: string; description?: string; hint?: string; action?: ReactNode; icon?: ReactNode;
}) {
  return (
    <div className={styles.emptyState}>
      {icon ? <span className={styles.emptyIcon} aria-hidden="true">{icon}</span> : null}
      <p className={styles.emptyTitle}>{title}</p>
      {description ? <p className={styles.emptySubtitle}>{description}</p> : null}
      {hint ? <span className={styles.hint}>{hint}</span> : null}
      {action ? <div>{action}</div> : null}
    </div>
  );
}

export function SnadErrorState({
  message, kind = "generic", retryLabel, onRetry,
}: {
  message: string; kind?: string; retryLabel?: string; onRetry?: () => void;
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

export function SnadSuccessNotice({ children }: { children: ReactNode }) {
  return <p role="status" className={styles.successNotice}>{children}</p>;
}
