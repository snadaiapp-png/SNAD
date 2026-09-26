import { describe, expect, it } from "vitest";
import type { SubscriptionRow, TenantRow } from "./scp-api";

describe("SCP commercial read contracts", () => {
  it("types the actual recurring charge separately from monthly equivalent", () => {
    const annual: SubscriptionRow = {
      id: "sub-1",
      tenantId: "tenant-1",
      tenantName: "Tenant",
      tenantCountry: "SA",
      status: "ACTIVE",
      billingCycle: "ANNUAL",
      seatQuantity: 2,
      planId: "plan-1",
      planName: "Growth",
      planCode: "GROWTH",
      planVersion: "v1",
      currencyCode: "SAR",
      recurringAmountMinor: 240_000,
      monthlyEquivalentMinor: 20_000,
      itemCount: 1,
      trial: false,
      cancelAtPeriodEnd: false,
      currentPeriodEnd: "2027-09-23T00:00:00Z",
    };

    expect(annual.recurringAmountMinor).toBe(240_000);
    expect(annual.monthlyEquivalentMinor).toBe(20_000);
  });

  it("types backend-derived tenant access and commercial actions without UI casts", () => {
    const tenant: TenantRow = {
      id: "tenant-1",
      name: "Tenant",
      code: "tenant",
      status: "ACTIVE",
      countryCode: "SA",
      currencyCode: "SAR",
      subscriptionCount: 0,
      subscriptionStatus: null,
      applicationHostname: null,
      effectiveSubscriptionId: null,
      billingState: null,
      accessDecision: "NO_EFFECTIVE_SUBSCRIPTION",
      commercialAction: "CREATE_SUBSCRIPTION",
      anomalyCode: "ACTIVE_WITHOUT_EFFECTIVE_SUBSCRIPTION",
      loginAllowed: false,
      createdAt: "2026-09-23T00:00:00Z",
    };

    expect(tenant.loginAllowed).toBe(false);
    expect(tenant.commercialAction).toBe("CREATE_SUBSCRIPTION");
  });
});
