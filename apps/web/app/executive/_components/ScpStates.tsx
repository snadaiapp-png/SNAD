"use client";

import type { ReactNode } from "react";
import { useAuth } from "@/lib/auth/auth-provider";
import { AuthLoadingState } from "@/components/auth/auth-loading-state";
import { Button } from "@/components/sds";
import { useI18n } from "@/lib/i18n/I18nProvider";
import styles from "../scp.module.css";

/**
 * Client auth gate for every control-plane page — mirrors the legacy
 * console's in-page guard (executive pages render only when the session
 * state machine reaches AUTHENTICATED).
 */
export function ScpAuthGate({ children }: { children: ReactNode }) {
  const { state } = useAuth();
  if (state !== "AUTHENTICATED") {
    return <AuthLoadingState phase="session" />;
  }
  return <>{children}</>;
}

export function ScpPage({
  title,
  subtitle,
  children,
}: {
  title: string;
  subtitle?: string;
  children: ReactNode;
}) {
  return (
    <section className={styles.main} aria-busy="false">
      <header>
        <h1 className={styles.pageTitle}>{title}</h1>
        {subtitle ? <p className={styles.pageSubtitle}>{subtitle}</p> : null}
      </header>
      {children}
    </section>
  );
}

export function ScpSkeleton({ lines = 6 }: { lines?: number }) {
  return (
    <div className={styles.panel} role="status" aria-live="polite">
      {Array.from({ length: lines }, (_, index) => (
        <span key={index} className={styles.skeletonLine} style={{ inlineSize: `${90 - index * 9}%` }} />
      ))}
    </div>
  );
}

export function ScpError({ message, onRetry }: { message: string; onRetry?: () => void }) {
  const { t } = useI18n();
  return (
    <div className={`${styles.stateBox} ${styles.stateError}`} role="alert">
      <p>{message || t("scp.state.errorGeneric")}</p>
      {onRetry ? (
        <Button variant="secondary" size="sm" onClick={onRetry}>
          {t("scp.state.retry")}
        </Button>
      ) : null}
    </div>
  );
}

export function ScpEmpty({ message }: { message: string }) {
  return (
    <div className={styles.stateBox} role="status">
      <p>{message}</p>
    </div>
  );
}

export function ScpNotice({ children }: { children: ReactNode }) {
  return (
    <div className={styles.stateBox} role="status" aria-live="polite">
      {children}
    </div>
  );
}

/** Status chip with tone derived from the value (data-driven, no hardcoding). */
export function ScpStatusPill({ value }: { value: string }) {
  const normalized = value?.toUpperCase() ?? "";
  const positive = ["ACTIVE", "PAID", "SUCCEEDED", "TRIAL", "TRIALING", "CURRENT"];
  const warning = ["PAST_DUE", "PENDING", "PENDING_PAYMENT", "PENDING_ACTIVATION", "RETRYING", "GRACE_PERIOD", "DRAFT", "PAUSED"];
  const negative = ["SUSPENDED", "CANCELLED", "EXPIRED", "TERMINATED", "FAILED", "VOID"];
  const tone = positive.includes(normalized)
    ? "positive"
    : warning.includes(normalized)
      ? "warning"
      : negative.includes(normalized)
        ? "negative"
        : "neutral";
  return (
    <span className={styles.statusPill} data-tone={tone}>
      {value}
    </span>
  );
}
