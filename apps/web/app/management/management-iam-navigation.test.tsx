// @vitest-environment jsdom
import "@testing-library/jest-dom/vitest";

import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const { authMock } = vi.hoisted(() => ({
  authMock: {
    capabilities: ["USER.READ", "ROLE.READ", "CAPABILITY.READ"] as string[],
  },
}));

vi.mock("@/lib/auth/auth-provider", () => ({
  useAuth: () => ({
    state: "AUTHENTICATED",
    me: { capabilities: authMock.capabilities },
  }),
}));

vi.mock("@/lib/i18n/I18nProvider", () => ({
  useI18n: () => ({
    t: (key: string) => ({
      "management.iam.users": "المستخدمون",
      "management.iam.access": "إدارة الوصول",
    } as Record<string, string>)[key] ?? key,
  }),
}));

vi.mock("next/link", () => ({
  default: ({ href, children }: { href: string; children: React.ReactNode }) => <a href={href}>{children}</a>,
}));

import { ManagementIamNav } from "./_client/management-iam-nav";

beforeEach(() => {
  authMock.capabilities = ["USER.READ", "ROLE.READ", "CAPABILITY.READ"];
});

afterEach(() => cleanup());

describe("Management IAM navigation", () => {
  it("discovers tenant users and access management only from authoritative read capabilities", () => {
    render(<ManagementIamNav />);

    expect(screen.getByRole("link", { name: "المستخدمون" })).toHaveAttribute("href", "/management/users");
    expect(screen.getByRole("link", { name: "إدارة الوصول" })).toHaveAttribute("href", "/management/access");
  });

  it("does not expose the access entry when either role or capability read permission is missing", () => {
    authMock.capabilities = ["USER.READ", "ROLE.READ"];
    render(<ManagementIamNav />);

    expect(screen.getByRole("link", { name: "المستخدمون" })).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "إدارة الوصول" })).not.toBeInTheDocument();
  });

  it("renders no IAM navigation for an actor without IAM read permissions", () => {
    authMock.capabilities = [];
    render(<ManagementIamNav />);

    expect(screen.queryByRole("link", { name: "المستخدمون" })).not.toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "إدارة الوصول" })).not.toBeInTheDocument();
  });
});
