import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { describe, expect, it } from "vitest";

const HR_ROOT = resolve(__dirname);
const REPO_ROOT = resolve(HR_ROOT, "../../../..");
const WEB_ROOT = resolve(REPO_ROOT, "apps/web");
const SEED_PATH = resolve(REPO_ROOT, "apps/sanad-platform/src/test/resources/sql/g2-acceptance-seed.sql");
const PREVIEW_WORKFLOW_PATH = resolve(REPO_ROOT, ".github/workflows/hrm-human-preview.yml");
const VISUAL_SPEC_PATH = resolve(WEB_ROOT, "e2e/g2-visual.spec.ts");

describe("G2 human visual acceptance data contract", () => {
  it("seeds representative product data instead of certifying empty-only G2 screens", () => {
    const seed = readFileSync(SEED_PATH, "utf8");
    for (const table of [
      "hr_attendance_records",
      "hr_timesheets",
      "hr_leave_requests",
      "hr_work_schedules",
    ]) {
      expect(seed, table).toContain(`INSERT INTO ${table}`);
    }
    expect(seed).toContain("G2_VISUAL_FIXTURE");
  });

  it("requires representative product rows during HRM Human Preview only", () => {
    const workflow = readFileSync(PREVIEW_WORKFLOW_PATH, "utf8");
    const visualSpec = readFileSync(VISUAL_SPEC_PATH, "utf8");

    expect(workflow).toContain("G2_VISUAL_REQUIRE_PRODUCT_DATA: \"true\"");
    expect(visualSpec).toContain('process.env.G2_VISUAL_REQUIRE_PRODUCT_DATA === "true"');
    expect(visualSpec).toContain('locator("main tbody tr")');
  });
});
