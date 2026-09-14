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
import styles from "./workflow.module.css";

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

const SECTION_KEYS: WorkflowSection[] = [
  "overview",
  "definitions",
  "my-tasks",
  "approvals",
  "instances",
  "incidents",
  "monitoring",
  "settings",
];

function sectionFromHash(): WorkflowSection | null {
  if (typeof window === "undefined") return null;
  const value = window.location.hash.replace(/^#/, "") as WorkflowSection;
  return SECTION_KEYS.includes(value) ? value : null;
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

  useEffect(() => {
    const syncFromHash = () => {
      const section = sectionFromHash();
      if (section) setActiveSection(section);
    };
    syncFromHash();
    window.addEventListener("hashchange", syncFromHash);
    return () => window.removeEventListener("hashchange", syncFromHash);
  }, []);

  const changeSection = (section: WorkflowSection) => {
    setActiveSection(section);
    if (typeof window !== "undefined") {
      window.history.replaceState(null, "", `#${section}`);
    }
  };

  const loading =
    state === "INITIALIZING" ||
    state === "CHECKING_SESSION" ||
    state === "AUTHENTICATING";

  if (loading || !user) return <AuthLoadingState />;

  return (
    <ExecutiveShell>
      <div dir="rtl" className={styles.workspace}>
        <header className={styles.hero}>
          <span className={styles.heroKicker}>مركز التشغيل والحوكمة</span>
          <h1 className={styles.heroTitle}>محرك سير العمل</h1>
          <p className={styles.heroDescription}>
            مساحة تشغيل موحّدة لمتابعة التعريفات والمهام والموافقات والمثيلات والحوادث
            وصحة التشغيل، مع إبقاء التفويض والقرارات الحساسة خاضعة للخادم.
          </p>
          <div className={styles.heroMeta} aria-label="قدرات مساحة العمل">
            <span className={styles.metaChip}>تشغيل يومي</span>
            <span className={styles.metaChip}>مراقبة SLA</span>
            <span className={styles.metaChip}>حوكمة الإصدارات</span>
          </div>
        </header>

        <WorkflowNav value={activeSection} onChange={changeSection} />

        <section
          id={`workflow-panel-${activeSection}`}
          role="tabpanel"
          aria-labelledby={`workflow-tab-${activeSection}`}
          className={styles.sectionSurface}
        >
          <WorkflowSectionContent section={activeSection} />
        </section>
      </div>
    </ExecutiveShell>
  );
}
