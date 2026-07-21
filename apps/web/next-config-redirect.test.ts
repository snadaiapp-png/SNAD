import { describe, expect, it } from "vitest";
import { NextRequest } from "next/server";

import { CRM_ROOT_ENTRY_COOKIE, middleware } from "./middleware";

describe("CRM root HTTP redirect", () => {
  it("redirects before the authenticated SPA boots and preserves root intent", () => {
    const response = middleware(new NextRequest("http://localhost:3000/crm"));

    expect(response.status).toBe(307);
    expect(response.headers.get("location")).toBe("http://localhost:3000/crm/overview");
    expect(response.cookies.get(CRM_ROOT_ENTRY_COOKIE)?.value).toBe("1");
  });
});
