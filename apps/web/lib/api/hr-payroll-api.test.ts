import { describe, expect, it, vi, beforeEach } from "vitest";

const client = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn() }));
vi.mock("./client", () => ({ apiClient: client }));
import { hrPayrollApi } from "./hr-payroll-api";

describe("G4-T9 payroll API contract", () => {
  beforeEach(() => { vi.clearAllMocks(); });

  it("reads runs and items only through authenticated canonical routes", async () => {
    client.get.mockResolvedValue([]);
    await hrPayrollApi.listRuns();
    await hrPayrollApi.listItems("run-1");
    expect(client.get).toHaveBeenNthCalledWith(1, "/api/v2/hr/payroll/runs");
    expect(client.get).toHaveBeenNthCalledWith(2, "/api/v2/hr/payroll/runs/run-1/items");
  });

  it("sends versioned mutations with fresh Idempotency-Key and no tenant override", async () => {
    client.post.mockResolvedValue({});
    await hrPayrollApi.mutate("run-1", "review", 7, "verified");
    await hrPayrollApi.mutate("run-1", "approve", 8, "approved");
    expect(client.post).toHaveBeenNthCalledWith(1, "/api/v2/hr/payroll/runs/run-1/review",
      { expectedVersion: 7, reason: "verified" }, expect.any(Object));
    const first = client.post.mock.calls[0][2].context.headers["Idempotency-Key"];
    const second = client.post.mock.calls[1][2].context.headers["Idempotency-Key"];
    expect(first).toBeTruthy();
    expect(second).not.toBe(first);
  });

  it("exports only the expected version, without bank or accounting journal data", async () => {
    client.post.mockResolvedValue({});
    await hrPayrollApi.mutate("run-1", "export", 9, "");
    expect(client.post.mock.calls[0][1]).toEqual({ expectedVersion: 9 });
  });
});
