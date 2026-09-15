// @vitest-environment jsdom
import "@testing-library/jest-dom/vitest";
import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const overviewMock = vi.fn();

vi.mock("@/lib/api/scp-api", () => ({
  scpApi: { overview: (...args: unknown[]) => overviewMock(...args) },
}));

vi.mock("@/lib/i18n/I18nProvider", () => ({
  useI18n: () => ({ t: (key: string) => key }),
}));

vi.mock("./_components/format", () => ({
  useScpFormat: () => ({
    number: (value: number) => String(value),
    money: (value: number, currency: string) => `${value} ${currency}`,
  }),
}));

vi.mock("./_components/scp-errors", () => ({
  scpErrorMessage: () => "SAFE_OVERVIEW_ERROR",
}));

import ExecutiveOverviewPage from "./page";

beforeEach(() => overviewMock.mockReset());
afterEach(() => cleanup());

describe("Executive overview error safety", () => {
  it("routes backend failures through the SCP safe error mapper", async () => {
    overviewMock.mockRejectedValueOnce(new Error("RAW_INTERNAL_OVERVIEW_ERROR"));

    render(<ExecutiveOverviewPage />);

    const alert = await screen.findByRole("alert");
    expect(alert).toHaveTextContent("SAFE_OVERVIEW_ERROR");
    expect(alert).not.toHaveTextContent("RAW_INTERNAL_OVERVIEW_ERROR");
  });
});
