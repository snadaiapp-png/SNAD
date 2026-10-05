import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { describe, expect, it } from "vitest";

const productDictionary = readFileSync(
  resolve(__dirname, "../../lib/i18n/locales/hrm-g2-product-i18n.ts"),
  "utf8",
);
const reviewsPageSource = readFileSync(
  resolve(__dirname, "performance/reviews/reviews-client.tsx"),
  "utf8",
);
const workspaceSource = readFileSync(
  resolve(__dirname, "components/hr-workspace.tsx"),
  "utf8",
);

/**
 * Every Task 6 i18n key must exist exactly once in the AR dictionary and
 * exactly once in the EN dictionary of the HR product catalog (the catalog
 * actually consumed by HR pages via HrI18nAugmenter).
 */
const TASK6_KEYS = [
  "hrm.g3.workspace.nav.reviews",
  "hrm.g3.reviews.eyebrow",
  "hrm.g3.reviews.title",
  "hrm.g3.reviews.subtitle",
  "hrm.g3.reviews.loading",
  "hrm.g3.reviews.col.cycle",
  "hrm.g3.reviews.col.period",
  "hrm.g3.reviews.col.source",
  "hrm.g3.reviews.col.status",
  "hrm.g3.reviews.col.rating",
  "hrm.g3.reviews.col.comments",
  "hrm.g3.reviews.col.updated",
  "hrm.g3.reviews.empty.title",
  "hrm.g3.reviews.empty.description",
  "hrm.g3.reviews.action.create",
  "hrm.g3.reviews.action.submit",
  "hrm.g3.reviews.action.acknowledge",
  "hrm.g3.reviews.action.cancel",
  "hrm.g3.reviews.action.saving",
  "hrm.g3.reviews.form.cycle",
  "hrm.g3.reviews.form.periodStart",
  "hrm.g3.reviews.form.periodEnd",
  "hrm.g3.reviews.form.rating",
  "hrm.g3.reviews.form.comments",
  "hrm.g3.reviews.form.rating.none",
  "hrm.g3.reviews.notice.created",
  "hrm.g3.reviews.notice.submitted",
  "hrm.g3.reviews.notice.acknowledged",
  "hrm.g3.reviews.notice.cancelled",
  "hrm.g3.reviews.validation.cycle",
  "hrm.g3.reviews.validation.rating",
  "hrm.g3.reviews.validation.period",
  "hrm.g3.reviews.team.title",
  "hrm.g3.reviews.team.description",
  "hrm.g3.reviews.team.create",
  "hrm.g3.reviews.team.employmentId",
  "hrm.g3.reviews.kpi.total",
  "hrm.g3.reviews.kpi.submitted",
  "hrm.g3.reviews.kpi.acknowledged",
  "hrm.g3.reviews.forbidden",
  "hrm.g3.reviews.state.DRAFT",
  "hrm.g3.reviews.state.SUBMITTED",
  "hrm.g3.reviews.state.ACKNOWLEDGED",
  "hrm.g3.reviews.state.CANCELLED",
  "hrm.g3.reviews.source.SELF",
  "hrm.g3.reviews.source.MANAGER",
  "hrm.g3.reviews.source.PEER",
  // Shared module shell navigation sections + header actions.
  "hrm.g2.workspace.brandMark",
  "hrm.g2.workspace.nav.section.overview",
  "hrm.g2.workspace.nav.section.talent",
  "hrm.g2.workspace.nav.section.timeLeave",
  "hrm.g2.workspace.nav.section.management",
  "hrm.g2.workspace.nav.section.admin",
  "hrm.g2.workspace.nav.section.governance",
  "hrm.g2.workspace.action.language",
  "hrm.g2.workspace.action.logout",
  "hrm.g2.workspace.action.workspace",
] as const;

describe("HRM G3 Task 6 reviews i18n contract", () => {
  it("keeps every Task 6 key present once in AR and once in EN", () => {
    for (const key of TASK6_KEYS) {
      expect(
        productDictionary.split(`"${key}"`).length - 1,
        `Task 6 key "${key}" must exist exactly twice (AR + EN)`,
      ).toBe(2);
    }
  });

  it("renders the reviews page through the catalog with no hard-coded locale branching", () => {
    expect(reviewsPageSource).toContain('t("hrm.g3.reviews.title")');
    expect(reviewsPageSource).toContain('t("hrm.g3.reviews.empty.title")');
    expect(reviewsPageSource).toContain('t("hrm.g3.reviews.team.title")');
    expect(reviewsPageSource).not.toMatch(/locale\s*===\s*["']ar["']/);
    expect(reviewsPageSource).not.toMatch(/locale\s*===\s*["']en["']/);
  });

  it("wires the workspace reviews link through the catalog", () => {
    expect(workspaceSource).toContain('labelMessageId: "hrm.g3.workspace.nav.reviews"');
    expect(workspaceSource).toContain("HRM_CAPABILITIES.REVIEW_SELF_VIEW");
    expect(workspaceSource).toContain("HRM_CAPABILITIES.REVIEW_TEAM_MANAGE");
  });
});
