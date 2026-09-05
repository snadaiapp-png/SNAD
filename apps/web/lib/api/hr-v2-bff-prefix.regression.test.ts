import { beforeEach, describe, expect, it, vi } from "vitest";

const { requestMock } = vi.hoisted(() => ({ requestMock: vi.fn() }));

vi.mock("@/lib/api/client", () => ({
  apiClient: {
    request: requestMock,
  },
}));

import { hrmV2Api } from "./hr-v2-api";

describe("HRM v2 BFF path regression", () => {
  beforeEach(() => {
    requestMock.mockReset();
    requestMock.mockResolvedValue([]);
  });

  it("passes a backend-relative /api/v2/hr path to apiClient instead of duplicating the /api/platform BFF prefix", async () => {
    await hrmV2Api.listPeople();

    const request = requestMock.mock.calls[0]?.[0] as { path?: string } | undefined;
    expect(request?.path).toBe("/api/v2/hr/people");
    expect(request?.path).not.toContain("/api/platform/api/platform/");
    expect(request?.path?.startsWith("/api/platform")).toBe(false);
  });
});
