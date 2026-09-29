// @vitest-environment jsdom
import "@testing-library/jest-dom/vitest";
import { cleanup, render, screen, within, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const { api, access } = vi.hoisted(() => ({
  api: {
    platformRoles: vi.fn(),
    platformCapabilities: vi.fn(),
    platformRoleCapabilities: vi.fn(),
    replacePlatformRoleCapabilities: vi.fn(),
    createPlatformRole: vi.fn(),
    updatePlatformRole: vi.fn(),
  },
  access: { has: vi.fn((cap: string) => ["PLATFORM.ROLE.READ", "PLATFORM.PERMISSION.READ"].includes(cap)) },
}));

vi.mock("@/lib/api/scp-platform-iam-api", () => ({ scpApi: api }));
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
import userEvent from "@testing-library/user-event";

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

const customRole = (id: string) => ({
  id, code: id.toUpperCase(), name: id, description: null, status: "ACTIVE",
  roleType: "CUSTOM", protectedRole: false, ownerRole: false,
});
function manageAccess() {
  access.has = vi.fn((cap: string) => cap !== "PLATFORM.USER.READ");
}
async function selectRole(code: string) {
  const heading = await screen.findByRole("heading", { name: code });
  await userEvent.click(within(heading.closest("article")!).getByRole("button", { name: "Capabilities" }));
}

it("renders protected and owner role selections as read-only", async () => {
  manageAccess();
  render(<PlatformAccessPage />);
  await selectRole("PLATFORM_OWNER");
  await waitFor(() => expect(api.platformRoleCapabilities).toHaveBeenCalledWith("r1"));
  expect(screen.queryByRole("button", { name: "scp.access.saveCapabilities" })).not.toBeInTheDocument();
  expect(screen.queryByRole("checkbox")).not.toBeInTheDocument();
});

it("disables capability saving while the selected role is loading", async () => {
  manageAccess();
  api.platformRoles.mockResolvedValue([customRole("a"), customRole("b")]);
  api.platformRoleCapabilities.mockResolvedValueOnce([{ capabilityId: "c1" }])
    .mockImplementationOnce(() => new Promise(() => {}));
  render(<PlatformAccessPage />);
  await selectRole("A");
  await waitFor(() => expect(screen.getByRole("checkbox")).toBeChecked());
  await selectRole("B");
  expect(screen.getByRole("button", { name: "scp.access.saveCapabilities" })).toBeDisabled();
});

it("displays an initial fetch failure with a retry instead of an endless skeleton", async () => {
  api.platformRoles.mockRejectedValueOnce(new Error("unavailable"));
  render(<PlatformAccessPage />);
  expect(await screen.findByRole("alert")).toBeInTheDocument();
  await userEvent.click(screen.getByRole("button", { name: "scp.state.retry" }));
  await waitFor(() => expect(api.platformRoles).toHaveBeenCalledTimes(2));
});

it("creates a custom role through the canonical API", async () => {
  manageAccess();
  api.createPlatformRole.mockResolvedValue(customRole("support"));
  render(<PlatformAccessPage />);
  await screen.findByText("PLATFORM_OWNER");
  await userEvent.type(screen.getByLabelText("scp.access.roleCode"), "SUPPORT_CUSTOM");
  await userEvent.type(screen.getByLabelText("scp.access.roleName"), "Support");
  await userEvent.click(screen.getByRole("button", { name: "scp.access.createRole" }));
  await waitFor(() => expect(api.createPlatformRole).toHaveBeenCalledWith({
    code: "SUPPORT_CUSTOM", name: "Support", description: "",
  }));
});

it("updates only a selected custom role through the canonical API", async () => {
  manageAccess();
  api.platformRoles.mockResolvedValue([customRole("a")]);
  api.updatePlatformRole.mockResolvedValue(customRole("a"));
  render(<PlatformAccessPage />);
  await selectRole("A");
  const name = screen.getByLabelText("scp.access.editRoleName");
  await userEvent.clear(name);
  await userEvent.type(name, "Updated custom");
  await userEvent.click(screen.getByRole("button", { name: "scp.access.updateRole" }));
  await waitFor(() => expect(api.updatePlatformRole).toHaveBeenCalledWith("a", {
    name: "Updated custom", description: "",
  }));
});
