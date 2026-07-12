"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";
import { useAuth } from "@/lib/auth/auth-provider";
import { AuthLoadingState } from "@/components/auth/auth-loading-state";
import { ExecutiveShell } from "@/components/shell";
import { useI18n } from "@/lib/i18n/I18nProvider";
import styles from "@/components/auth/auth.module.css";

function shortenTenantId(id: string): string {
  if (!id) return "—";
  if (id.length <= 8) return id;
  return `•••• ${id.slice(-4).toUpperCase()}`;
}

export default function WorkspacePage() {
  const { state, user, me, logout } = useAuth();
  const router = useRouter();
  const { t } = useI18n();

  useEffect(() => {
    if (
      state === "ANONYMOUS" ||
      state === "ERROR" ||
      state === "EXPIRED" ||
      state === "CREDENTIAL_ROTATION_REQUIRED"
    ) {
      router.replace("/");
    }
  }, [state, router]);

  if (
    state === "INITIALIZING" ||
    state === "REFRESHING" ||
    state === "LOGGING_OUT"
  ) {
    return <AuthLoadingState />;
  }

  if (state !== "AUTHENTICATED") {
    return <AuthLoadingState />;
  }

  const displayName = me?.displayName || user?.email || t("workspace.defaultUser");
  const tenantId = user?.tenantId ?? "";
  const hasAdministrativeRole = me?.roleGrants.some(
    (grant) => grant.status === "ACTIVE" && grant.roleCode === "ADMIN",
  ) ?? false;

  return (
    <ExecutiveShell>
      <div className={styles.workspaceRoot}>
        <div className={styles.workspaceCard}>
          <svg
            className={styles.workspaceSuccessIcon}
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            strokeWidth="2"
            strokeLinecap="round"
            strokeLinejoin="round"
            aria-hidden="true"
          >
            <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14" />
            <polyline points="22 4 12 14.01 9 11.01" />
          </svg>

          <h1 className={styles.workspaceTitle}>{t("workspace.loginSuccess")}</h1>

          <div className={styles.workspaceInfo}>
            <div className={styles.workspaceInfoRow}>
              <span className={styles.workspaceInfoLabel}>{t("workspace.userInfo")}</span>
              <span className={styles.workspaceInfoValue}>{displayName}</span>
            </div>
            <div className={styles.workspaceInfoRow}>
              <span className={styles.workspaceInfoLabel}>{t("workspace.tenantInfo")}</span>
              <span className={styles.workspaceInfoValue}>
                {shortenTenantId(tenantId)}
              </span>
            </div>
            <div className={styles.workspaceInfoRow}>
              <span className={styles.workspaceInfoLabel}>{t("workspace.sessionStatus")}</span>
              <span className={styles.workspaceInfoValue}>{t("workspace.sessionActive")}</span>
            </div>
          </div>

          <button
            type="button"
            className={styles.workspacePrimaryButton}
            onClick={() => router.push("/crm")}
          >
            {t("workspace.openCrm")}
          </button>

          <button
            type="button"
            className={styles.workspaceLogoutButton}
            onClick={() => router.push("/crm/command-center")}
          >
            {t("workspace.openCrmCommandCenter")}
          </button>

          {hasAdministrativeRole ? (
            <button
              type="button"
              className={styles.workspaceLogoutButton}
              onClick={() => router.push("/control-plane")}
            >
              {t("workspace.openControlPlane")}
            </button>
          ) : null}

          <button
            type="button"
            className={styles.workspaceLogoutButton}
            onClick={async () => {
              await logout();
              router.replace("/");
            }}
          >
            {t("nav.logout")}
          </button>
        </div>
      </div>
    </ExecutiveShell>
  );
}
