"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";
import { useAuth } from "@/lib/auth/auth-provider";
import { AuthLoadingState } from "@/components/auth/auth-loading-state";
import styles from "@/components/auth/auth.module.css";

function shortenTenantId(id: string): string {
  if (!id) return "—";
  if (id.length <= 8) return id;
  return `•••• ${id.slice(-4).toUpperCase()}`;
}

export default function WorkspacePage() {
  const { state, user, me, logout } = useAuth();
  const router = useRouter();

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

  const displayName = me?.displayName || user?.email || "المستخدم";
  const tenantId = user?.tenantId ?? "";
  const hasAdministrativeRole = me?.roleGrants.some(
    (grant) => grant.status === "ACTIVE" && grant.roleCode === "ADMIN",
  ) ?? false;

  return (
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

        <h1 className={styles.workspaceTitle}>تم تسجيل الدخول بنجاح</h1>

        <div className={styles.workspaceInfo}>
          <div className={styles.workspaceInfoRow}>
            <span className={styles.workspaceInfoLabel}>المستخدم</span>
            <span className={styles.workspaceInfoValue}>{displayName}</span>
          </div>
          <div className={styles.workspaceInfoRow}>
            <span className={styles.workspaceInfoLabel}>مساحة العمل</span>
            <span className={styles.workspaceInfoValue}>
              {shortenTenantId(tenantId)}
            </span>
          </div>
          <div className={styles.workspaceInfoRow}>
            <span className={styles.workspaceInfoLabel}>حالة الجلسة</span>
            <span className={styles.workspaceInfoValue}>نشطة</span>
          </div>
        </div>

        {hasAdministrativeRole ? (
          <button
            type="button"
            className={styles.workspaceLogoutButton}
            onClick={() => router.push("/control-plane")}
          >
            فتح مركز الإدارة العليا
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
          تسجيل الخروج
        </button>
      </div>
    </div>
  );
}
