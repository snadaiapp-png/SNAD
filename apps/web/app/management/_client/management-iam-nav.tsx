"use client";

import Link from "next/link";
import { useAuth } from "@/lib/auth/auth-provider";
import { useI18n } from "@/lib/i18n/I18nProvider";
import styles from "./management-iam-nav.module.css";

export function ManagementIamNav() {
  const { me } = useAuth();
  const { t } = useI18n();
  const capabilities = me?.capabilities ?? [];
  const canReadUsers = capabilities.includes("USER.READ");
  const canReadAccess = capabilities.includes("ROLE.READ") && capabilities.includes("CAPABILITY.READ");

  if (!canReadUsers && !canReadAccess) return null;

  return (
    <nav className={styles.nav} aria-label={t("management.iam.label")}>
      {canReadUsers ? <Link className={styles.link} href="/management/users">{t("management.iam.users")}</Link> : null}
      {canReadAccess ? <Link className={styles.link} href="/management/access">{t("management.iam.access")}</Link> : null}
    </nav>
  );
}
