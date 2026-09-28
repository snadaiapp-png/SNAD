import { beforeEach, describe, expect, it, vi } from "vitest";

const getMock = vi.fn();
const postMock = vi.fn();
const putMock = vi.fn();
const patchMock = vi.fn();

vi.mock("./client", () => ({
  apiClient: {
    get: (...args: unknown[]) => getMock(...args),
    post: (...args: unknown[]) => postMock(...args),
    put: (...args: unknown[]) => putMock(...args),
    patch: (...args: unknown[]) => patchMock(...args),
  },
}));

import { scpApi } from "./scp-api";

describe("scpApi — Platform IAM", () => {
  beforeEach(() => {
    getMock.mockReset().mockResolvedValue({});
    postMock.mockReset().mockResolvedValue({});
    putMock.mockReset().mockResolvedValue({});
    patchMock.mockReset().mockResolvedValue({});
  });

  it("uses the canonical platform-user routes with no controlTenantId input", async () => {
    await scpApi.platformUsers();
    await scpApi.platformUser("user-1");
    await scpApi.createPlatformUser({ email: "owner@example.com", displayName: "Owner" });
    await scpApi.updatePlatformUser("user-1", { displayName: "Updated" });
    await scpApi.transitionPlatformUser("user-1", "suspend", "incident");

    expect(getMock).toHaveBeenNthCalledWith(1, "/api/v1/executive/users");
    expect(getMock).toHaveBeenNthCalledWith(2, "/api/v1/executive/users/user-1");
    expect(postMock).toHaveBeenNthCalledWith(1, "/api/v1/executive/users", {
      email: "owner@example.com",
      displayName: "Owner",
    });
    expect(patchMock).toHaveBeenCalledWith("/api/v1/executive/users/user-1", { displayName: "Updated" });
    expect(postMock).toHaveBeenNthCalledWith(2, "/api/v1/executive/users/user-1/suspend", { reason: "incident" });
  });

  it("models role, permission and session-security commands on the Executive surface", async () => {
    await scpApi.platformUserRoles("user-1");
    await scpApi.replacePlatformUserRoles("user-1", ["role-1"], "rotation");
    await scpApi.platformUserPermissions("user-1");
    await scpApi.platformUserSessions("user-1");
    await scpApi.revokePlatformUserSessions("user-1", "security response");
    await scpApi.platformRoles();
    await scpApi.platformCapabilities();
    await scpApi.platformRoleCapabilities("role-1");
    await scpApi.replacePlatformRoleCapabilities("role-1", ["cap-1"]);

    expect(getMock).toHaveBeenCalledWith("/api/v1/executive/users/user-1/roles");
    expect(putMock).toHaveBeenCalledWith("/api/v1/executive/users/user-1/roles", {
      roleIds: ["role-1"],
      reason: "rotation",
    });
    expect(getMock).toHaveBeenCalledWith("/api/v1/executive/users/user-1/permissions");
    expect(getMock).toHaveBeenCalledWith("/api/v1/executive/users/user-1/sessions");
    expect(postMock).toHaveBeenCalledWith("/api/v1/executive/users/user-1/sessions/revoke", {
      reason: "security response",
    });
    expect(getMock).toHaveBeenCalledWith("/api/v1/executive/roles");
    expect(getMock).toHaveBeenCalledWith("/api/v1/executive/capabilities");
    expect(getMock).toHaveBeenCalledWith("/api/v1/executive/roles/role-1/capabilities");
    expect(putMock).toHaveBeenCalledWith("/api/v1/executive/roles/role-1/capabilities", {
      capabilityIds: ["cap-1"],
    });
  });
});
