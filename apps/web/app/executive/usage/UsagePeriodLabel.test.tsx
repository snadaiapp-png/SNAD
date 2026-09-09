// @vitest-environment jsdom

/**
 * R0C-12 G6-R6 — usage gauges must expose the metering period/reset state
 * (design §9: current/limit/%/period/reset/warning). The backend snapshot
 * now carries `periodStart`; this pins the period renderer.
 */
import "@testing-library/jest-dom/vitest";
import { describe, it, expect, vi } from "vitest";
import { render, cleanup } from "@testing-library/react";

vi.mock("@/lib/i18n/I18nProvider", () => ({
  useI18n: () => ({
    t: (key: string, params?: Record<string, unknown>) =>
      params
        ? `i18n:${key}:${JSON.stringify(params)}`
        : `i18n:${key}`,
    locale: "en",
  }),
}));

import { UsagePeriodLabel } from "./UsagePeriodLabel";

describe("UsagePeriodLabel", () => {
  it("renders the metering period for a snapshot with an aggregate", () => {
    const { container } = render(<UsagePeriodLabel periodStart="2026-09-01T00:00:00Z" />);
    const span = container.querySelector("span");
    expect(span).not.toBeNull();
    expect(span?.textContent ?? "").toContain("i18n:scp.usage.period");
    expect(span?.textContent ?? "").toContain("2026");
    cleanup();
  });

  it("renders nothing when the snapshot has no period", () => {
    const { container } = render(<UsagePeriodLabel periodStart={null} />);
    expect(container.textContent).toBe("");
    cleanup();
  });
});
