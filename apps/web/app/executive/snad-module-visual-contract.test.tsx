// @vitest-environment jsdom

import "@testing-library/jest-dom/vitest";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { describe, expect, it } from "vitest";

function source(relativePath: string): string {
  return readFileSync(resolve(__dirname, relativePath), "utf8");
}

describe("Executive Administration — SNAD Module Visual Contract", () => {
  it("renders the executive module through SnadModuleShell rather than the legacy double shell", () => {
    const layout = source("./_components/ScpLayout.tsx");
    const routeLayout = source("./layout.tsx");

    expect(layout).toContain("SnadModuleShell");
    expect(layout).toContain("@/components/sds/module");
    expect(routeLayout).not.toContain("ScpExecutiveShell");
    expect(layout).not.toContain("scp.module.css");
  });

  it("delegates page frame and state surfaces to the shared CRM/HR primitives", () => {
    const states = source("./_components/ScpStates.tsx");
    for (const primitive of [
      "SnadPageFrame",
      "SnadLoadingState",
      "SnadEmptyState",
      "SnadErrorState",
      "SnadStatusBadge",
    ]) {
      expect(states).toContain(primitive);
    }
    expect(states).not.toContain("scp.module.css");
  });

  it("records /executive as compliant in the visual migration ledger", () => {
    const ledger = source("../../../../docs/superpowers/specs/2026-10-02-visual-migration-ledger.md");
    expect(ledger).toContain("Executive Administration");
    expect(ledger).toContain("/executive");
    expect(ledger).toContain("COMPLIANT (Executive IAM convergence)");
  });
});
