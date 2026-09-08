import { describe, expect, it } from "vitest";
import { existsSync, readFileSync } from "node:fs";

const base = (relative: string) => new URL(`../${relative}`, import.meta.url);

function readRequired(relative: string) {
  const url = base(relative);
  expect(existsSync(url), `${relative} must exist`).toBe(true);
  return existsSync(url) ? readFileSync(url, "utf8") : "";
}

const COMPONENTS = [
  "page.tsx",
  "components/workflow-designer.tsx",
  "components/step-palette.tsx",
  "components/step-inspector.tsx",
  "components/assignment-rule-editor.tsx",
  "components/expression-rule-editor.tsx",
  "components/publish-panel.tsx",
];

describe("workflow Y2 definition designer (Task 18)", () => {
  it("creates the approved route and focused designer components", () => {
    for (const component of COMPONENTS) {
      expect(existsSync(base(component)), `${component} must exist`).toBe(true);
    }
  });

  it("keeps published definitions read-only and offers an explicit next draft", () => {
    const designer = readRequired("components/workflow-designer.tsx");
    expect(designer).toContain('publicationState === "DRAFT"');
    expect(designer).toContain("منشور");
    expect(designer).toContain("إنشاء مسودة جديدة");
    expect(designer).toContain("حذف خطوة");
    expect(designer).toContain("createNextDraft");
  });

  it("uses native DOM/SVG graph rendering and real definition APIs without a graph dependency", () => {
    const designer = readRequired("components/workflow-designer.tsx");
    const api = readFileSync(new URL("../../../../../lib/api/workflow-api.ts", import.meta.url), "utf8");
    expect(designer).toContain("<svg");
    expect(designer).toContain("getDefinitionSteps");
    expect(designer).toContain("getDefinitionTransitions");
    expect(designer).toContain("addDefinitionStep");
    expect(designer).toContain("createDefinitionTransition");
    expect(api).toContain("addDefinitionStep:");
    expect(api).toContain("createDefinitionTransition:");
    expect(designer).not.toMatch(/reactflow|xyflow|dagre/i);
  });

  it("supports Y2 step, assignment, approval, SLA, transition and safe AST controls", () => {
    const inspector = readRequired("components/step-inspector.tsx");
    const assignment = readRequired("components/assignment-rule-editor.tsx");
    const expression = readRequired("components/expression-rule-editor.tsx");

    for (const type of ["START", "HUMAN_TASK", "APPROVAL", "CONDITION", "SYSTEM_ACTION", "PARALLEL_FORK", "PARALLEL_JOIN", "CALL_WORKFLOW", "NOTIFICATION", "END"]) {
      expect(inspector).toContain(type);
    }
    for (const rule of ["EMPLOYEE", "MANAGER", "POSITION", "DEPARTMENT", "ROLE", "PERMISSION"]) {
      expect(assignment).toContain(rule);
    }
    expect(inspector).toContain("ANY_ONE");
    expect(inspector).toContain("ALL");
    expect(inspector).toContain("DENY");
    expect(inspector).toContain("ALLOW");
    expect(inspector).toContain("slaMode");
    expect(inspector).toContain("outcome");

    expect(expression).toContain("WorkflowConditionAst");
    expect(expression).toContain("operator");
    expect(expression).not.toContain("eval(");
    expect(expression).not.toContain("new Function");
    expect(expression).not.toContain("textarea");
  });

  it("gates publish on the latest server validation and labels simulation non-production", () => {
    const panel = readRequired("components/publish-panel.tsx");
    expect(panel).toContain("validateDefinition");
    expect(panel).toContain("simulateDefinition");
    expect(panel).toContain("publishDefinition");
    expect(panel).toContain("latestValidation");
    expect(panel).toContain("disabled={!latestValidation?.valid");
    expect(panel).toContain("محاكاة غير إنتاجية");
    expect(panel).toContain("لا تنفذ آثارًا جانبية");
  });
});
