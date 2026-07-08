"use client";

import { useState } from "react";
import styles from "./auth.module.css";
import { useI18n } from "@/lib/i18n/I18nProvider";

interface TenantPickerProps {
  tenantIds: string[];
  onSelect: (tenantId: string) => Promise<void>;
  onDismiss: () => void;
  authenticating: boolean;
}

function shortenTenantId(id: string): string {
  if (id.length <= 8) return id;
  return `•••• ${id.slice(-4).toUpperCase()}`;
}

export function TenantPicker({
  tenantIds,
  onSelect,
  onDismiss,
  authenticating,
}: TenantPickerProps) {
  const [selected, setSelected] = useState("");
  const { t } = useI18n();

  return (
    <div className={styles.authShell}>
      <AuthIntelligenceVisualStatic />
      <div className={styles.loginPanel}>
        <div className={styles.loginCard}>
          <div className={styles.loginBrandMark}>SNAD</div>
          <h1 className={styles.loginWelcomeTitle}>{t("auth.tenant.welcomeTitle")}</h1>
          <p className={styles.loginWelcomeSubtitle}>
            {t("auth.tenant.welcomeSubtitle")}
          </p>

          <div className={styles.tenantList} role="radiogroup" aria-label={t("auth.tenant.workspaceLabel")}>
            {tenantIds.map((id) => (
              <label
                key={id}
                className={styles.tenantOption}
                aria-selected={selected === id}
              >
                <input
                  type="radio"
                  name="tenant"
                  value={id}
                  checked={selected === id}
                  onChange={() => setSelected(id)}
                  className={styles.tenantRadio}
                  disabled={authenticating}
                />
                <span className={styles.tenantLabel}>
                  {t("auth.tenant.workspacePrefix")} {shortenTenantId(id)}
                </span>
              </label>
            ))}
          </div>

          <button
            type="button"
            className={styles.authSubmit}
            disabled={!selected || authenticating}
            onClick={() => selected && onSelect(selected)}
            aria-busy={authenticating}
          >
            {authenticating ? t("auth.tenant.entering") : t("auth.tenant.continue")}
          </button>

          <button
            type="button"
            className={styles.authBackLink}
            onClick={onDismiss}
            disabled={authenticating}
          >
            {t("auth.tenant.backToLogin")}
          </button>
        </div>
      </div>
    </div>
  );
}

/** Static import to avoid circular dependency warning */
import { AuthIntelligenceVisual as AuthIntelligenceVisualStatic } from "./auth-intelligence-visual";
