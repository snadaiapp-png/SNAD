import { beforeEach, describe, expect, it, vi } from "vitest";

const postMock = vi.fn();

vi.mock("./client", () => ({
  apiClient: {
    get: vi.fn(),
    post: (...args: unknown[]) => postMock(...args),
    patch: vi.fn(),
    put: vi.fn(),
  },
}));

import { executiveApi } from "./executive-api";

describe("executiveApi subscription creation contract", () => {
  beforeEach(() => {
    postMock.mockReset().mockResolvedValue({ id: "sub-1", status: "ACTIVE" });
  });

  it("posts the canonical create-subscription payload used by tenant upgrade", async () => {
    const body = {
      tenantId: "b195f190-b9a1-48e4-867b-c617fce8a098",
      planId: "11111111-1111-4111-8111-111111111111",
      billingCycle: "MONTHLY" as const,
      seatQuantity: 4,
      trialDays: 0,
    };

    await executiveApi.createSubscription(body);

    expect(postMock).toHaveBeenCalledWith(
      "/api/v1/executive/subscriptions",
      body,
    );
  });
});
