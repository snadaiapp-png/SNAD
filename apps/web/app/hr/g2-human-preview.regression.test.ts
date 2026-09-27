import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { describe, expect, it } from "vitest";

const REPO_ROOT = resolve(__dirname, "../../../../");
const WORKFLOW = resolve(REPO_ROOT, ".github/workflows/hrm-human-preview.yml");

describe("generalized HRM Human Preview governance", () => {
  it("uses host-native PostgreSQL Direct and is not pinned to historical PR 914", () => {
    const source = readFileSync(WORKFLOW, "utf8");
    expect(source).not.toContain("pull_request.number == 914");
    expect(source).not.toMatch(/\n\s+services:\s*\n/);
    expect(source).toContain("sudo systemctl start postgresql");
    expect(source).toContain("NOBYPASSRLS");
  });

  it("binds visual evidence to exact head and runs the dedicated G2 visual suite", () => {
    const source = readFileSync(WORKFLOW, "utf8");
    expect(source).toContain("GITHUB_HEAD_SHA");
    expect(source).toContain("playwright-g2-visual.config.ts");
    expect(source).toContain("g2-visual-evidence");
    expect(source).toContain("playwright-g2-visual-report");
    expect(source).toContain("if-no-files-found: error");
  });

  it("derives the visual matrix from role routes and projects instead of hard-coded historical counts", () => {
    const source = readFileSync(WORKFLOW, "utf8");
    expect(source).not.toContain('test "$VISUAL" = "12"');
    expect(source).not.toContain("EXPECTED=60");
    expect(source).toContain("G2_VISUAL_EXPECTED_CASES");
    expect(source).toContain("expected_cases");
    expect(source).toContain('"employee": ["/hr", "/hr/attendance", "/hr/timesheets", "/hr/leave"]');
    expect(source).toContain('"manager": ["/hr", "/hr/team-attendance", "/hr/team-timesheets", "/hr/leave/approvals", "/hr/reports/attendance"]');
    expect(source).toContain('"hr": ["/hr", "/hr/schedules", "/hr/attendance/admin", "/hr/leave/policies", "/hr/leave/approvals", "/hr/reports/attendance"]');
    expect(source).toContain('"g2-visual-ar-desktop"');
    expect(source).toContain('"g2-visual-en-mobile"');
  });

  it("requires a complete unique PASS manifest with exact-head screenshots for every matrix case", () => {
    const source = readFileSync(WORKFLOW, "utf8");
    expect(source).toContain('row["sha"] != head_sha');
    expect(source).toContain('row["result"] != "PASS"');
    expect(source).toContain('Path(row["screenshot"]).is_file()');
    expect(source).toContain("len(actual_cases) != len(set(actual_cases))");
    expect(source).toContain("set(actual_cases) != expected_cases");
  });
});

// G2 final recertification marker: intentionally no behavioral change.
