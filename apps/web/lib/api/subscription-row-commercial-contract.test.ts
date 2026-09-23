import { describe, expect, it } from "vitest";
import type { SubscriptionRow } from "./scp-api";

describe("SubscriptionRow commercial pricing contract", () => {
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
});
