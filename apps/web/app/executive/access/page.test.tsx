// @vitest-environment jsdom
import "@testing-library/jest-dom/vitest";
import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const { api, access } = vi.hoisted(() => ({
  api: {
    platformRoles: vi.fn(),
    platformCapabilities: vi.fn(),
    platformRoleCapabilities: vi.fn(),
    replacePlatformRoleCapabilities: vi.fn(),
  },
  access: { has: vi.fn((cap: string) => ["PLATFORM.ROLE.READ", "PLATFORM.PERMISSION.READ"].includes(cap)) },
}));

vi.mock("@/lib/api/scp-api", () => ({ scpApi: api }));
vi.mock("../_components/ScpAccess", () => ({ useScpAccess: () => access }));
vi.mock("@/lib/i18n/I18nProvider", () => ({
  useI18n: () => ({ t: (key: string) => ({
    "scp.access.title": "Platform Access",
    "scp.access.subtitle": "Roles and capabilities",
    "scp.access.forbidden": "Platform access read permission required",
    "scp.access.roles": "Roles",
    "scp.access.capabilities": "Capabilities",
    "scp.state.loading": "Loading",
  } as Record<string, string>)[key] ?? key }),
}));

import PlatformAccessPage from "./page";

beforeEach(() => {
  api.platformRoles.mockReset().mockResolvedValue([
    { id: "r1", code: "PLATFORM_OWNER", name: "Platform Owner", description: null, status: "ACTIVE", roleType: "SYSTEM", protectedRole: true, ownerRole: true },
  ]);
  api.platformCapabilities.mockReset().mockResolvedValue([
    { id: "c1", code: "PLATFORM.USER.READ", name: "Read platform users", description: null, status: "ACTIVE" },
  ]);
  api.platformRoleCapabilities.mockReset().mockResolvedValue([]);
  api.replacePlatformRoleCapabilities.mockReset();
  access.has = vi.fn((cap: string) => ["PLATFORM.ROLE.READ", "PLATFORM.PERMISSION.READ"].includes(cap));
});
afterEach(() => cleanup());

describe("Executive Platform Access", () => {
  it("renders protected roles and the capability registry", async () => {
    render(<PlatformAccessPage />);
    expect(await screen.findByRole("heading", { name: "Platform Access" })).toBeInTheDocument();
    expect(screen.getByText("PLATFORM_OWNER")).toBeInTheDocument();
    expect(screen.getByText("PLATFORM.USER.READ")).toBeInTheDocument();
  });

  it("fails closed when role/capability read access is absent", async () => {
    access.has = vi.fn(() => false);
    render(<PlatformAccessPage />);
    expect(await screen.findByRole("alert")).toHaveTextContent("Platform access read permission required");
    expect(api.platformRoles).not.toHaveBeenCalled();
    expect(api.platformCapabilities).not.toHaveBeenCalled();
  });
});
