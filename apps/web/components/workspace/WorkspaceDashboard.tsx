"use client";

import dynamic from "next/dynamic";
import { useEffect } from "react";
import { useRouter } from "next/navigation";
import { useAuth } from "@/lib/auth/auth-provider";
import { emitAuthMetric } from "@/lib/observability/auth-performance";
import styles from "./workspace.module.css";

const WorkspaceSecondaryPanels = dynamic(
  () => import("./WorkspaceSecondaryPanels")
    .then((module) => module.WorkspaceSecondaryPanels),
  {
    ssr: false,
    loading: () => (
      <section className={styles.secondarySkeleton} aria-label="جارٍ تحميل البيانات الثانوية">
        <div /><div /><div />
      </section>
    ),
  },
);

export function WorkspaceDashboard() {
  const { user, me, logout } = useAuth();
  const router = useRouter();

  useEffect(() => {
    emitAuthMetric({ event: "workspace_shell_rendered", outcome: "success" });
    const frame = requestAnimationFrame(() => {
      emitAuthMetric({ event: "workspace_interactive", outcome: "success" });
    });
    return () => cancelAnimationFrame(frame);
  }, []);

  const displayName = me?.displayName || user?.displayName || user?.email || "المستخدم";
  const activeRoles = me?.roleGrants.filter((grant) => grant.status === "ACTIVE") ?? [];
  const activeMemberships = me?.memberships.filter((membership) => membership.status === "ACTIVE") ?? [];
  const isAdmin = activeRoles.some((grant) => grant.roleCode === "ADMIN");

  return (
    <div className={styles.root}>
      <section className={styles.hero} aria-labelledby="workspace-title">
        <div>
          <p className={styles.eyebrow}>SNAD BUSINESS OPERATING SYSTEM</p>
          <h1 id="workspace-title">مرحبًا، {displayName}</h1>
          <p>تم تجهيز المساحة الحرجة فورًا، بينما تُحمّل البيانات الثانوية تدريجيًا دون حجب العمل.</p>
        </div>
        <div className={styles.actions}>
          {isAdmin ? (
            <button type="button" onClick={() => router.push("/control-plane")}>مركز الإدارة العليا</button>
          ) : null}
          <button
            type="button"
            className={styles.secondaryAction}
            onClick={async () => {
              await logout();
              router.replace("/");
            }}
          >
            تسجيل الخروج
          </button>
        </div>
      </section>

      <section className={styles.criticalGrid} aria-label="ملخص مساحة العمل">
        <article><span>حالة الجلسة</span><strong>نشطة وآمنة</strong></article>
        <article><span>العضويات النشطة</span><strong>{activeMemberships.length}</strong></article>
        <article><span>الأدوار النشطة</span><strong>{activeRoles.length}</strong></article>
        <article>
          <span>المستأجر</span>
          <strong>{user?.tenantId ? `•••• ${user.tenantId.slice(-4).toUpperCase()}` : "—"}</strong>
        </article>
      </section>

      <WorkspaceSecondaryPanels />
    </div>
  );
}
