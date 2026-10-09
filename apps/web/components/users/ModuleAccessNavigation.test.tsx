// @vitest-environment jsdom
import "@testing-library/jest-dom/vitest";

import { cleanup, render, screen, waitFor } from "@testing-library/react";
import type React from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const { usersApiMock, authState } = vi.hoisted(() => ({
  usersApiMock: {
    moduleContext: vi.fn(),
  },
  authState: {
    current: "AUTHENTICATED",
    capabilities: ["USER.READ", "USER.CREATE", "USER.GRANT_ROLE"] as string[],
  },
}));

vi.mock("@/lib/api/users", () => ({ usersApi: usersApiMock }));
vi.mock("@/lib/auth/auth-provider", () => ({
  useAuth: () => ({
    state: authState.current,
    me: { capabilities: authState.capabilities },
  }),
}));
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
  authState.current = "AUTHENTICATED";
  authState.capabilities = ["USER.READ", "USER.CREATE", "USER.GRANT_ROLE"];
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

  it.each(["INITIALIZING", "CHECKING_SESSION", "REFRESHING", "REFRESHING_SESSION", "ANONYMOUS"])(
    "does not issue governed IAM discovery while auth state is %s",
    async (state) => {
      authState.current = state;
      render(<ModuleAccessNavigation routePath="/crm/accounts" presentation="sidebar" />);

      await Promise.resolve();
      expect(usersApiMock.moduleContext).not.toHaveBeenCalled();
      expect(screen.queryByTestId("module-access-navigation")).not.toBeInTheDocument();
    },
  );

  it("issues exactly one discovery after the session becomes authenticated", async () => {
    authState.current = "CHECKING_SESSION";
    const { rerender } = render(
      <ModuleAccessNavigation routePath="/crm/accounts" presentation="sidebar" />,
    );

    await Promise.resolve();
    expect(usersApiMock.moduleContext).not.toHaveBeenCalled();

    authState.current = "AUTHENTICATED";
    rerender(<ModuleAccessNavigation routePath="/crm/accounts" presentation="sidebar" />);

    await waitFor(() => expect(usersApiMock.moduleContext).toHaveBeenCalledTimes(1));
    expect(usersApiMock.moduleContext).toHaveBeenCalledWith("crm");
    expect(await screen.findByTestId("module-access-navigation")).toBeInTheDocument();
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

  it("keeps shared-shell IAM navigation visible when registry discovery degrades", async () => {
    usersApiMock.moduleContext.mockRejectedValue(new Error("forbidden"));
    render(<ModuleAccessNavigation routePath="/crm/overview" presentation="sidebar" />);
    await waitFor(() => expect(usersApiMock.moduleContext).toHaveBeenCalledWith("crm"));
    const nav = screen.getByTestId("module-access-navigation");
    expect(nav).toHaveAttribute("data-registry-status", "degraded");
    expect(screen.getByRole("link", { name: "المستخدمون" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "الصلاحيات" })).toBeInTheDocument();
  });

  it("still hides IAM navigation when the authenticated subject has no IAM authority", async () => {
    authState.capabilities = [];
    render(<ModuleAccessNavigation routePath="/crm/overview" presentation="sidebar" />);
    expect(screen.queryByTestId("module-access-navigation")).not.toBeInTheDocument();
  });
});
