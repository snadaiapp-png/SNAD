// @vitest-environment jsdom

/**
 * R0C-12 G6-R2 — the executive shell must not hardcode a localized string.
 * The layout is a server component (useI18n is a client hook), so a client
 * boundary translates the logo aria label via t() with keys in BOTH locales.
 */
import "@testing-library/jest-dom/vitest";
import { describe, it, expect, vi } from "vitest";
import { render, screen, cleanup } from "@testing-library/react";

const shellProps = vi.fn();

vi.mock("@/components/shell", () => ({
  ExecutiveShell: (props: Record<string, unknown>) => {
    shellProps(props);
    return <div data-testid="shell">{props.children as React.ReactNode}</div>;
  },
}));

vi.mock("@/lib/i18n/I18nProvider", () => ({
  useI18n: () => ({ t: (key: string) => `i18n:${key}` }),
}));

import { ScpExecutiveShell } from "./ScpExecutiveShell";

describe("ScpExecutiveShell", () => {
  it("translates the logo aria label through t() (no hardcoded Arabic)", () => {
    render(
      <ScpExecutiveShell>
        <div>content</div>
      </ScpExecutiveShell>,
    );
    expect(shellProps).toHaveBeenCalledWith(
      expect.objectContaining({
        logoHref: "/executive",
        logoAriaLabel: "i18n:scp.layout.logoAriaLabel",
      }),
    );
    expect(screen.getByTestId("shell")).toBeInTheDocument();
    cleanup();
  });
});
