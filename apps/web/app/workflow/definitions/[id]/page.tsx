"use client";

import { use, useEffect } from "react";
import { useRouter } from "next/navigation";
import { WorkflowDesigner } from "./components/workflow-designer";
import { AuthLoadingState } from "@/components/auth/auth-loading-state";
import { useAuth } from "@/lib/auth/auth-provider";
import { ExecutiveShell } from "@/components/shell";
import styles from "../../workflow.module.css";

/**
 * Definition/version designer route. The server remains the authorization
 * boundary; this page owns authentication routing and the designer shell.
 */
export default function WorkflowDesignerPage({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const { id } = use(params);
  const router = useRouter();
  const { state, user } = useAuth();

  useEffect(() => {
    if (state !== "INITIALIZING" && state !== "CHECKING_SESSION" && !user) {
      router.replace(`/?returnUrl=${encodeURIComponent(`/workflow/definitions/${id}`)}`);
    }
  }, [id, router, state, user]);

  const isLoading =
    state === "INITIALIZING" ||
    state === "CHECKING_SESSION" ||
    state === "AUTHENTICATING";

  if (isLoading || !user) return <AuthLoadingState />;

  return (
    <ExecutiveShell>
      <div dir="rtl" className={styles.designerPage}>
        <div className={styles.breadcrumbBar}>
          <a className={styles.backLink} href="/workflow#definitions">
            ← العودة إلى تعريفات سير العمل
          </a>
          <span className={styles.metaChip}>مصمم Workflow Y2</span>
        </div>
        <WorkflowDesigner definitionId={id} />
      </div>
    </ExecutiveShell>
  );
}
