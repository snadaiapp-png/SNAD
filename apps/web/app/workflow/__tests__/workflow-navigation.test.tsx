import { describe, expect, it } from "vitest";
import { existsSync, readFileSync } from "node:fs";

const workflowUrl = (relative: string) => new URL(`../${relative}`, import.meta.url);

function readRequired(relative: string) {
  const url = workflowUrl(relative);
  expect(existsSync(url), `${relative} must exist`).toBe(true);
  return existsSync(url) ? readFileSync(url, "utf8") : "";
}

const DESTINATIONS = [
  "نظرة عامة",
  "التعريفات",
  "مهامي",
  "الموافقات",
  "المثيلات",
  "الحوادث",
  "المراقبة",
  "الإعدادات",
];

const FOCUSED_COMPONENTS = [
  "components/workflow-overview.tsx",
  "components/workflow-definitions.tsx",
  "components/workflow-my-tasks.tsx",
  "components/workflow-approvals.tsx",
  "components/workflow-instances.tsx",
  "components/workflow-incidents.tsx",
  "components/workflow-monitoring.tsx",
  "components/workflow-settings.tsx",
];

describe("workflow Y2 operational IA (Task 17)", () => {
  it("routes all eight destinations through focused components", () => {
    const pageSource = readRequired("page.tsx");
    const navSource = readRequired("components/workflow-nav.tsx");

    for (const label of DESTINATIONS) {
      expect(navSource).toContain(label);
    }
    for (const component of FOCUSED_COMPONENTS) {
      expect(existsSync(workflowUrl(component)), `${component} must exist`).toBe(true);
    }

    for (const componentName of [
      "WorkflowOverview",
      "WorkflowDefinitions",
      "WorkflowMyTasks",
      "WorkflowApprovals",
      "WorkflowInstances",
      "WorkflowIncidents",
      "WorkflowMonitoring",
      "WorkflowSettings",
    ]) {
      expect(pageSource).toContain(`<${componentName} />`);
    }

    expect(pageSource).not.toContain("function DefinitionsTab");
    expect(pageSource).not.toContain("function ApprovalsTab");
    expect(pageSource).not.toContain("function InstancesTab");
    expect(pageSource).not.toContain("function MonitoringTab");
  });

  it("keeps auth, RTL, and the approved navigation shell in page.tsx", () => {
    const pageSource = readRequired("page.tsx");
    expect(pageSource).toContain("useAuth");
    expect(pageSource).toContain("AuthLoadingState");
    expect(pageSource).toContain("ExecutiveShell");
    expect(pageSource).toContain("WorkflowNav");
    expect(pageSource).toContain('dir="rtl"');
  });

  it("uses real versioned task and approval mutations", () => {
    const myTasks = readRequired("components/workflow-my-tasks.tsx");
    expect(myTasks).toContain("workflowApi.listMyWorkItems");
    expect(myTasks).toContain("workflowApi.claimWorkItem");
    expect(myTasks).toContain("workItem.version");

    const approvals = readRequired("components/workflow-approvals.tsx");
    expect(approvals).toContain("workflowApi.listPendingApprovals");
    expect(approvals).toContain("workflowApi.approveRequest");
    expect(approvals).toContain("workflowApi.rejectRequest");
    expect(approvals).toContain("approval.version");
    expect(approvals).toContain("سبب الرفض مطلوب");
  });

  it("surfaces authorization and stale-version conflicts instead of authorizing in UI", () => {
    const approvals = readRequired("components/workflow-approvals.tsx");
    const incidents = readRequired("components/workflow-incidents.tsx");
    const myTasks = readRequired("components/workflow-my-tasks.tsx");

    for (const source of [approvals, incidents, myTasks]) {
      expect(source).toContain("describeWorkflowError");
    }
    expect(approvals).toContain("409");
    expect(approvals).toContain("403");
  });

  it("keeps legacy activation out of the Y2 publication lifecycle", () => {
    const definitions = readRequired("components/workflow-definitions.tsx");

    expect(definitions).toContain(
      'definition.engineGeneration === "LEGACY" && definition.publicationState === "DRAFT" && definition.status === "DRAFT"',
    );
    expect(definitions).toContain("workflowApi.activateDefinition");
    expect(definitions).toContain("فتح المصمم");
  });
});
