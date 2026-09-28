// @vitest-environment jsdom
import "@testing-library/jest-dom/vitest";
import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const { api, access } = vi.hoisted(() => ({
  api: {
    platformUser: vi.fn(),
    platformUserRoles: vi.fn(),
    platformUserPermissions: vi.fn(),
    platformUserSessions: vi.fn(),
    platformRoles: vi.fn(),
    platformUserTemporaryAccess: vi.fn(),
    platformCapabilities: vi.fn(),
  },
  access: { has: vi.fn(() => true) },
}));

vi.mock("@/lib/api/scp-platform-iam-api", () => ({ scpApi: api }));
vi.mock("../../_components/ScpAccess", () => ({ useScpAccess: () => access }));
vi.mock("next/navigation", () => ({ useParams: () => ({ userId: "u1" }) }));
vi.mock("@/lib/i18n/I18nProvider", () => ({
  useI18n: () => ({ t: (key: string) => ({
    "scp.userDetail.title": "Platform User",
    "scp.userDetail.membership": "Membership",
    "scp.userDetail.roles": "Roles",
    "scp.userDetail.permissions": "Effective permissions",
    "scp.userDetail.sessions": "Session security",
    "scp.userDetail.forbidden": "Platform user read access required",
    "scp.state.loading": "Loading",
  } as Record<string, string>)[key] ?? key }),
}));

import PlatformUserDetailPage from "./page";

beforeEach(() => {
  api.platformUser.mockReset().mockResolvedValue({
    userId: "u1", email: "owner@example.com", displayName: "Owner", accountStatus: "ACTIVE", membershipStatus: "ACTIVE", lastLoginAt: null, joinedAt: "2026-09-28T00:00:00Z",
  });
  api.platformUserRoles.mockReset().mockResolvedValue([{ id: "g1", tenantId: "t1", userId: "u1", roleId: "r1", roleCode: "PLATFORM_OWNER", organizationId: null, status: "ACTIVE", createdAt: "2026-09-28T00:00:00Z", updatedAt: "2026-09-28T00:00:00Z" }]);
  api.platformUserPermissions.mockReset().mockResolvedValue(["PLATFORM.USER.READ"]);
  api.platformUserSessions.mockReset().mockResolvedValue({ userId: "u1", sessionVersion: 2, lastLoginAt: null });
  api.platformRoles.mockReset().mockResolvedValue([]);
  api.platformUserTemporaryAccess.mockReset().mockResolvedValue([]);
  api.platformCapabilities.mockReset().mockResolvedValue([]);
  access.has = vi.fn(() => true);
});
afterEach(() => cleanup());

describe("Executive Platform User detail", () => {
  it("shows membership, roles, effective permissions and session security from backend data", async () => {
    render(<PlatformUserDetailPage />);
    expect(await screen.findByRole("heading", { name: "Platform User" })).toBeInTheDocument();
    expect(screen.getByText("owner@example.com")).toBeInTheDocument();
    expect(screen.getByText("PLATFORM_OWNER")).toBeInTheDocument();
    expect(screen.getByText("PLATFORM.USER.READ")).toBeInTheDocument();
    expect(screen.getByText(/2/)).toBeInTheDocument();
  });
});

it("provides the temporary access section on the platform user detail", async () => {
  render(<PlatformUserDetailPage />);
  expect(await screen.findByRole("heading", { name: "scp.temporary.title" })).toBeInTheDocument();
});
