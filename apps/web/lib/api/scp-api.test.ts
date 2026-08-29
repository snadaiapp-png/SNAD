import { describe, expect, it, vi, beforeEach, afterEach } from "vitest";

/**
 * Unit tests for the Subscription Control Plane API surface — endpoint
 * construction, query serialization and pagination contract decoding.
 */

const getMock = vi.fn();
const postMock = vi.fn();

vi.mock("./client", () => ({
  apiClient: {
    get: (...args: unknown[]) => getMock(...args),
    post: (...args: unknown[]) => postMock(...args),
  },
}));

import { scpApi } from "./scp-api";

describe("scpApi — endpoint construction", () => {
  beforeEach(() => {
    getMock.mockReset();
    getMock.mockResolvedValue({});
    postMock.mockReset();
    postMock.mockResolvedValue({});
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("builds the overview route", async () => {
    await scpApi.overview();
    expect(getMock).toHaveBeenCalledWith("/api/v1/executive/overview");
  });

  it("serializes tenant search filters and pagination", async () => {
    await scpApi.tenants({ search: "acme", status: "ACTIVE", page: 2, size: 20 });
    expect(getMock).toHaveBeenCalledWith(
      "/api/v1/executive/tenants/v2?search=acme&status=ACTIVE&page=2&size=20",
    );
  });

  it("omits empty query parameters", async () => {
    await scpApi.tenants({ search: "", status: undefined, page: 0, size: 20 });
    expect(getMock).toHaveBeenCalledWith("/api/v1/executive/tenants/v2?page=0&size=20");
  });

  it("builds the subscriptions v2 grid route with trial filter", async () => {
    await scpApi.subscriptions({ trialOnly: true, search: "erp" });
    expect(getMock).toHaveBeenCalledWith(
      "/api/v1/executive/subscriptions/v2?trialOnly=true&search=erp",
    );
  });

  it("builds usage and audit routes tenant-scoped", async () => {
    await scpApi.usage("tenant-1");
    await scpApi.audit({ page: 1, size: 20, direction: "DESC" });
    expect(getMock).toHaveBeenNthCalledWith(1, "/api/v1/executive/usage?tenantId=tenant-1");
    expect(getMock).toHaveBeenNthCalledWith(
      2,
      "/api/v1/executive/audit?page=1&size=20&direction=DESC",
    );
  });

  it("posts lifecycle commands with a reason body", async () => {
    await scpApi.lifecycleCommand("sub-1", "SUSPEND", "policy violation");
    expect(postMock).toHaveBeenCalledWith(
      "/api/v1/executive/subscriptions/sub-1/lifecycle/SUSPEND",
      { reason: "policy violation" },
    );
  });

  it("posts change previews to the change-preview route", async () => {
    await scpApi.previewChange("sub-1", "version-9", "SA");
    expect(postMock).toHaveBeenCalledWith("/api/v1/executive/subscriptions/sub-1/change-preview", {
      targetPlanVersionId: "version-9",
      countryCode: "SA",
    });
  });
});
