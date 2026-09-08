"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { AuthLoadingState } from "@/components/auth/auth-loading-state";
import { ExecutiveShell } from "@/components/shell";
import { useAuth } from "@/lib/auth/auth-provider";
import { WorkflowApprovals } from "./components/workflow-approvals";
import { WorkflowDefinitions } from "./components/workflow-definitions";
import { WorkflowIncidents } from "./components/workflow-incidents";
import { WorkflowInstances } from "./components/workflow-instances";
import { WorkflowMonitoring } from "./components/workflow-monitoring";
import { WorkflowMyTasks } from "./components/workflow-my-tasks";
import { WorkflowNav, type WorkflowSection } from "./components/workflow-nav";
import { WorkflowOverview } from "./components/workflow-overview";
import { WorkflowSettings } from "./components/workflow-settings";

function WorkflowSectionContent({ section }: { section: WorkflowSection }) {
  switch (section) {
    case "definitions":
      return <WorkflowDefinitions />;
    case "my-tasks":
      return <WorkflowMyTasks />;
    case "approvals":
      return <WorkflowApprovals />;
    case "instances":
      return <WorkflowInstances />;
    case "incidents":
      return <WorkflowIncidents />;
    case "monitoring":
      return <WorkflowMonitoring />;
    case "settings":
      return <WorkflowSettings />;
    case "overview":
    default:
      return <WorkflowOverview />;
  }
}

export default function WorkflowPage() {
  const router = useRouter();
  const { state, user } = useAuth();
  const [activeSection, setActiveSection] = useState<WorkflowSection>("overview");

  useEffect(() => {
    if (state !== "INITIALIZING" && state !== "CHECKING_SESSION" && !user) {
      router.push("/identity/login?from=/workflow");
    }
  }, [router, state, user]);

  const loading =
    state === "INITIALIZING" ||
    state === "CHECKING_SESSION" ||
    state === "AUTHENTICATING";

  if (loading || !user) return <AuthLoadingState />;

  return (
    <ExecutiveShell>
      <main
        dir="rtl"
        style={{
          maxWidth: 1280,
          margin: "0 auto",
          padding: "24px 16px",
          color: "var(--snad-color-text-primary)",
        }}
      >
        <header style={{ marginBottom: 20 }}>
          <h1 style={{ margin: 0, fontSize: 28, fontWeight: 700 }}>محرك سير العمل</h1>
          <p style={{ margin: "8px 0 0", color: "var(--snad-color-text-secondary)" }}>
            تشغيل ومتابعة سير العمل، المهام، الموافقات والحوادث من واجهة تشغيلية واحدة.
          </p>
        </header>

        <WorkflowNav value={activeSection} onChange={setActiveSection} />

        <section
          id={`workflow-panel-${activeSection}`}
          role="tabpanel"
          aria-label="محتوى قسم سير العمل"
          style={{ minWidth: 0 }}
        >
          <WorkflowSectionContent section={activeSection} />
        </section>
      </main>
    </ExecutiveShell>
  );
}
