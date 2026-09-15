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
    has: (capability: string) => hasMock(capability),
  }),
}));

vi.mock("@/lib/i18n/I18nProvider", () => ({
  useI18n: () => ({ t: (key: string) => key }),
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

beforeEach(() => {
  applicationsMock.mockResolvedValue([APP]);
  createApplicationMock.mockReset();
  updateApplicationMock.mockReset();
  hasMock.mockReset();
});

afterEach(() => cleanup());

describe("ApplicationsPage governed mutations", () => {
  it("manager can edit and retire an application without physical delete", async () => {
    const user = userEvent.setup();
    hasMock.mockImplementation((capability: string) => capability === "EXECUTIVE_MANAGE");
    updateApplicationMock.mockResolvedValue({ ...APP, status: "DEPRECATED" });

    render(<ApplicationsPage />);
    await waitFor(() => expect(screen.getByText("المؤسسات")).toBeInTheDocument());

    expect(screen.getByRole("button", { name: "scp.applications.edit" })).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "scp.applications.archive" }));

    await waitFor(() =>
      expect(updateApplicationMock).toHaveBeenCalledWith(
        APP.id,
        expect.objectContaining({ code: "ERP", status: "DEPRECATED" }),
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
  });
});
