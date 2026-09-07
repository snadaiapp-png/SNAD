import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { describe, expect, it } from "vitest";

describe("HR workspace SDS token contract", () => {
  const css = readFileSync(resolve(process.cwd(), "app/hr/hr.module.css"), "utf8");

  it("does not reference undefined legacy shorthand tokens", () => {
    for (const unsupported of ["--snad-primary", "--snad-border", "--snad-surface", "--snad-text)"]) {
      expect(css, `unsupported HR token reference: ${unsupported}`).not.toContain(unsupported);
    }
  });

  it("uses the canonical SDS action token for primary actions", () => {
    expect(css).toContain("--snad-color-action-primary");
  });
});
