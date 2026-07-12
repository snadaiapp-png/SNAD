"use client";

import { useI18n } from "@/lib/i18n/I18nProvider";
import styles from "../crm.module.css";

interface CrmErrorProps {
  /** Optional custom message. Falls back to the provided error's message. */
  message?: string;
  /** Optional title override. */
  title?: string;
  /** Called when the user clicks Retry. If omitted, the button is hidden. */
  onRetry?: () => void;
}

/**
 * CrmError — Inline error state with optional retry CTA.
 *
 * Use this when a CRM page fails to load its primary data. The component
 * renders a single, focused error block (no lists, no tables) so the user
 * can either retry or navigate away.
 */
export function CrmError({ message, title, onRetry }: CrmErrorProps) {
  const { t } = useI18n();
  const heading = title ?? t("crm.state.errorTitle");
  const body = message ?? t("crm.state.operationFailed");
  return (
    <div className={styles.root} role="alert">
      <div className={styles.error} aria-live="assertive">
        <strong>{heading}</strong>
        <p>{body}</p>
      </div>
      {onRetry ? (
        <button type="button" onClick={onRetry}>
          {t("crm.state.retry")}
        </button>
      ) : null}
    </div>
  );
}
