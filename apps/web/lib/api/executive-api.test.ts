import { afterEach, describe, expect, it, vi } from "vitest";

const postMock = vi.fn();

vi.mock("./client", () => ({
  apiClient: {
    get: vi.fn(),
    post: (...args: unknown[]) => postMock(...args),
    patch: vi.fn(),
    put: vi.fn(),
  },
}));

import { executiveApi, type CreateSubscriptionRequest } from "./executive-api";

describe("executive subscription creation client", () => {
  afterEach(() => {
    vi.clearAllMocks();
  });

  it("posts the complete governed subscription creation contract", async () => {
    const body: CreateSubscriptionRequest = {
      tenantId: "11111111-1111-1111-1111-111111111111",
      planId: "22222222-2222-2222-2222-222222222222",
      billingCycle: "ANNUAL",
      seatQuantity: 7,
      trialDays: 14,
    };
    postMock.mockResolvedValue({ id: "sub-1" });

    await executiveApi.createSubscription(body);

    expect(postMock).toHaveBeenCalledWith("/api/v1/executive/subscriptions", body);
  });
});
