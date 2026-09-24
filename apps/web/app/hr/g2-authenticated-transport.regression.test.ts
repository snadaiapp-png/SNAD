import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { describe, expect, it } from "vitest";

const G2_AUTHENTICATED_PAGES = [
  "attendance/page.tsx",
  "attendance/admin/page.tsx",
  "team-attendance/page.tsx",
  "schedules/page.tsx",
  "timesheets/page.tsx",
  "team-timesheets/page.tsx",
  "leave/page.tsx",
  "leave/approvals/page.tsx",
  "leave/policies/page.tsx",
  "reports/attendance/page.tsx",
] as const;

describe("G2 authenticated transport regression", () => {
  it("keeps authenticated G2 product pages on hrG2Api/apiClient", () => {
    for (const relativePath of G2_AUTHENTICATED_PAGES) {
      const source = readFileSync(resolve(__dirname, relativePath), "utf8");
      expect(source, relativePath).not.toMatch(/\bfetch\s*\(\s*["'`]\/api\/platform\/api\/v2\/hr\//);
    }
  });
});
