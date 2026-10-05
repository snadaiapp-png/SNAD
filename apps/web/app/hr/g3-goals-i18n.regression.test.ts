import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { describe, expect, it } from "vitest";

const productDictionary = readFileSync(
  resolve(__dirname, "../../lib/i18n/locales/hrm-g2-product-i18n.ts"),
  "utf8",
);
const goalsPageSource = readFileSync(
  resolve(__dirname, "performance/goals/goals-client.tsx"),
  "utf8",
);
const workspaceSource = readFileSync(
  resolve(__dirname, "components/hr-workspace.tsx"),
  "utf8",
);

/**
 * Every Task 5 i18n key must exist exactly once in the AR dictionary and
 * exactly once in the EN dictionary of the HR product catalog (the catalog
 * actually consumed by HR pages via HrI18nAugmenter).
 */
const TASK5_KEYS = [
  "hrm.g3.workspace.nav.goals",
  "hrm.g3.goals.eyebrow",
  "hrm.g3.goals.title",
  "hrm.g3.goals.subtitle",
  "hrm.g3.goals.loading",
  "hrm.g3.goals.col.title",
  "hrm.g3.goals.col.metric",
  "hrm.g3.goals.col.target",
  "hrm.g3.goals.col.progress",
  "hrm.g3.goals.col.status",
  "hrm.g3.goals.col.period",
  "hrm.g3.goals.col.updated",
  "hrm.g3.goals.empty.title",
  "hrm.g3.goals.empty.description",
  "hrm.g3.goals.action.updateProgress",
  "hrm.g3.goals.action.saving",
  "hrm.g3.goals.form.selectGoal",
  "hrm.g3.goals.progress.label",
  "hrm.g3.goals.notice.progressUpdated",
  "hrm.g3.goals.validation.progressRange",
  "hrm.g3.goals.kpi.total",
  "hrm.g3.goals.kpi.avgProgress",
  "hrm.g3.goals.kpi.completed",
  "hrm.g3.goals.forbidden",
  "hrm.g3.goals.state.DRAFT",
  "hrm.g3.goals.state.ACTIVE",
  "hrm.g3.goals.state.IN_PROGRESS",
  "hrm.g3.goals.state.COMPLETED",
  "hrm.g3.goals.state.CANCELLED",
] as const;

describe("HRM G3 Task 5 goals i18n contract", () => {
  it("keeps every Task 5 key present once in AR and once in EN", () => {
    for (const key of TASK5_KEYS) {
      expect(
        productDictionary.split(`"${key}"`).length - 1,
        `Task 5 key "${key}" must exist exactly twice (AR + EN)`,
      ).toBe(2);
    }
  });

  it("keeps the goals page free of hard-coded locale branching", () => {
    expect(goalsPageSource).not.toContain('locale === "ar"');
    expect(goalsPageSource).not.toContain('locale === "en"');
  });

  it("wires the workspace goals link through the catalog, not hard-coded EN labels", () => {
    expect(workspaceSource).toContain('labelMessageId: "hrm.g3.workspace.nav.goals"');
    expect(workspaceSource).not.toContain("labelEn");
  });

  it("renders governing page copy through t() message ids", () => {
    for (const key of [
      "hrm.g3.goals.title",
      "hrm.g3.goals.subtitle",
      "hrm.g3.goals.col.metric",
      "hrm.g3.goals.col.target",
      "hrm.g3.goals.col.progress",
      "hrm.g3.goals.col.status",
      "hrm.g3.goals.empty.title",
      "hrm.g3.goals.action.updateProgress",
      "hrm.g3.goals.notice.progressUpdated",
      "hrm.g3.goals.validation.progressRange",
    ] as const) {
      expect(goalsPageSource).toContain(`t("${key}")`);
    }
  });
});
