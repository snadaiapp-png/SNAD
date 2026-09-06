// @vitest-environment jsdom

/**
 * R0C-12 G6-R4 — the subscription detail page must render an entitlements
 * section (design §8/§9 detail contract: items, entitlements, usage,
 * invoices, changes, provisioning, audit). The backend detail read model
 * now carries `entitlements`; this pins the section renderer.
 */
import "@testing-library/jest-dom/vitest";
import { describe, it, expect, vi } from "vitest";
import { render, screen, cleanup } from "@testing-library/react";

vi.mock("@/lib/i18n/I18nProvider", () => ({
  useI18n: () => ({ t: (key: string) => `i18n:${key}` }),
}));

import { SubscriptionEntitlementsSection } from "./SubscriptionEntitlementsSection";

const rows = [
  {
    source: "PLAN",
    moduleCode: "hr",
    moduleName: "Human Resources",
    capabilityCode: "USAGE.EMPLOYEES",
    moduleEnabled: true,
    booleanValue: null,
    limitValue: 250,
    quotaValue: null,
    quotaPeriod: null,
  },
  {
    source: "PRODUCT",
    moduleCode: "hr",
    moduleName: "Human Resources",
    capabilityCode: "hr.payroll.enabled",
    moduleEnabled: true,
    booleanValue: true,
    limitValue: null,
    quotaValue: null,
    quotaPeriod: null,
  },
];

describe("SubscriptionEntitlementsSection", () => {
  it("renders plan-derived and item-derived entitlement rows", () => {
    render(<SubscriptionEntitlementsSection entitlements={rows} />);
    expect(screen.getByText("i18n:scp.detail.entitlements.title")).toBeInTheDocument();
    expect(screen.getAllByText("Human Resources").length).toBeGreaterThan(0);
    expect(screen.getAllByText("USAGE.EMPLOYEES").length).toBeGreaterThan(0);
    expect(screen.getAllByText("hr.payroll.enabled").length).toBeGreaterThan(0);
    expect(screen.getAllByText("i18n:scp.detail.entitlements.source.plan").length).toBeGreaterThan(0);
    expect(screen.getAllByText("i18n:scp.detail.entitlements.source.product").length).toBeGreaterThan(0);
    cleanup();
  });

  it("renders an explicit empty state when no entitlement rows exist", () => {
    render(<SubscriptionEntitlementsSection entitlements={[]} />);
    expect(screen.getByText("i18n:scp.detail.entitlements.title")).toBeInTheDocument();
    expect(screen.getByText("i18n:scp.detail.entitlements.none")).toBeInTheDocument();
    cleanup();
  });
});
