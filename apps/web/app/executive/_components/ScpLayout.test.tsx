// @vitest-environment jsdom
import "@testing-library/jest-dom/vitest";
import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import type React from "react";

const accessState = vi.hoisted(() => ({
  phase: "authorized" as "authorized" | "checking" | "unauthorized" | "degraded",
  capabilities: {
    "subscription.read": true,
    "PLATFORM.USER.READ": true,
    "PLATFORM.ROLE.READ": true,
    "ROLE.READ": true,
  } as Record<string, boolean>,
}));

vi.mock("@/lib/auth/auth-provider", () => ({
  useAuth: () => ({
    state: "AUTHENTICATED",
    me: { displayName: "Executive Operator", email: "operator@example.test" },
    logout: vi.fn(async () => undefined),
  }),
}));

vi.mock("@/lib/i18n/I18nProvider", () => ({
  useI18n: () => ({
    t: (key: string) => `i18n:${key}`,
    locale: "en",
    direction: "ltr",
    setLocale: vi.fn(),
  }),
}));

vi.mock("next/navigation", () => ({
  usePathname: () => "/executive/users",
  useRouter: () => ({ replace: vi.fn(), push: vi.fn() }),
}));

vi.mock("next/link", () => ({
  default: ({ href, children, ...props }: React.AnchorHTMLAttributes<HTMLAnchorElement>) => (
    <a href={String(href)} {...props}>{children}</a>
  ),
}));

vi.mock("./ScpStates", async () => {
  const actual = await vi.importActual<typeof import("./ScpStates")>("./ScpStates");
  return {
    ...actual,
    ScpAuthGate: ({ children }: { children: React.ReactNode }) => <>{children}</>,
  };
});

vi.mock("./ScpAccess", () => ({
  ScpAccessProvider: ({ children }: { children: React.ReactNode }) => <>{children}</>,
  useScpAccess: () => ({
    phase: accessState.phase,
    capabilities: accessState.capabilities,
  }),
}));

import { ScpLayout } from "./ScpLayout";

afterEach(() => cleanup());

describe("Executive shared SNAD module shell", () => {
  it("renders the same shared shell identity used by CRM and HR", () => {
    render(<ScpLayout><p>page</p></ScpLayout>);

    expect(screen.getByText("i18n:scp.layout.title")).toBeInTheDocument();
    expect(screen.getByText("Executive Operator")).toBeInTheDocument();
    expect(screen.getByRole("navigation", { name: "i18n:scp.nav.ariaLabel" })).toBeInTheDocument();

    const users = screen.getByRole("link", { name: "i18n:controlPlane.users" });
    expect(users).toHaveAttribute("href", "/executive/users");
    expect(users).toHaveAttribute("aria-current", "page");
  });

  it("keeps navigation capability-gated and fail-closed after access resolution", () => {
    accessState.capabilities = { "PLATFORM.USER.READ": true };
    render(<ScpLayout><p>page</p></ScpLayout>);

    expect(screen.getByRole("link", { name: "i18n:controlPlane.users" })).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "i18n:controlPlane.roles" })).not.toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "i18n:scp.nav.authorization" })).not.toBeInTheDocument();

    accessState.capabilities = {
      "subscription.read": true,
      "PLATFORM.USER.READ": true,
      "PLATFORM.ROLE.READ": true,
      "ROLE.READ": true,
    };
  });
});
