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

  it("routes SELF attendance reads through the canonical apiClient", async () => {
    vi.mocked(apiClient.get).mockResolvedValueOnce([]);

    await hrG2Api.listAttendance({ employmentId: "emp-1" });

    expect(apiClient.get).toHaveBeenCalledWith("/api/v2/hr/time/attendance", {
      query: { employmentId: "emp-1" },
    });
  });

  it("routes TEAM and ADMIN attendance reads through explicit scoped endpoints", async () => {
    vi.mocked(apiClient.get).mockResolvedValue([]);

    await hrG2Api.listTeamAttendance({ startDate: "2026-09-01" });
    await hrG2Api.listAdminAttendance({ employmentId: "emp-1" });

    expect(apiClient.get).toHaveBeenNthCalledWith(1, "/api/v2/hr/time/attendance/team", {
      query: { startDate: "2026-09-01" },
    });
    expect(apiClient.get).toHaveBeenNthCalledWith(2, "/api/v2/hr/time/attendance/admin", {
      query: { employmentId: "emp-1" },
    });
  });

  it("routes leave reads through SELF TEAM and HR scoped endpoints", async () => {
    vi.mocked(apiClient.get).mockResolvedValue([]);

    await hrG2Api.listLeaveRequests({ state: "DRAFT" });
    await hrG2Api.listTeamLeaveRequests("PENDING_MANAGER");
    await hrG2Api.listHrLeaveRequests("PENDING_HR");

    expect(apiClient.get).toHaveBeenNthCalledWith(1, "/api/v2/hr/leave/requests", {
      query: { state: "DRAFT" },
    });
    expect(apiClient.get).toHaveBeenNthCalledWith(2, "/api/v2/hr/leave/requests/team", {
      query: { state: "PENDING_MANAGER" },
    });
    expect(apiClient.get).toHaveBeenNthCalledWith(3, "/api/v2/hr/leave/requests/hr", {
      query: { state: "PENDING_HR" },
    });
  });

  it("routes team timesheets and admin leave types through scoped endpoints", async () => {
    vi.mocked(apiClient.get).mockResolvedValue([]);

    await hrG2Api.listTeamTimesheets("SUBMITTED");
    await hrG2Api.listAdminLeaveTypes();

    expect(apiClient.get).toHaveBeenNthCalledWith(1, "/api/v2/hr/time/timesheets/team", {
      query: { state: "SUBMITTED" },
    });
    expect(apiClient.get).toHaveBeenNthCalledWith(2, "/api/v2/hr/leave/types/admin");
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

  it("routes monthly reports through TEAM and ADMIN scoped endpoints", async () => {
    vi.mocked(apiClient.get).mockResolvedValue([]);

    await hrG2Api.teamMonthlyAttendanceReport(2026, 9);
    await hrG2Api.adminMonthlyAttendanceReport(2026, 9, "emp-1");

    expect(apiClient.get).toHaveBeenNthCalledWith(1, "/api/v2/hr/time/attendance/monthly-report/team", {
      query: { year: 2026, month: 9 },
    });
    expect(apiClient.get).toHaveBeenNthCalledWith(2, "/api/v2/hr/time/attendance/monthly-report/admin", {
      query: { year: 2026, month: 9, employmentId: "emp-1" },
    });
  });
});
