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
  // CRM: show if user has CRM capabilities OR control-plane access (admin)
  const canOpenCrm = availableDestinations.includes("/crm") || availableDestinations.includes("/control-plane");
  // HRM is capability-driven today; the auth destination catalog predates the
  // HRM-G0 route. Never show the launcher to an identity with no HRM grant.
  const canOpenHr = me?.capabilities?.some((capability) => capability.startsWith("HRM.")) ?? false;
  // /control-plane is a legacy compatibility route that redirects to /executive.
  // Render only the canonical Executive launcher to avoid duplicate destinations.
  const canOpenExecutive = true;
  const canOpenSystemHealth = true;

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
            {canOpenHr && (
              <Link className={styles.appCard} href="/hr" prefetch>
                <div>
                  <div className={styles.appName}>{t("workspace.openHr")}</div>
                  <p className={styles.appDescription}>{t("workspace.hrDescription")}</p>
                </div>
                <span className={styles.appAction}>{t("workspace.openHr")}</span>
              </Link>
            )}
            {canOpenExecutive && (
              <Link className={styles.appCard} href="/executive" prefetch>
                <div>
                  <div className={styles.appName}>{t("workspace.openExecutive")}</div>
                  <p className={styles.appDescription}>{t("workspace.executiveDescription")}</p>
                </div>
                <span className={styles.appAction}>{t("workspace.openExecutive")}</span>
              </Link>
            )}
            {canOpenSystemHealth && (
              <Link className={styles.appCard} href="/system-health" prefetch>
                <div>
                  <div className={styles.appName}>{t("workspace.openSystemHealth")}</div>
                  <p className={styles.appDescription}>{t("workspace.systemHealthDescription")}</p>
                </div>
                <span className={styles.appAction}>{t("workspace.openSystemHealth")}</span>
              </Link>
            )}
            <Link className={styles.appCard} href="/finance" prefetch>
              <div>
                <div className={styles.appName}>{t("workspace.openFinance")}</div>
                <p className={styles.appDescription}>{t("workspace.financeDescription")}</p>
              </div>
              <span className={styles.appAction}>{t("workspace.openFinance")}</span>
            </Link>
            <Link className={styles.appCard} href="/erp" prefetch>
              <div>
                <div className={styles.appName}>{t("workspace.openErp")}</div>
                <p className={styles.appDescription}>{t("workspace.erpDescription")}</p>
              </div>
              <span className={styles.appAction}>{t("workspace.openErp")}</span>
            </Link>
            <Link className={styles.appCard} href="/workflow" prefetch>
              <div>
                <div className={styles.appName}>{t("workspace.openWorkflow")}</div>
                <p className={styles.appDescription}>{t("workspace.workflowDescription")}</p>
              </div>
              <span className={styles.appAction}>{t("workspace.openWorkflow")}</span>
            </Link>
            <Link className={styles.appCard} href="/analytics" prefetch>
              <div>
                <div className={styles.appName}>{t("workspace.openAnalytics")}</div>
                <p className={styles.appDescription}>{t("workspace.analyticsDescription")}</p>
              </div>
              <span className={styles.appAction}>{t("workspace.openAnalytics")}</span>
            </Link>
            <Link className={styles.appCard} href="/ai-platform" prefetch>
              <div>
                <div className={styles.appName}>{t("workspace.openAiPlatform")}</div>
                <p className={styles.appDescription}>{t("workspace.aiPlatformDescription")}</p>
              </div>
              <span className={styles.appAction}>{t("workspace.openAiPlatform")}</span>
            </Link>
            <Link className={styles.appCard} href="/stores" prefetch>
              <div>
                <div className={styles.appName}>{t("workspace.openStores")}</div>
                <p className={styles.appDescription}>{t("workspace.storesDescription")}</p>
              </div>
              <span className={styles.appAction}>{t("workspace.openStores")}</span>
            </Link>
            <Link className={styles.appCard} href="/websites" prefetch>
              <div>
                <div className={styles.appName}>{t("workspace.openWebsites")}</div>
                <p className={styles.appDescription}>{t("workspace.websitesDescription")}</p>
              </div>
              <span className={styles.appAction}>{t("workspace.openWebsites")}</span>
            </Link>
            <Link className={styles.appCard} href="/management" prefetch>
              <div>
                <div className={styles.appName}>{t("workspace.openManagement")}</div>
                <p className={styles.appDescription}>{t("workspace.managementDescription")}</p>
              </div>
              <span className={styles.appAction}>{t("workspace.openManagement")}</span>
            </Link>
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