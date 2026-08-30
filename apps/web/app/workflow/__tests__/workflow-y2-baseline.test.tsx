import { describe, expect, it } from "vitest";
import { readFileSync } from "node:fs";

const source = readFileSync(new URL("../page.tsx", import.meta.url), "utf8");

describe("workflow Y2 migration baseline", () => {
  it("keeps the canonical workflow page while the new IA is introduced additively", () => {
    expect(source).toContain('type Tab = "definitions" | "instances" | "approvals" | "monitoring"');
  });
});
