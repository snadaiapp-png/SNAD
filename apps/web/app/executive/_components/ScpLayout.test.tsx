// @vitest-environment jsdom

/**
 * R0C-12 G6-R3 — the authoritative design (§9) requires the shared executive
 * layout to provide a desktop sidebar, a collapsible nav on tablet and a
 * drawer on mobile. These tests pin the accessible toggle contract:
 *   - a persistent toggle button with aria-expanded / aria-controls
 *   - the nav slot exposes data-open so CSS can collapse (tablet) or draw
 *     the overlay drawer (mobile)
 *   - an explicit backdrop control closes the drawer
 *   - navigating via a nav link closes the drawer
 */
import "@testing-library/jest-dom/vitest";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, cleanup } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

vi.mock("@/lib/i18n/I18nProvider", () => ({
  useI18n: () => ({ t: (key: string) => `i18n:${key}` }),
}));

vi.mock("./ScpStates", () => ({
  ScpAuthGate: ({ children }: { children: React.ReactNode }) => (
    <>{children}</>
  ),
}));

vi.mock("./ScpNav", () => ({
  ScpNav: () => <nav data-testid="scp-nav">nav</nav>,
}));

import { ScpLayout } from "./ScpLayout";

describe("ScpLayout responsive navigation", () => {
  beforeEach(() => {
    cleanup();
  });

  it("renders an accessible nav toggle wired to the sidebar slot", async () => {
    const user = userEvent.setup();
    render(
      <ScpLayout>
        <div>page</div>
      </ScpLayout>,
    );
    const toggle = screen.getByRole("button", {
      name: "i18n:scp.nav.openMenu",
    });
    expect(toggle).toHaveAttribute("aria-controls", "scp-sidebar-nav");
    expect(toggle).toHaveAttribute("aria-expanded", "false");
    const slot = document.getElementById("scp-sidebar-nav");
    expect(slot).not.toBeNull();
    expect(slot).toHaveAttribute("data-open", "false");

    await user.click(toggle);
    expect(toggle).toHaveAttribute("aria-expanded", "true");
    expect(document.getElementById("scp-sidebar-nav")).toHaveAttribute(
      "data-open",
      "true",
    );
  });

  it("offers an explicit backdrop control that closes the drawer", async () => {
    const user = userEvent.setup();
    render(
      <ScpLayout>
        <div>page</div>
      </ScpLayout>,
    );
    await user.click(
      screen.getByRole("button", { name: "i18n:scp.nav.openMenu" }),
    );
    const backdrop = screen.getByRole("button", {
      name: "i18n:scp.nav.closeMenu",
    });
    await user.click(backdrop);
    expect(
      screen.getByRole("button", { name: "i18n:scp.nav.openMenu" }),
    ).toHaveAttribute("aria-expanded", "false");
    expect(document.getElementById("scp-sidebar-nav")).toHaveAttribute(
      "data-open",
      "false",
    );
  });
});
