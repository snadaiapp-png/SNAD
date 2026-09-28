// @vitest-environment jsdom
import "@testing-library/jest-dom/vitest";
import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const { api, access } = vi.hoisted(() => ({
  api: { platformUsers: vi.fn() },
  access: { has: vi.fn((cap: string): boolean => cap === "PLATFORM.USER.READ") },
}));

vi.mock("@/lib/api/scp-api", () => ({ scpApi: api }));
vi.mock("../_components/ScpAccess", () => ({ useScpAccess: () => access }));
vi.mock("@/lib/i18n/I18nProvider", () => ({
  useI18n: () => ({ t: (key: string) => ({
    "scp.users.title": "Platform Users",
    "scp.users.subtitle": "Control-plane identities",
    "scp.users.empty": "No platform users",
    "scp.users.forbidden": "Platform user read access required",
    "scp.state.loading": "Loading",
  } as Record<string, string>)[key] ?? key }),
}));

import PlatformUsersPage from "./page";

beforeEach(() => {
  api.platformUsers.mockReset().mockResolvedValue([
    { userId: "u1", email: "owner@example.com", displayName: "Owner", accountStatus: "ACTIVE", membershipStatus: "ACTIVE", lastLoginAt: null, joinedAt: "2026-09-28T00:00:00Z" },
  ]);
  access.has = vi.fn((cap: string): boolean => cap === "PLATFORM.USER.READ");
});
afterEach(() => cleanup());

describe("Executive Platform Users", () => {
  it("loads the platform directory without exposing a control-tenant selector", async () => {
    render(<PlatformUsersPage />);
    expect(await screen.findByRole("heading", { name: "Platform Users" })).toBeInTheDocument();
    expect(api.platformUsers).toHaveBeenCalledTimes(1);
    expect(screen.getByText("owner@example.com")).toBeInTheDocument();
    expect(screen.queryByLabelText(/control.*tenant|tenant.*id/i)).not.toBeInTheDocument();
  });

  it("fails closed without PLATFORM.USER.READ", async () => {
    access.has = vi.fn((_cap: string): boolean => false);
    render(<PlatformUsersPage />);
    expect(await screen.findByRole("alert")).toHaveTextContent("Platform user read access required");
    expect(api.platformUsers).not.toHaveBeenCalled();
  });
});
