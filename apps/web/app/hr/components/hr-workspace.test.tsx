// @vitest-environment jsdom

/**
 * WS5 Task 8 — shared HR workspace contract tests.
 *
 * Pins the Arabic-first workspace shell: the authoritative navigation set,
 * RTL direction semantics, accessibility landmarks, and the capability-aware
 * (UX-only) gating contract. Backend authorization remains authoritative.
 */
import "@testing-library/jest-dom/vitest";

import { cleanup, render, screen } from "@testing-library/react";
import type React from "react";
import { afterEach, describe, expect, it, vi } from "vitest";

import { HrWorkspace } from "./hr-workspace";
import { HRM_CAPABILITIES } from "@/lib/auth/capabilities";

const { useRouterMock } = vi.hoisted(() => ({ useRouterMock: vi.fn() }));

vi.mock("next/navigation", () => ({
  useRouter: useRouterMock,
  usePathname: () => "/hr",
}));

vi.mock("next/link", () => ({
  default: ({ href, children, ...props }: React.AnchorHTMLAttributes<HTMLAnchorElement>) => (
    <a href={String(href)} {...props}>{children}</a>
  ),
}));

const FULL_CAPS = Object.values(HRM_CAPABILITIES);
const NO_HR_CAPS: string[] = [];

afterEach(() => cleanup());

describe("HrWorkspace navigation", () => {
  it("renders every authoritative workspace link", () => {
    render(
      <HrWorkspace capabilities={FULL_CAPS} activeHref="/hr">
        <p>المحتوى</p>
      </HrWorkspace>,
    );

    const expected = [
      { href: "/hr", name: "الرئيسية" },
      { href: "/hr/employees", name: "الموظفون" },
      { href: "/hr/org-structure", name: "الهيكل التنظيمي" },
      { href: "/hr/jobs", name: "الوظائف" },
      { href: "/hr/positions", name: "المناصب" },
      { href: "/hr/assignments", name: "الإسنادات" },
      { href: "/hr/compliance", name: "الالتزام" },
      { href: "/hr/execution", name: "لوحة التنفيذ" },
    ];
    for (const { href, name } of expected) {
      const link = screen.getByRole("link", { name });
      expect(link).toHaveAttribute("href", href);
    }
  });

  it("marks the active route with aria-current", () => {
    render(
      <HrWorkspace capabilities={FULL_CAPS} activeHref="/hr/employees">
        <p>المحتوى</p>
      </HrWorkspace>,
    );
    expect(screen.getByRole("link", { name: "الموظفون" })).toHaveAttribute("aria-current", "page");
    expect(screen.getByRole("link", { name: "الرئيسية" })).not.toHaveAttribute("aria-current");
  });

  it("keeps the execution dashboard reachable regardless of capabilities", () => {
    render(
      <HrWorkspace capabilities={NO_HR_CAPS} activeHref="/hr">
        <p>المحتوى</p>
      </HrWorkspace>,
    );
    expect(screen.getByRole("link", { name: "لوحة التنفيذ" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "الرئيسية" })).toBeInTheDocument();
  });

  it("hides capability-gated sections from users without the matching UX capability", () => {
    render(
      <HrWorkspace capabilities={NO_HR_CAPS} activeHref="/hr">
        <p>المحتوى</p>
      </HrWorkspace>,
    );
    expect(screen.queryByRole("link", { name: "الموظفون" })).not.toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "الالتزام" })).not.toBeInTheDocument();
  });

  it("exposes navigation as a labelled landmark and renders children in main", () => {
    render(
      <HrWorkspace capabilities={FULL_CAPS} activeHref="/hr">
        <p>المحتوى</p>
      </HrWorkspace>,
    );
    expect(screen.getByRole("navigation", { name: "أقسام الموارد البشرية" })).toBeInTheDocument();
    expect(screen.getByRole("main")).toContainElement(screen.getByText("المحتوى"));
  });
});
