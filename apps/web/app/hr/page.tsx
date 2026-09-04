"use client";

/**
 * HR Workspace home — WS5 Task 8.
 *
 * The former execution dashboard moved intact to /hr/execution (Step 1).
 * This page is now the Arabic-first authenticated workspace shell. In Task 8
 * it hosts the shared navigation; Task 10 upgrades it into the operational
 * HR dashboard backed by authoritative server summaries.
 *
 * Authorization note: capability checks here are UX-only. The backend
 * remains the authoritative authorization layer for every operation.
 */

import { useRouter } from "next/navigation";
import { AuthLoadingState } from "@/components/auth/auth-loading-state";
import { useAuth } from "@/lib/auth/auth-provider";
import { HrWorkspace } from "./components/hr-workspace";
import { HrForbidden } from "./components/hr-feedback";
import { HRM_CAPABILITIES } from "@/lib/auth/capabilities";

export default function HrPage() {
  const { state, me } = useAuth();
  const router = useRouter();

  if (["INITIALIZING", "CHECKING_SESSION", "REFRESHING"].includes(state))
    return <AuthLoadingState phase="session" />;
  if (state !== "AUTHENTICATED") {
    router.replace("/?returnUrl=%2Fhr");
    return <AuthLoadingState phase="workspace" />;
  }

  const capabilities = me?.capabilities ?? [];
  const canSeeHr = capabilities.includes(HRM_CAPABILITIES.EMPLOYEE_VIEW)
    || capabilities.includes(HRM_CAPABILITIES.ORG_STRUCTURE_VIEW)
    || capabilities.includes(HRM_CAPABILITIES.ASSIGNMENT_VIEW)
    || capabilities.includes(HRM_CAPABILITIES.EMPLOYEE_CREATE);

  return (
    <HrWorkspace capabilities={capabilities} activeHref="/hr">
      {canSeeHr ? (
        <section aria-label="نظرة عامة">
          <p style={{ color: "var(--snad-text-muted)", lineHeight: 1.7 }}>
            اختر قسمًا من الأعلى للبدء: إدارة سجلات الموظفين، الهيكل التنظيمي،
            الوظائف والمناصب، الإسنادات، ومتابعة حالة الالتزام النظامي.
          </p>
        </section>
      ) : (
        <HrForbidden />
      )}
    </HrWorkspace>
  );
}
