// @vitest-environment jsdom
import "@testing-library/jest-dom/vitest";
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, expect, it, vi } from "vitest";

const { api, access } = vi.hoisted(() => ({
  api: {
    platformUserTemporaryAccess: vi.fn(), platformCapabilities: vi.fn(),
    grantPlatformUserTemporaryAccess: vi.fn(), revokePlatformUserTemporaryAccess: vi.fn(),
  },
  access: { has: vi.fn((cap: string) => Boolean(cap)) },
}));
vi.mock("@/lib/api/scp-platform-iam-api", () => ({ scpApi: api }));
vi.mock("../../_components/ScpAccess", () => ({ useScpAccess: () => access }));
vi.mock("@/lib/i18n/I18nProvider", () => ({ useI18n: () => ({ t: (key: string) => key }) }));
import { TemporaryAccessCard } from "./TemporaryAccessCard";

beforeEach(() => {
  vi.clearAllMocks();
  access.has.mockImplementation(() => true);
  api.platformUserTemporaryAccess.mockResolvedValue([]);
  api.platformCapabilities.mockResolvedValue([{ id: "c1", code: "PLATFORM.USER.READ", status: "ACTIVE" }]);
  api.grantPlatformUserTemporaryAccess.mockResolvedValue({});
  api.revokePlatformUserTemporaryAccess.mockResolvedValue(undefined);
});
afterEach(cleanup);

it("fails closed without read permission and makes no data requests", () => {
  access.has.mockReturnValue(false);
  render(<TemporaryAccessCard userId="u1" />);
  expect(screen.getByRole("alert")).toHaveTextContent("scp.temporary.forbidden");
  expect(api.platformUserTemporaryAccess).not.toHaveBeenCalled();
  expect(screen.queryByRole("button", { name: "scp.temporary.grant" })).not.toBeInTheDocument();
});

it("requires a future expiry and sends the reason without any tenant authority", async () => {
  const user = userEvent.setup();
  render(<TemporaryAccessCard userId="u1" />);
  await screen.findByRole("option", { name: "PLATFORM.USER.READ" });
  await user.selectOptions(screen.getByLabelText("scp.temporary.capability"), "c1");
  fireEvent.change(screen.getByLabelText("scp.temporary.expiry"), { target: { value: "2099-01-01T12:00" } });
  await user.type(screen.getByLabelText("scp.temporary.reason"), "Coverage");
  await user.click(screen.getByRole("button", { name: "scp.temporary.grant" }));
  await waitFor(() => expect(api.grantPlatformUserTemporaryAccess).toHaveBeenCalledWith("u1", {
    capabilityId: "c1", effectiveTo: new Date("2099-01-01T12:00").toISOString(), reason: "Coverage",
  }));
});

it("requires confirmation and a reason before revocation", async () => {
  const user = userEvent.setup();
  api.platformUserTemporaryAccess.mockResolvedValue([{
    id: "g1", userId: "u1", capabilityCode: "PLATFORM.USER.READ", status: "ACTIVE",
    effectiveTo: "2099-01-01T12:00:00Z", reason: "Coverage", grantedBy: "actor-1",
  }]);
  render(<TemporaryAccessCard userId="u1" />);
  await user.click(await screen.findByRole("button", { name: "scp.temporary.revoke" }));
  expect(api.revokePlatformUserTemporaryAccess).not.toHaveBeenCalled();
  await user.type(screen.getByLabelText("scp.temporary.revokeReason"), "Resolved");
  await user.click(screen.getByRole("button", { name: "scp.temporary.confirmRevoke" }));
  await waitFor(() => expect(api.revokePlatformUserTemporaryAccess).toHaveBeenCalledWith("u1", "g1", "Resolved"));
});

it("shows backend history while hiding mutations for read-only operators", async () => {
  access.has.mockImplementation((cap) => cap === "PLATFORM.PERMISSION.READ");
  render(<TemporaryAccessCard userId="u1" />);
  expect(await screen.findByText("scp.temporary.empty")).toBeInTheDocument();
  expect(api.platformCapabilities).not.toHaveBeenCalled();
  expect(screen.queryByRole("button", { name: "scp.temporary.grant" })).not.toBeInTheDocument();
});
