import { describe, expect, it, vi } from "vitest";
import { ApiClient } from "./client";
import { createRegistrationApi } from "./registration";

describe("createRegistrationApi", () => {
  it("posts workspace registration data to the public auth endpoint", async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({
      message: "تم إنشاء مساحة العمل.",
      subdomain: "acme",
      passwordSetupRequired: true,
    }), {
      status: 201,
      headers: { "content-type": "application/json" },
    }));
    vi.stubGlobal("fetch", fetchMock);

    const api = createRegistrationApi(new ApiClient({
      baseUrl: "https://api.example.com",
      timeoutMs: 1000,
    }));

    const result = await api.register({
      displayName: "Abdulrahman Sinan",
      email: "admin@example.com",
      organizationName: "Acme Company",
      subdomain: "acme",
      acceptTerms: true,
    });

    expect(result.subdomain).toBe("acme");
    expect(result.passwordSetupRequired).toBe(true);
    expect(fetchMock).toHaveBeenCalledOnce();
    expect(fetchMock.mock.calls[0][0]).toBe("https://api.example.com/api/v1/auth/register");
  });
});
