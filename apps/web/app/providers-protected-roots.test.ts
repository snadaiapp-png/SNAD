// @vitest-environment jsdom

/**
 * R0C-12 G6-R1 — the authoritative implementation plan (G6.2) requires
 * `/executive` to be listed in PROTECTED_ROOTS so that a session loss on any
 * executive route triggers the AuthRouteRecovery redirect-with-returnUrl
 * instead of only the in-page gate.
 */
import { describe, it, expect } from "vitest";

import { PROTECTED_ROOTS } from "./providers";

describe("PROTECTED_ROOTS", () => {
  it("protects /executive so session recovery redirects back to it", () => {
    expect(PROTECTED_ROOTS).toContain("/executive");
  });

  it("keeps the existing protected roots intact (no regression)", () => {
    expect(PROTECTED_ROOTS).toEqual(
      expect.arrayContaining(["/workspace", "/crm", "/control-plane"]),
    );
  });

  it("matches executive sub-routes with the root-prefix semantics", () => {
    const executiveProtected = (pathname: string) =>
      PROTECTED_ROOTS.some(
        (root) => pathname === root || pathname.startsWith(`${root}/`),
      );
    expect(executiveProtected("/executive")).toBe(true);
    expect(executiveProtected("/executive/tenants")).toBe(true);
    expect(executiveProtected("/executive/subscriptions/abc")).toBe(true);
    expect(executiveProtected("/workspace")).toBe(true);
    expect(executiveProtected("/")).toBe(false);
  });
});
