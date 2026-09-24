import { beforeEach, describe, expect, it, vi } from "vitest";

vi.mock("./client", () => ({
  apiClient: {
    get: vi.fn(),
    post: vi.fn(),
  },
}));

import { apiClient } from "./client";
import { hrG2Api } from "./hr-g2-api";

describe("hrG2Api authenticated transport", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("routes attendance reads through the canonical apiClient", async () => {
    vi.mocked(apiClient.get).mockResolvedValueOnce([]);

    await hrG2Api.listAttendance({ employmentId: "emp-1" });

    expect(apiClient.get).toHaveBeenCalledWith("/api/v2/hr/time/attendance", {
      query: { employmentId: "emp-1" },
    });
  });

  it("routes leave reads through the canonical apiClient", async () => {
    vi.mocked(apiClient.get).mockResolvedValueOnce([]);

    await hrG2Api.listLeaveRequests({ state: "PENDING_MANAGER" });

    expect(apiClient.get).toHaveBeenCalledWith("/api/v2/hr/leave/requests", {
      query: { state: "PENDING_MANAGER" },
    });
  });

  it("uses apiClient mutation transport with an idempotency key", async () => {
    vi.mocked(apiClient.post).mockResolvedValueOnce(undefined);

    await hrG2Api.managerApproveLeave("leave-1", "approved");

    expect(apiClient.post).toHaveBeenCalledWith(
      "/api/v2/hr/leave/requests/leave-1/manager-approve",
      { comment: "approved" },
      expect.objectContaining({
        context: {
          headers: {
            "Idempotency-Key": expect.any(String),
          },
        },
      }),
    );
  });

  it("passes monthly report filters via apiClient query options", async () => {
    vi.mocked(apiClient.get).mockResolvedValueOnce([]);

    await hrG2Api.monthlyAttendanceReport(2026, 9);

    expect(apiClient.get).toHaveBeenCalledWith("/api/v2/hr/time/attendance/monthly-report", {
      query: { year: 2026, month: 9, employmentId: undefined },
    });
  });
});
