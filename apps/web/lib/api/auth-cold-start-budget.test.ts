import { describe, expect, it, vi } from "vitest";
import { createAuthApi } from "./auth";
import { ApiClient } from "./client";

describe("browser auth cold-start budget", () => {
  it("keeps the browser login budget above the BFF cold-start budget", async () => {
    const post = vi.fn().mockResolvedValue({
      accessToken: "access",
      expiresAt: "2099-01-01T00:00:00Z",
      user: {
        id: "u1",
        tenantId: "t1",
        email: "admin@example.com",
        displayName: null,
        status: "ACTIVE",
      },
    });
    const client = { post } as unknown as ApiClient;
    const api = createAuthApi(client);

    await api.login({ email: "admin@example.com", password: "secret" });

    expect(post).toHaveBeenCalledTimes(1);
    const options = post.mock.calls[0][2] as { timeoutMs?: number } | undefined;
    expect(options?.timeoutMs).toBeGreaterThan(120_000);
  });
});
