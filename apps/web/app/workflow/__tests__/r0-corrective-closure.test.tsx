import { describe, expect, it } from "vitest";
import { existsSync, readFileSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

/**
 * R0 corrective closure — web source-contract coverage.
 *
 * R0.G1 version semantics: business versions render as v{version}; row lock
 * counters (versionLock / instance version / step version) must never be
 * presented as semantic versions.
 * R0.G4: My Tasks mine/pool datasets are failure-isolated with independent
 * retries and precise per-dataset access states.
 * R0.G5: Definitions workspace groups families, offers search/filters and
 * version history.
 * R0.G6: Designer communicates SOURCE -> OUTCOME -> DESTINATION with edge
 * labels, mirrors the grid for RTL, treats fork/join distinctly, filters
 * outcomes by step type (TIMEOUT included), and stacks responsively.
 * R0.G8: system canaries are classified and separated in the workspace.
 */
const here = path.dirname(fileURLToPath(import.meta.url));
const workflowDir = path.resolve(here, "..");

function readRequired(relative: string) {
  const target = path.resolve(workflowDir, relative);
  expect(existsSync(target), `${relative} must exist`).toBe(true);
  return existsSync(target) ? readFileSync(target, "utf8") : "";
}

describe("R0.G1 — version semantics", () => {
  const definitions = readRequired("components/workflow-definitions.tsx");
  const instances = readRequired("components/workflow-instances.tsx");
  const myTasks = readRequired("components/workflow-my-tasks.tsx");
  const approvals = readRequired("components/workflow-approvals.tsx");
  const incidents = readRequired("components/workflow-incidents.tsx");
  const stepInspector = readRequired("definitions/[id]/components/step-inspector.tsx");

  it("never composes v{version}.{versionLock} in the definitions workspace", () => {
    expect(definitions).not.toContain("v{definition.version}.{definition.versionLock}");
    expect(definitions).not.toContain(".{definition.versionLock}");
    expect(definitions).toContain("`v${family.publishedVersion}`");
    expect(definitions).toContain("`v${family.draftVersion}`");
  });

  it("never composes the instance row lock into the displayed version", () => {
    expect(instances).not.toContain("v{instance.workflowVersion}.{instance.version}");
    expect(instances).toContain("v{instance.workflowVersion}");
  });

  it("never labels row lock counters as versions in operational views", () => {
    expect(myTasks).toContain("مرجع المزامنة #{workItem.version}");
    expect(myTasks).not.toContain("إصدار {workItem.version}");
    expect(approvals).toContain("مرجع المزامنة #{approval.version}");
    expect(approvals).not.toContain("إصدار {approval.version}");
    expect(incidents).toContain("مرجع المزامنة #{incident.version}");
    expect(incidents).not.toContain("إصدار {incident.version}");
    expect(stepInspector).toContain("مرجع المزامنة #{selectedStep.version}");
    expect(stepInspector).not.toContain("v{selectedStep.version}");
  });
});

describe("R0.G4 — My Tasks partial-failure resilience", () => {
  const myTasks = readRequired("components/workflow-my-tasks.tsx");

  it("fetches mine and pool independently with failure isolation", () => {
    expect(myTasks).toContain("const loadMine");
    expect(myTasks).toContain("const loadPool");
    // The forbidden coupled-fetch pattern is gone.
    expect(myTasks).not.toContain("Promise.all([\n      workflowApi.listMyWorkItems");
    expect(myTasks).toContain("loadMine()");
    expect(myTasks).toContain("loadPool()");
  });

  it("exposes per-dataset error banners and independent retries", () => {
    expect(myTasks).toContain('testId="error-mine"');
    expect(myTasks).toContain('testId="error-pool"');
    expect(myTasks).toContain("إعادة محاولة المهام المباشرة");
    expect(myTasks).toContain("إعادة محاولة تجمع المهام");
    expect(myTasks).toContain("bothDenied");
    expect(myTasks).toContain('data-testid="both-denied"');
    expect(myTasks).toContain("WORKFLOW.TASK_EXECUTE");
  });
});

describe("R0.G5 — Definitions workspace", () => {
  const definitions = readRequired("components/workflow-definitions.tsx");

  it("groups logical versions into families", () => {
    expect(definitions).toContain("definitionFamilyId");
    expect(definitions).toContain("buildFamilies");
    expect(definitions).toContain("publishedVersion");
    expect(definitions).toContain("draftVersion");
  });

  it("provides search, module/state/engine/classification filters", () => {
    expect(definitions).toContain('aria-label="بحث"');
    expect(definitions).toContain('aria-label="تصفية الوحدة"');
    expect(definitions).toContain('aria-label="تصفية حالة النشر"');
    expect(definitions).toContain('aria-label="تصفية المحرك"');
    expect(definitions).toContain('aria-label="تصفية التصنيف"');
  });

  it("exposes version history through the family endpoint", () => {
    expect(definitions).toContain("listDefinitionVersions");
    expect(definitions).toContain("سجل الإصدارات");
    expect(definitions).toContain("آخر تحديث");
  });
});

describe("R0.G6 — Designer semantics", () => {
  const designer = readRequired("definitions/[id]/components/workflow-designer.tsx");
  const inspector = readRequired("definitions/[id]/components/step-inspector.tsx");

  it("labels every canvas edge with its outcome (SOURCE -> OUTCOME -> DESTINATION)", () => {
    expect(designer).toContain("data-testid={`edge-label-${transition.transitionKey}`}");
    expect(designer).toContain("{transition.outcome || transition.transitionKey}");
    expect(designer).toContain("markerEnd");
  });

  it("mirrors the node grid for RTL presentation", () => {
    expect(designer).toContain("3 - (index % 4)");
  });

  it("gives fork/join nodes a distinct visual treatment", () => {
    expect(designer).toContain("PARALLEL_FORK");
    expect(designer).toContain("PARALLEL_JOIN");
    expect(designer).toContain('borderStyle: "dashed"');
  });

  it("filters outcome vocabulary by step type and includes TIMEOUT", () => {
    expect(inspector).toContain("outcomeOptionsFor");
    expect(inspector).toContain('case "CONDITION":');
    expect(inspector).toContain('case "APPROVAL":');
    expect(inspector).toContain('"TIMEOUT"');
  });

  it("stacks the designer layout responsively", () => {
    expect(designer).toContain("repeat(auto-fit, minmax(280px, 1fr))");
  });
});

describe("R0.G8 — system canary separation", () => {
  const definitions = readRequired("components/workflow-definitions.tsx");

  it("renders system canaries in a separate classified section", () => {
    expect(definitions).toContain("SYSTEM_CANARY");
    expect(definitions).toContain('data-testid="system-canaries"');
    expect(definitions).toContain("canaryFamilies");
    expect(definitions).toContain("businessFamilies");
  });
});
