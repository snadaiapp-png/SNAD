"use client";

import type { ReactNode } from "react";
import { useAuth } from "@/lib/auth/auth-provider";
import { AuthLoadingState } from "@/components/auth/auth-loading-state";
import {
  SnadEmptyState,
  SnadErrorState,
  SnadLoadingState,
  SnadPageFrame,
  SnadStatusBadge,
  SnadSuccessNotice,
  type SnadBadgeTone,
} from "@/components/sds/module/SnadSurfaces";
import { useI18n } from "@/lib/i18n/I18nProvider";

/**
 * Client auth gate for every control-plane page.
 * Session authorization remains independent from visual-shell migration.
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
    <SnadPageFrame title={title} subtitle={subtitle}>
      {children}
    </SnadPageFrame>
  );
}

export function ScpSkeleton({ lines: _lines = 6 }: { lines?: number }) {
  const { t } = useI18n();
  return <SnadLoadingState label={t("scp.state.loading")} />;
}

export function ScpError({ message, onRetry }: { message: string; onRetry?: () => void }) {
  const { t } = useI18n();
  return (
    <SnadErrorState
      message={message || t("scp.state.errorGeneric")}
      retryLabel={t("scp.state.retry")}
      onRetry={onRetry}
    />
  );
}

export function ScpEmpty({ message }: { message: string }) {
  return <SnadEmptyState title={message} />;
}

export function ScpNotice({ children }: { children: ReactNode }) {
  return <SnadSuccessNotice>{children}</SnadSuccessNotice>;
}

/** Status chip with tone derived from the value (data-driven, no hardcoding). */
export function ScpStatusPill({ value }: { value: string }) {
  const normalized = value?.toUpperCase() ?? "";
  const positive = ["ACTIVE", "PAID", "SUCCEEDED", "TRIAL", "TRIALING", "CURRENT"];
  const warning = ["PAST_DUE", "PENDING", "PENDING_PAYMENT", "PENDING_ACTIVATION", "RETRYING", "GRACE_PERIOD", "DRAFT", "PAUSED"];
  const negative = ["SUSPENDED", "CANCELLED", "EXPIRED", "TERMINATED", "FAILED", "VOID"];
  const tone: SnadBadgeTone = positive.includes(normalized)
    ? "success"
    : warning.includes(normalized)
      ? "warning"
      : negative.includes(normalized)
        ? "danger"
        : "neutral";

  return <SnadStatusBadge label={value} tone={tone} status={normalized} />;
}
