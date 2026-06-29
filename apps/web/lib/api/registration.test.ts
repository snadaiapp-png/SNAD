import { describe, expect, it, vi } from "vitest";
import { ApiClient } from "./client";
import { createRegistrationApi } from "./registration";

describe("createRegistrationApi", () => {
  it("posts the regional registration contract", async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({
      message: "created",
      subdomain: "workspace-id",
      passwordSetupRequired: true,
    }), { status: 201, headers: { "content-type": "application/json" } }));
    vi.stubGlobal("fetch", fetchMock);

    const api = createRegistrationApi(new ApiClient({
      baseUrl: "https://api.example.com",
      timeoutMs: 1000,
    }));

    const result = await api.register({
      displayName: "Account Owner",
      email: "owner@example.com",
      organizationName: "Example Company",
      regionCode: "SA",
      countryCode: `+${966}`,
      mobileNumber: `5${"0".repeat(8)}`,
      acceptTerms: true,
    });

    expect(result.passwordSetupRequired).toBe(true);
    expect(fetchMock).toHaveBeenCalledOnce();
  });
});
