import { describe, expect, it, vi } from "vitest";

const { redirectMock } = vi.hoisted(() => ({
  redirectMock: vi.fn(),
}));

vi.mock("next/navigation", () => ({
  redirect: redirectMock,
}));

import SubscriptionsPage from "./page";

describe("SubscriptionsPage", () => {
  it("routes the legacy module entry point to the canonical subscription UI", () => {
    SubscriptionsPage();
    expect(redirectMock).toHaveBeenCalledWith("/executive/subscriptions");
  });
});
