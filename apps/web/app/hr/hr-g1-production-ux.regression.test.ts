/**
 * HRM-G1 production UX/capability regression.
 *
 * Fails closed if the 15-screen G1 implementation becomes undiscoverable from
 * the HR workspace, if the frontend reintroduces a non-canonical onboarding
 * capability, or if the T11 evidence matrix drifts back below 15/15.
 */
import { readFileSync } from "fs";
import { resolve } from "path";
import { describe, expect, it } from "vitest";

const REPO_ROOT = resolve(__dirname, "../../../../");

function read(relativePath: string): string {
  return readFileSync(resolve(REPO_ROOT, relativePath), "utf8");
}

describe("HRM-G1 production UX and capability closure", () => {
  it("keeps recruitment and onboarding discoverable from the HR workspace", () => {
    const workspace = read("apps/web/app/hr/components/hr-workspace.tsx");
    expect(workspace).toContain('href: "/hr/recruitment"');
    expect(workspace).toContain('label: "التوظيف"');
    expect(workspace).toContain('href: "/hr/onboarding"');
    expect(workspace).toContain('label: "التأهيل"');
  });

  it("uses only canonical onboarding capabilities", () => {
    const capabilities = read("apps/web/lib/auth/capabilities.ts");
    const dashboard = read("apps/web/app/hr/onboarding/page.tsx");
    const detail = read("apps/web/app/hr/onboarding/plans/[planId]/page.tsx");
    for (const source of [capabilities, dashboard, detail]) {
      expect(source).not.toContain("HRM.ONBOARDING.PLAN.VIEW");
      expect(source).not.toContain("ONBOARDING_PLAN_VIEW");
    }
    expect(capabilities).toContain('ONBOARDING_PLAN_MANAGE: "HRM.ONBOARDING.PLAN.MANAGE"');
    expect(capabilities).toContain('RECRUITMENT_OFFER_APPROVE: "HRM.RECRUITMENT.OFFER.APPROVE"');
  });

  it("keeps all 15 official T11 screens marked DONE", () => {
    const matrix = read("docs/hrm/g1/evidence/T11-OFFICIAL-SCREEN-MATRIX.md");
    expect(matrix).not.toContain("NOT_IMPLEMENTED");
    expect(matrix).toContain("15/15 = 100%");
    expect((matrix.match(/\|\s*DONE\s*\|/g) ?? []).length).toBe(15);
  });

  it("seeds the complete canonical G1 capability catalog for ADMIN backfill", () => {
    const migration = read(
      "apps/sanad-platform/src/main/resources/db/migration/V20260922_1__hr_g1_operator_capabilities_and_admin_scopes.sql",
    );
    expect((migration.match(/'HRM\.RECRUITMENT\./g) ?? []).length).toBeGreaterThanOrEqual(19);
    expect((migration.match(/'HRM\.ONBOARDING\./g) ?? []).length).toBeGreaterThanOrEqual(3);
    expect(migration).toContain("INSERT INTO role_capabilities");
    expect(migration).toContain("INSERT INTO access_scope_grants");
    expect(migration).not.toContain("HRM.ONBOARDING.PLAN.VIEW");
  });
});
