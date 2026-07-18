"use client";

import Link from "next/link";
import { useEffect } from "react";
import { useRouter } from "next/navigation";
import { useAuth } from "@/lib/auth/auth-provider";
import { AuthLoadingState } from "@/components/auth/auth-loading-state";
import { ExecutiveShell } from "@/components/shell";
import { useI18n } from "@/lib/i18n/I18nProvider";
import styles from "./workspace.module.css";

export default function WorkspacePage() {
  const { state, user, me, logout, availableDestinations } = useAuth();
  const router = useRouter();
  const { t } = useI18n();

  useEffect(() => {
    if (["ANONYMOUS", "ERROR", "EXPIRED", "CREDENTIAL_ROTATION_REQUIRED"].includes(state)) {
      router.replace("/?returnUrl=%2Fworkspace");
    }
  }, [router, state]);

  if (["INITIALIZING", "CHECKING_SESSION", "REFRESHING", "REFRESHING_SESSION", "LOGGING_OUT"].includes(state)) {
    return <AuthLoadingState phase="session" />;
  }
  if (state !== "AUTHENTICATED") return <AuthLoadingState phase="workspace" />;

  const displayName = me?.displayName || user?.email || t("workspace.defaultUser");
  const canOpenCrm = availableDestinations.includes("/crm");
  const canOpenCommandCenter = availableDestinations.includes("/crm/command-center");
  const canOpenControlPlane = availableDestinations.includes("/control-plane");

  return (
    <ExecutiveShell>
      <main className={styles.root}>
        <section className={styles.hero}>
          <div>
            <h1 className={styles.title}>{t("workspace.welcome", { name: displayName })}</h1>
            <p className={styles.subtitle}>{t("workspace.quickActions")}</p>
          </div>
          <div className={styles.sessionCard}>
            <span className={styles.sessionLabel}>{t("workspace.tenantInfo")}</span>
            <span className={styles.sessionValue}>{user?.tenantId ?? "—"}</span>
          </div>
        </section>

        <section aria-labelledby="workspace-applications">
          <h2 id="workspace-applications" className={styles.sectionTitle}>{t("nav.workspace")}</h2>
          <div className={styles.appGrid}>
            {canOpenCrm && (
              <Link className={styles.appCard} href="/crm" prefetch>
                <div>
                  <div className={styles.appName}>{t("workspace.openCrm")}</div>
                  <p className={styles.appDescription}>{t("crm.shell.subtitle")}</p>
                </div>
                <span className={styles.appAction}>{t("workspace.openCrm")}</span>
              </Link>
            )}
            {canOpenCommandCenter && (
              <Link className={styles.appCard} href="/crm/command-center" prefetch>
                <div>
                  <div className={styles.appName}>{t("workspace.openCrmCommandCenter")}</div>
                  <p className={styles.appDescription}>{t("workspace.overview")}</p>
                </div>
                <span className={styles.appAction}>{t("workspace.openCrmCommandCenter")}</span>
              </Link>
            )}
            {canOpenControlPlane && (
              <Link className={styles.appCard} href="/control-plane" prefetch>
                <div>
                  <div className={styles.appName}>{t("workspace.openControlPlane")}</div>
                  <p className={styles.appDescription}>{t("controlPlane.title")}</p>
                </div>
                <span className={styles.appAction}>{t("workspace.openControlPlane")}</span>
              </Link>
            )}
          </div>
        </section>

        <footer className={styles.footer}>
          <button
            type="button"
            className={styles.logout}
            onClick={async () => {
              await logout();
              router.replace("/");
            }}
          >
            {t("nav.logout")}
          </button>
        </footer>
      </main>
    </ExecutiveShell>
  );
}
