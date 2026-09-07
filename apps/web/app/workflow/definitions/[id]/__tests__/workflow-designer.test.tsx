import { describe, expect, it } from "vitest";
import { readFileSync } from "node:fs";

const designerSource = readFileSync(
  new URL("../components/workflow-designer.tsx", import.meta.url),
  "utf8",
);

describe("workflow designer (Task 18)", () => {
  it("renders the published state marker and next-draft as the only mutation", () => {
    expect(designerSource).toContain("منشور");
    expect(designerSource).toContain("إنشاء مسودة جديدة");
  });

  it("does not expose graph mutation controls for a published version", () => {
    // Published versions disable pointer events and hide move semantics.
    expect(designerSource).toContain("pointerEvents");
    expect(designerSource).toContain("publicationState !== \"DRAFT\"");
  });

  it("keeps publish gated behind server validation", () => {
    expect(designerSource).toContain("!validation?.valid");
  });

  it("marks simulation as explicitly non-production", () => {
    expect(designerSource).toContain("غير إنتاجية");
  });

  it("always offers the structured table view", () => {
    expect(designerSource).toContain("جدول البنية");
    expect(designerSource).toContain("لوحة الرسم");
  });

  it("uses DOM/SVG canvas without adding a graph dependency", () => {
    expect(designerSource).toContain("<svg");
    const pkg = readFileSync(new URL("../../../../../package.json", import.meta.url), "utf8");
    expect(pkg).not.toMatch(/"(reactflow|react-flow|dagre|x6)"/);
  });
});
