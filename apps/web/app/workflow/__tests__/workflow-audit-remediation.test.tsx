import { describe, expect, it } from "vitest";
import { existsSync, readFileSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const here = path.dirname(fileURLToPath(import.meta.url));
const workflowDir = path.resolve(here, "..");

function readRequired(relative: string) {
  const target = path.resolve(workflowDir, relative);
  expect(existsSync(target), `${relative} must exist`).toBe(true);
  return existsSync(target) ? readFileSync(target, "utf8") : "";
}

function readWeb(relativeFromWebRoot: string) {
  const target = path.resolve(workflowDir, "../..", relativeFromWebRoot);
  expect(existsSync(target), `${relativeFromWebRoot} must exist`).toBe(true);
  return existsSync(target) ? readFileSync(target, "utf8") : "";
}

describe("Workflow audit remediation regressions", () => {
  it("creates new Y2 definitions through the dedicated server contract and opens the designer", () => {
    const api = readWeb("lib/api/workflow-api.ts");
    const definitions = readRequired("components/workflow-definitions.tsx");

    expect(api).toContain("createY2Definition");
    expect(api).toContain("${BASE}/definitions/y2");
    expect(definitions).toContain("useRouter");
    expect(definitions).toContain("workflowApi.createY2Definition");
    expect(definitions).toContain("router.push(`/workflow/definitions/${saved.id}`)");
  });

  it("reloads authoritative definition state after transition mutations", () => {
    const designer = readRequired("definitions/[id]/components/workflow-designer.tsx");
    const start = designer.indexOf("const createTransition = async");
    const end = designer.indexOf("const createNextDraft = async");
    const transitionMutation = designer.slice(start, end);

    expect(transitionMutation).toContain("await load()");
  });

  it("does not double-count totalBreaches in overview or monitoring attention totals", () => {
    const overview = readRequired("components/workflow-overview.tsx");
    const monitoring = readRequired("components/workflow-monitoring.tsx");
    const duplicated =
      "(health?.overdueSteps ?? 0) + (health?.overdueApprovals ?? 0) + (health?.totalBreaches ?? 0)";

    expect(overview).not.toContain(duplicated);
    expect(monitoring).not.toContain(duplicated);
    expect(overview).toContain("health?.totalBreaches ?? 0");
    expect(monitoring).toContain("health?.totalBreaches ?? 0");
  });
});
