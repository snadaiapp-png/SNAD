import { describe, expect, it } from "vitest";
import { EXECUTIVE_COMMERCIAL_I18N_AR, EXECUTIVE_COMMERCIAL_I18N_EN } from "./executive-commercial-l10n";

const REQUIRED_KEYS = [
  "scp.tenants.resumeSubscription",
  "scp.tenants.createSubscription",
  "scp.tenants.createSuccessor",
  "scp.tenants.commercialBlocked",
  "scp.subscriptions.recurringAmount",
  "scp.subscriptions.annualRecurringAmount",
  "scp.subscriptions.monthlyRecurringAmount",
  "scp.subscriptions.create.section",
  "scp.subscriptions.create.plan",
  "scp.subscriptions.create.selectPlan",
  "scp.subscriptions.create.billingCycle",
  "scp.subscriptions.create.seatQuantity",
  "scp.subscriptions.create.submit",
  "scp.subscriptions.create.success",
  "scp.subscriptions.createSuccessor.submit",
  "scp.subscriptions.createSuccessor.success",
  "scp.subscriptions.resume.section",
  "scp.subscriptions.resume.submit",
  "scp.subscriptions.resume.success",
  "scp.subscriptions.resume.unavailable",
] as const;

describe("executive commercial locale namespace", () => {
  it("has exact Arabic/English key parity", () => {
    expect(Object.keys(EXECUTIVE_COMMERCIAL_I18N_AR).sort()).toEqual(
      Object.keys(EXECUTIVE_COMMERCIAL_I18N_EN).sort(),
    );
  });

  it.each(REQUIRED_KEYS)("defines %s in both locales", (key) => {
    expect(EXECUTIVE_COMMERCIAL_I18N_AR[key]).toBeTruthy();
    expect(EXECUTIVE_COMMERCIAL_I18N_EN[key]).toBeTruthy();
  });
});
