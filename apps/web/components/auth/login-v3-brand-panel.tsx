"use client";

import { useI18n } from "@/lib/i18n/I18nProvider";
import styles from "./login-v3.module.css";

export function LoginV3BrandPanel() {
  const { t } = useI18n();

  return (
    <aside className={styles.brandPanel} aria-label={t("brand.fullName")}>
      <div className={styles.brandAmbient} aria-hidden="true" />
      <div className={styles.brandContent}>
        <p className={styles.brandKicker}>{t("brand.fullName")}</p>
        <h2 className={styles.brandHeadline}>{t("brand.tagline")}</h2>
        <p className={styles.brandBody}>{t("auth.login.welcomeSubtitle")}</p>
      </div>
    </aside>
  );
}
