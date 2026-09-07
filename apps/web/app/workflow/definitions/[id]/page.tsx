"use client";

import { use } from "react";
import { WorkflowDesigner } from "./components/workflow-designer";
import { AuthLoadingState } from "@/components/auth/auth-loading-state";
import { useAuth } from "@/lib/auth/auth-provider";
import { ExecutiveShell } from "@/components/shell";

/**
 * Definition/version designer route (design decision H3). The server stays
 * the authorization boundary; this page only hosts the designer surface.
 */
export default function WorkflowDesignerPage({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const { id } = use(params);
  const { state, user } = useAuth();

  const isLoading =
    state === "INITIALIZING" || state === "CHECKING_SESSION" || state === "AUTHENTICATING";
  if (isLoading || !user) return <AuthLoadingState />;

  return (
    <ExecutiveShell>
      <div style={{
        padding: "24px 16px", maxWidth: 1280, margin: "0 auto",
        direction: "rtl", fontFamily: "system-ui, -apple-system, sans-serif",
      }}>
        <WorkflowDesigner definitionId={id} />
      </div>
    </ExecutiveShell>
  );
}
