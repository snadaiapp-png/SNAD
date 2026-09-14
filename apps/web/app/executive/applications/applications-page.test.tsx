// @vitest-environment jsdom

import "@testing-library/jest-dom/vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const applicationsMock = vi.fn();
const createApplicationMock = vi.fn();
const updateApplicationMock = vi.fn();
const hasMock = vi.fn();

vi.mock("@/lib/api/scp-api", () => ({
  scpApi: {
    applications: (...args: unknown[]) => applicationsMock(...args),
    createApplication: (...args: unknown[]) => createApplicationMock(...args),
    updateApplication: (...args: unknown[]) => updateApplicationMock(...args),
  },
}));

vi.mock("../_components/ScpAccess", () => ({
  useScpAccess: () => ({
    phase: "authorized",
    authenticated: true,
    loading: false,
    error: false,
    capabilities: {},
    has: (capability: string) => hasMock(capability),
    hasAll: () => false,
    hasAny: () => false,
    refresh: () => undefined,
  }),
}));

vi.mock("@/lib/i18n/I18nProvider", () => ({
  useI18n: () => ({ locale: "ar", direction: "rtl", t: (key: string) => key }),
}));

vi.mock("../_components/format", () => ({
  useScpFormat: () => ({ day: (value: string) => value }),
}));

import ApplicationsPage from "./page";

const APP = {
  id: "a0000000-0000-4000-8000-000000000001",
  code: "ERP",
  name: "ERP",
  localizedName: "المؤسسات",
  description: "Enterprise resource planning",
  category: "MODULE",
  status: "ACTIVE",
  version: "1.0",
  displayOrder: 10,
  iconKey: "erp",
  provisioningMode: "IMMEDIATE",
  supportedCountries: ["SA"],
  dependencies: [],
  createdAt: "2026-09-01T00:00:00Z",
  updatedAt: "2026-09-01T00:00:00Z",
};

const ARCHIVED_APP = {
  ...APP,
  id: "a0000000-0000-4000-8000-000000000002",
  code: "HRM",
  name: "HRM",
  localizedName: "الموارد البشرية",
  status: "ARCHIVED",
};

beforeEach(() => {
  applicationsMock.mockResolvedValue([APP, ARCHIVED_APP]);
  createApplicationMock.mockReset();
  updateApplicationMock.mockReset();
  hasMock.mockReset();
});

afterEach(() => cleanup());

describe("ApplicationsPage governed mutations", () => {
  it("manager can edit and archive an application; archive targets ARCHIVED with no physical delete", async () => {
    const user = userEvent.setup();
    hasMock.mockImplementation((capability: string) => capability === "EXECUTIVE_MANAGE");
    updateApplicationMock.mockResolvedValue({ ...APP, status: "ARCHIVED" });

    render(<ApplicationsPage />);
    await waitFor(() => expect(screen.getByText("المؤسسات")).toBeInTheDocument());

    expect(screen.getAllByRole("button", { name: "scp.applications.edit" }).length).toBeGreaterThan(0);
    await user.click(screen.getAllByRole("button", { name: "scp.applications.archive" })[0]);

    await waitFor(() =>
      expect(updateApplicationMock).toHaveBeenCalledWith(
        APP.id,
        expect.objectContaining({ code: "ERP", status: "ARCHIVED" }),
      ),
    );
    // No physical delete: the only mutation client is updateApplication.
    expect(screen.queryByRole("button", { name: "scp.applications.delete" })).not.toBeInTheDocument();
  });

  it("manager can restore an archived application back to ACTIVE", async () => {
    const user = userEvent.setup();
    hasMock.mockImplementation((capability: string) => capability === "EXECUTIVE_MANAGE");
    updateApplicationMock.mockResolvedValue({ ...ARCHIVED_APP, status: "ACTIVE" });

    render(<ApplicationsPage />);
    await waitFor(() => expect(screen.getByText("المؤسسات")).toBeInTheDocument());

    const restoreButtons = screen.getAllByRole("button", { name: "scp.applications.restore" });
    await user.click(restoreButtons[0]);

    await waitFor(() =>
      expect(updateApplicationMock).toHaveBeenCalledWith(
        ARCHIVED_APP.id,
        expect.objectContaining({ code: "HRM", status: "ACTIVE" }),
      ),
    );
  });

  it("read-only actor sees no catalog mutation controls", async () => {
    hasMock.mockReturnValue(false);

    render(<ApplicationsPage />);
    await waitFor(() => expect(screen.getByText("المؤسسات")).toBeInTheDocument());

    expect(screen.queryByRole("button", { name: "scp.applications.create" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "scp.applications.edit" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "scp.applications.archive" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "scp.applications.restore" })).not.toBeInTheDocument();
  });
});
