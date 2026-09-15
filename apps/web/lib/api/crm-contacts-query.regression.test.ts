import { afterEach, describe, expect, it, vi } from "vitest";

vi.mock("./client", () => ({
  apiClient: {
    get: vi.fn(),
    post: vi.fn(),
    patch: vi.fn(),
    put: vi.fn(),
    delete: vi.fn(),
    getBlob: vi.fn(),
  },
}));

import { crmApi } from "./crm";
import { apiClient } from "./client";

afterEach(() => vi.clearAllMocks());

describe("crmApi contacts query contract", () => {
  it("omits a blank search value instead of sending search= to the V2 contacts endpoint", async () => {
    vi.mocked(apiClient.get).mockResolvedValue({
      data: [],
      page: { nextCursor: null, hasMore: false, limit: 200 },
    });

    await crmApi.contacts(undefined, "   ");

    expect(apiClient.get).toHaveBeenCalledWith(
      "/api/v2/crm/contacts",
      {
        query: {
          limit: 200,
          accountId: undefined,
          search: undefined,
          cursor: undefined,
        },
        cache: "no-store",
      },
    );
  });

  it("trims a non-blank contacts search before sending it", async () => {
    vi.mocked(apiClient.get).mockResolvedValue({
      data: [],
      page: { nextCursor: null, hasMore: false, limit: 200 },
    });

    await crmApi.contacts(undefined, "  Ada  ");

    expect(apiClient.get).toHaveBeenCalledWith(
      "/api/v2/crm/contacts",
      {
        query: {
          limit: 200,
          accountId: undefined,
          search: "Ada",
          cursor: undefined,
        },
        cache: "no-store",
      },
    );
  });
});
