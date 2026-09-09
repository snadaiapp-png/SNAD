// @vitest-environment jsdom

/**
 * R0C-12 G6-R5 — "Plans & Pricing" must make Plan ≠ Version ≠ Price ≠
 * Entitlement explicit (design §9). The page already renders plans and
 * versions; these pins cover the additive price and entitlement surfaces
 * that consume the existing `/plans/{id}/versions/{versionId}/prices` and
 * `/plans/{id}/modules` contracts.
 */
import "@testing-library/jest-dom/vitest";
import { describe, it, expect, vi } from "vitest";
import { render, screen, cleanup } from "@testing-library/react";

vi.mock("@/lib/i18n/I18nProvider", () => ({
  useI18n: () => ({
    t: (key: string) => `i18n:${key}`,
    locale: "en",
  }),
}));

import { PlanVersionPricesTable } from "./PlanVersionPricesTable";
import { PlanEntitlementsSummary } from "./PlanEntitlementsSummary";

const prices = [
  {
    id: "p1",
    planVersionId: "v1",
    productId: null,
    priceModel: "FLAT",
    countryCode: "SA",
    currencyCode: "SAR",
    billingInterval: "MONTHLY",
    baseAmountMinor: 15000,
    unitAmountMinor: null,
    tiersJson: null,
    minAmountMinor: null,
    maxAmountMinor: null,
    effectiveFrom: "2026-01-01T00:00:00Z",
    effectiveTo: null,
  },
];

const modules = [
  {
    id: "m1",
    planId: "plan1",
    moduleId: "mod1",
    moduleCode: "hr",
    moduleEnabled: true,
    capabilityCode: "USAGE.EMPLOYEES",
    capabilityValue: null,
    limitValue: 250,
    quotaValue: null,
    quotaPeriod: null,
    effectiveAt: "2026-01-01T00:00:00Z",
    createdAt: "2026-01-01T00:00:00Z",
    updatedAt: "2026-01-01T00:00:00Z",
  },
];

describe("PlanVersionPricesTable", () => {
  it("renders per-country price rows (Price ≠ Version made explicit)", () => {
    render(<PlanVersionPricesTable prices={prices} />);
    expect(screen.getByText("i18n:scp.plans.prices.title")).toBeInTheDocument();
    expect(screen.getAllByText(/SA/).length).toBeGreaterThan(0);
    expect(screen.getAllByText("i18n:scp.plans.prices.model").length).toBeGreaterThan(0);
    expect(screen.getByText("FLAT")).toBeInTheDocument();
    cleanup();
  });

  it("renders an explicit empty state when a version has no prices", () => {
    render(<PlanVersionPricesTable prices={[]} />);
    expect(screen.getByText("i18n:scp.plans.prices.none")).toBeInTheDocument();
    cleanup();
  });
});

describe("PlanEntitlementsSummary", () => {
  it("renders per-module entitlement rows (Entitlement ≠ Version made explicit)", () => {
    render(<PlanEntitlementsSummary modules={modules} />);
    expect(screen.getByText("i18n:scp.plans.entitlements.title")).toBeInTheDocument();
    expect(screen.getByText("hr")).toBeInTheDocument();
    expect(screen.getByText("USAGE.EMPLOYEES")).toBeInTheDocument();
    expect(
      screen.getAllByText((_, element) =>
        (element?.textContent ?? "").includes("scp.plans.entitlements.limit"),
      ).length,
    ).toBeGreaterThan(0);
    cleanup();
  });

  it("renders an explicit empty state when a plan has no module entitlements", () => {
    render(<PlanEntitlementsSummary modules={[]} />);
    expect(screen.getByText("i18n:scp.plans.entitlements.none")).toBeInTheDocument();
    cleanup();
  });
});
