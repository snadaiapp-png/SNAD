// @vitest-environment jsdom
import "@testing-library/jest-dom/vitest";

import { cleanup, render, screen, waitFor } from "@testing-library/react";
import type React from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const { usersApiMock } = vi.hoisted(() => ({
  usersApiMock: {
    moduleContext: vi.fn(),
  },
}));

vi.mock("@/lib/api/users", () => ({ usersApi: usersApiMock }));
vi.mock("next/link", () => ({
  default: ({ href, children, ...props }: React.AnchorHTMLAttributes<HTMLAnchorElement>) => (
    <a href={String(href)} {...props}>{children}</a>
  ),
}));

import { ModuleAccessNavigation, routeRootForModuleAccess } from "./ModuleAccessNavigation";

const context = {
  applicationCode: "CRM",
  name: "CRM",
  localizedName: "إدارة علاقات العملاء",
  capabilityNamespaces: ["CRM"],
  declaredCapabilities: ["CRM.ACCOUNT.READ"],
  supportedScopes: ["TENANT"],
  roles: [],
};

beforeEach(() => {
  usersApiMock.moduleContext.mockReset();
  usersApiMock.moduleContext.mockResolvedValue(context);
});

afterEach(() => cleanup());

describe("ModuleAccessNavigation", () => {
  it("derives current and future module roots without a frontend registry", () => {
    expect(routeRootForModuleAccess("/crm/accounts")).toBe("crm");
    expect(routeRootForModuleAccess("/hr/employees")).toBe("hr");
    expect(routeRootForModuleAccess("/future-ledger/dashboard")).toBe("future-ledger");
  });

  it("renders users and permissions links for a governed module", async () => {
    render(<ModuleAccessNavigation routePath="/crm/accounts" presentation="sidebar" />);

    await waitFor(() => expect(usersApiMock.moduleContext).toHaveBeenCalledWith("crm"));
    const nav = await screen.findByTestId("module-access-navigation");
    expect(nav).toHaveAttribute("data-application-code", "CRM");
    expect(screen.getByRole("link", { name: "المستخدمون" })).toHaveAttribute(
      "href",
      expect.stringContaining("/management/users?"),
    );
    expect(screen.getByRole("link", { name: "الصلاحيات" })).toHaveAttribute(
      "href",
      expect.stringContaining("/management/access?"),
    );
  });

  it("supports a future registered module with no component change", async () => {
    usersApiMock.moduleContext.mockResolvedValue({
      ...context,
      applicationCode: "FUTURE_LEDGER",
      name: "Future Ledger",
      localizedName: "دفتر المستقبل",
      capabilityNamespaces: ["FUTURE_LEDGER"],
    });

    render(<ModuleAccessNavigation routePath="/future-ledger/dashboard" presentation="sidebar" />);
    await waitFor(() => expect(usersApiMock.moduleContext).toHaveBeenCalledWith("future-ledger"));
    expect(await screen.findByTestId("module-access-navigation")).toHaveAttribute(
      "data-application-code",
      "FUTURE_LEDGER",
    );
  });

  it("fails closed when route is not governed or session lacks authority", async () => {
    usersApiMock.moduleContext.mockRejectedValue(new Error("forbidden"));
    render(<ModuleAccessNavigation routePath="/unknown/page" presentation="sidebar" />);
    await waitFor(() => expect(usersApiMock.moduleContext).toHaveBeenCalledWith("unknown"));
    expect(screen.queryByTestId("module-access-navigation")).not.toBeInTheDocument();
  });
});
