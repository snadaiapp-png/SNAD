"use client";

import { useEffect, useState } from "react";
import { useI18n } from "@/lib/i18n/I18nProvider";
import styles from "./auth.module.css";

type LoadingPhase = "session" | "workspace" | "refresh" | "logout";

interface AuthLoadingStateProps {
  phase?: LoadingPhase;
  title?: string;
  subtitle?: string;
}

function DelayedProgress({ initial, delayed }: { initial: string; delayed: string }) {
  const [isSlow, setIsSlow] = useState(false);
  useEffect(() => {
    const timer = window.setTimeout(() => setIsSlow(true), 3000);
    return () => window.clearTimeout(timer);
  }, []);
  return <p className={styles.authLoadingSubtitle}>{isSlow ? delayed : initial}</p>;
}

export function AuthLoadingState({
  phase = "workspace",
  title,
  subtitle,
}: AuthLoadingStateProps) {
  const { t } = useI18n();
  const defaults: Record<LoadingPhase, { title: string; subtitle: string }> = {
    session: { title: t("auth.loading.restoring"), subtitle: t("crm.shell.loading") },
    workspace: { title: t("auth.loading.redirecting"), subtitle: t("loading.data") },
    refresh: { title: t("auth.loading.restoring"), subtitle: t("loading.processing") },
    logout: { title: t("nav.logout"), subtitle: t("loading.processing") },
  };
  const resolved = defaults[phase];
  const initial = subtitle ?? resolved.subtitle;

  return (
    <div className={styles.authLoadingRoot} role="status" aria-live="polite" aria-busy="true">
      <div className={styles.authLoadingCard}>
        <div className={styles.authSpinner} aria-hidden="true" />
        <p className={styles.authLoadingTitle}>{title ?? resolved.title}</p>
        <DelayedProgress key={`${phase}:${initial}`} initial={initial} delayed={t("loading.processing")} />
      </div>
    </div>
  );
}
