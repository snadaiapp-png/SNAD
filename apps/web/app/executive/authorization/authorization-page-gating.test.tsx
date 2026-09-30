// @vitest-environment jsdom
import "@testing-library/jest-dom/vitest";
import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";

const hasAnyMock = vi.fn();

vi.mock("../_components/ScpAccess", () => ({
  useScpAccess: () => ({ hasAny: hasAnyMock, has: vi.fn(() => false) }),
}));

vi.mock("@/lib/i18n/I18nProvider", () => ({
  useI18n: () => ({ locale: "en" }),
}));

import AuthorizationPage from "./page";
import { authorizationLocaleParity } from "@/lib/i18n/authorization-l10n";

afterEach(() => { cleanup(); hasAnyMock.mockReset(); });

describe("authorization admin fail-closed gating", () => {
  it("renders no administration controls when backend capability state does not grant access", () => {
    hasAnyMock.mockReturnValue(false);
    render(<AuthorizationPage />);
    expect(screen.getByText(/do not have permission/i)).toBeInTheDocument();
    expect(screen.queryByRole("textbox")).not.toBeInTheDocument();
  });

  it("renders the user lookup only after an explicit grant", () => {
    hasAnyMock.mockReturnValue(true);
    render(<AuthorizationPage />);
    expect(screen.getByRole("textbox")).toBeInTheDocument();
  });

  it("keeps the route-scoped Arabic and English dictionaries in exact key parity", () => {
    expect(authorizationLocaleParity.ar).toEqual(authorizationLocaleParity.en);
  });
});
