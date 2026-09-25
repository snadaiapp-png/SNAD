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
});
