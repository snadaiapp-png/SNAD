import { describe, expect, it } from "vitest";
import { readFileSync, existsSync } from "node:fs";

const pageSource = readFileSync(new URL("../page.tsx", import.meta.url), "utf8");

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

describe("workflow Y2 operational IA (Task 17)", () => {
  it("renders all Y2 workflow destinations", () => {
    for (const label of DESTINATIONS) {
      expect(pageSource).toContain(label);
    }
  });

  it("routes the eight sections to focused components or legacy tabs", () => {
    expect(pageSource).toContain("<WorkflowOverview />");
    expect(pageSource).toContain("<WorkflowMyTasks />");
    expect(pageSource).toContain("<WorkflowIncidents />");
    expect(pageSource).toContain("<WorkflowSettings />");
    expect(pageSource).toContain("<DefinitionsTab />");
    expect(pageSource).toContain("<ApprovalsTab />");
    expect(pageSource).toContain("<InstancesTab />");
    expect(pageSource).toContain("<MonitoringTab />");
  });

  it("keeps RTL as a first-class layout direction", () => {
    expect(pageSource).toContain('direction: "rtl"');
    const rtlDocument = readFileSync(
      new URL("../components/workflow-rtl-document.tsx", import.meta.url),
      "utf8",
    );
    expect(rtlDocument).toContain('document.documentElement.dir = "rtl"');
    expect(rtlDocument).toContain('document.documentElement.lang = "ar"');
  });

  it("backs My Tasks and Incidents with the real API client", () => {
    const myTasks = readFileSync(
      new URL("../components/workflow-my-tasks.tsx", import.meta.url),
      "utf8",
    );
    expect(myTasks).toContain("workflowApi.listMyWorkItems");
    expect(myTasks).toContain("workflowApi.claimWorkItem");
    expect(myTasks).toContain("expectedVersion");

    const incidents = readFileSync(
      new URL("../components/workflow-incidents.tsx", import.meta.url),
      "utf8",
    );
    expect(incidents).toContain("workflowApi.listIncidents");
    expect(incidents).toContain("سبب الحل مطلوب");
  });

  it("keeps the legacy four-tab baseline alive during cutover", () => {
    expect(pageSource).toContain('"definitions"');
    expect(existsSync(new URL("../components/workflow-nav.tsx", import.meta.url))).toBe(true);
  });
});
