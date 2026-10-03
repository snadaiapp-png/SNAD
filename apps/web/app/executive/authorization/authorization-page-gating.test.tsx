// @vitest-environment jsdom
import "@testing-library/jest-dom/vitest";
import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const hasAnyMock = vi.fn();
const hasMock = vi.fn();
const platformUsersMock = vi.fn();

vi.mock("../_components/ScpAccess", () => ({
  useScpAccess: () => ({
    phase: "authorized",
    hasAny: hasAnyMock,
    has: hasMock,
    refresh: vi.fn(),
  }),
}));

vi.mock("@/lib/api/scp-platform-iam-api", () => ({
  scpApi: {
    platformUsers: (...args: unknown[]) => platformUsersMock(...args),
  },
}));

vi.mock("@/lib/i18n/I18nProvider", () => ({
  useI18n: () => ({ locale: "en", t: (key: string) => key }),
}));

import AuthorizationPage from "./page";
import { authorizationLocaleParity } from "@/lib/i18n/authorization-l10n";

beforeEach(() => {
  hasAnyMock.mockReset();
  hasMock.mockReset();
  platformUsersMock.mockReset();
  hasMock.mockImplementation((capability: string) => capability === "PLATFORM.USER.READ");
  platformUsersMock.mockResolvedValue([]);
});

afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
});

describe("authorization admin convergence", () => {
  it("fails closed when backend capability state does not grant authorization access", () => {
    hasAnyMock.mockReturnValue(false);
    render(<AuthorizationPage />);
    expect(screen.getByText(/do not have permission/i)).toBeInTheDocument();
    expect(platformUsersMock).not.toHaveBeenCalled();
  });

  it("uses the canonical platform-user directory instead of a manual user UUID lookup", async () => {
    hasAnyMock.mockReturnValue(true);
    platformUsersMock.mockResolvedValue([
      {
        userId: "00000000-0000-0000-0000-000000000010",
        email: "owner@example.test",
        displayName: "Platform Owner",
        accountStatus: "ACTIVE",
        membershipStatus: "ACTIVE",
        lastLoginAt: null,
        joinedAt: "2026-01-01T00:00:00Z",
      },
    ]);

    render(<AuthorizationPage />);

    expect(await screen.findByText("Platform Owner")).toBeInTheDocument();
    expect(screen.getByText("owner@example.test")).toBeInTheDocument();
    expect(screen.queryByLabelText(/user id/i)).not.toBeInTheDocument();
    expect(screen.getByRole("link", { name: /open effective access/i })).toHaveAttribute(
      "href",
      "/executive/authorization/users/00000000-0000-0000-0000-000000000010",
    );
  });

  it("fails closed when authorization access exists but platform-user read is absent", () => {
    hasAnyMock.mockReturnValue(true);
    hasMock.mockReturnValue(false);
    render(<AuthorizationPage />);
    expect(
      screen.getByText(/platform user read access is required/i),
    ).toBeInTheDocument();
    expect(platformUsersMock).not.toHaveBeenCalled();
  });

  it("keeps the route-scoped Arabic and English dictionaries in exact key parity", () => {
    expect(authorizationLocaleParity.ar).toEqual(authorizationLocaleParity.en);
  });
});
