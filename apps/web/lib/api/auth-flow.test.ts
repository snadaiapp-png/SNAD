import { describe, expect, it } from "vitest";

import { authResponseToMe, type AuthResponse } from "./auth";
import { resolvePostLoginDestination, safeReturnUrl } from "@/lib/auth/destination";

describe("Auth bootstrap contract", () => {
  const response: AuthResponse = {
    accessToken: "test-access-token-not-a-jwt",
    expiresAt: "2099-01-01T00:00:00Z",
    user: {
      id: "user-uuid",
      tenantId: "tenant-uuid",
      email: "test@example.com",
      displayName: "Test User",
      status: "ACTIVE",
    },
    lastLoginAt: "2026-07-18T12:00:00Z",
    credentialRotationRequired: false,
    memberships: [{ id: "membership-1", organizationId: "org-1", status: "ACTIVE" }],
    effectiveRoleGrants: [{
      id: "grant-1",
      roleId: "role-1",
      roleCode: "VIEWER",
      organizationId: "org-1",
      status: "ACTIVE",
    }],
    defaultOrganizationId: "org-1",
    defaultDestination: "/crm",
    availableDestinations: ["/workspace", "/crm"],
    tenantContext: { tenantId: "tenant-uuid", defaultOrganizationId: "org-1" },
  };

  it("contains enough data to finish login without a mandatory /me request", () => {
    expect(response.credentialRotationRequired).toBe(false);
    expect(response.memberships).toHaveLength(1);
    expect(response.effectiveRoleGrants[0].roleCode).toBe("VIEWER");
    expect(response.defaultDestination).toBe("/crm");
  });

  it("maps the bootstrap response to the existing MeResponse view model", () => {
    const me = authResponseToMe(response);
    expect(me.tenantId).toBe("tenant-uuid");
    expect(me.roleGrants).toEqual(response.effectiveRoleGrants);
    expect(me.credentialRotationRequired).toBe(false);
  });

  it("uses a safe authorized return URL before the default destination", () => {
    expect(resolvePostLoginDestination({
      returnUrl: "/crm/leads",
      defaultDestination: response.defaultDestination,
      availableDestinations: response.availableDestinations,
    })).toBe("/crm/leads");
  });

  it("rejects open redirects", () => {
    expect(safeReturnUrl("https://evil.example", response.availableDestinations)).toBeNull();
    expect(resolvePostLoginDestination({
      returnUrl: "//evil.example",
      defaultDestination: response.defaultDestination,
      availableDestinations: response.availableDestinations,
    })).toBe("/crm");
  });
});
