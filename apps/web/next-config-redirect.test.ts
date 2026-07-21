import { describe, expect, it } from "vitest";

import nextConfig from "./next.config";

describe("Next.js HTTP redirects", () => {
  it("redirects /crm to /crm/overview before the authenticated SPA boots", async () => {
    const redirects = await nextConfig.redirects?.();

    expect(redirects).toEqual(
      expect.arrayContaining([
        {
          source: "/crm",
          destination: "/crm/overview",
          permanent: false,
        },
      ]),
    );
  });
});
