// @vitest-environment jsdom
import "@testing-library/jest-dom/vitest";

import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const { TENANT_ID, USER_ID, ROLE_ID, GRANT_ID, usersApiMock, tenantAccessApiMock, authMock } = vi.hoisted(() => {
  const TENANT_ID = "11111111-1111-1111-1111-111111111111";
  const USER_ID = "22222222-2222-2222-2222-222222222222";
  const ROLE_ID = "33333333-3333-3333-3333-333333333333";
  const GRANT_ID = "44444444-4444-4444-4444-444444444444";
  return {
    TENANT_ID,
    USER_ID,
    ROLE_ID,
    GRANT_ID,
    usersApiMock: { get: vi.fn(), update: vi.fn(), transition: vi.fn() },
    tenantAccessApiMock: {
      listUserMemberships: vi.fn(),
      listUserRoleLinks: vi.fn(),
      listRoles: vi.fn(),
      grantUserRole: vi.fn(),
      revokeUserRole: vi.fn(),
    },
    authMock: {
      state: "AUTHENTICATED",
      user: { id: "actor-1", tenantId: TENANT_ID, email: "admin@example.com", displayName: "Admin", status: "ACTIVE" },
      capabilities: ["USER.READ", "USER.WRITE", "USER.DELETE", "MEMBERSHIP.READ", "ROLE.READ", "USER.GRANT_ROLE", "USER.REVOKE_ROLE"] as string[],
    },
  };
});

vi.mock("@/lib/api/users", () => ({ usersApi: usersApiMock }));
vi.mock("@/lib/api/tenant-access", () => ({ tenantAccessApi: tenantAccessApiMock }));
vi.mock("@/lib/auth/auth-provider", () => ({
  useAuth: () => ({ state: authMock.state, user: authMock.user, me: { capabilities: authMock.capabilities } }),
}));
vi.mock("@/components/shell", () => ({ ExecutiveShell: ({ children }: { children: React.ReactNode }) => <>{children}</> }));
vi.mock("next/navigation", () => ({
  useParams: () => ({ userId: USER_ID }),
  useRouter: () => ({ replace: vi.fn(), push: vi.fn() }),
}));
vi.mock("@/lib/i18n/I18nProvider", () => ({ useI18n: () => ({ locale: "ar" }) }));
vi.mock("@/lib/api/user-facing-errors", () => ({ toUserFacingMessage: () => "تعذر تحميل بيانات المستخدم" }));

import TenantUserDetailPage from "./page";

const USER = {
  id: USER_ID,
  tenantId: TENANT_ID,
  email: "salem@example.com",
  displayName: "سالم العتيبي",
  status: "ACTIVE",
  createdAt: "2026-01-01T00:00:00Z",
  updatedAt: "2026-01-01T00:00:00Z",
};

beforeEach(() => {
  usersApiMock.get.mockReset();
  usersApiMock.update.mockReset();
  usersApiMock.transition.mockReset();
  tenantAccessApiMock.listUserMemberships.mockReset();
  tenantAccessApiMock.listUserRoleLinks.mockReset();
  tenantAccessApiMock.listRoles.mockReset();
  tenantAccessApiMock.grantUserRole.mockReset();
  tenantAccessApiMock.revokeUserRole.mockReset();

  usersApiMock.get.mockResolvedValue(USER);
  usersApiMock.update.mockResolvedValue(USER);
  usersApiMock.transition.mockResolvedValue(USER);
  tenantAccessApiMock.listUserMemberships.mockResolvedValue([
    { id: "55555555-5555-5555-5555-555555555555", tenantId: TENANT_ID, organizationId: "66666666-6666-6666-6666-666666666666", userId: USER_ID, email: USER.email, displayName: USER.displayName, status: "ACTIVE", createdAt: USER.createdAt, updatedAt: USER.updatedAt },
  ]);
  tenantAccessApiMock.listUserRoleLinks.mockResolvedValue([
    { id: GRANT_ID, tenantId: TENANT_ID, userId: USER_ID, roleId: ROLE_ID, roleCode: "TENANT_ADMIN", organizationId: null, status: "ACTIVE", createdAt: USER.createdAt, updatedAt: USER.updatedAt },
  ]);
  tenantAccessApiMock.listRoles.mockResolvedValue([
    { id: ROLE_ID, tenantId: TENANT_ID, code: "TENANT_ADMIN", name: "Tenant Admin", description: null, status: "ACTIVE", createdAt: USER.createdAt, updatedAt: USER.updatedAt },
  ]);
  authMock.capabilities = ["USER.READ", "USER.WRITE", "USER.DELETE", "MEMBERSHIP.READ", "ROLE.READ", "USER.GRANT_ROLE", "USER.REVOKE_ROLE"];
});

afterEach(() => cleanup());

describe("Tenant User Detail", () => {
  it("loads identity with authenticated tenant while memberships derive tenant from JWT context", async () => {
    render(<TenantUserDetailPage />);

    expect(await screen.findByText("سالم العتيبي")).toBeInTheDocument();
    expect(usersApiMock.get).toHaveBeenCalledWith(TENANT_ID, USER_ID);
    expect(tenantAccessApiMock.listUserMemberships).toHaveBeenCalledWith(USER_ID);
    expect(tenantAccessApiMock.listUserRoleLinks).toHaveBeenCalledWith(TENANT_ID, USER_ID);
    expect(tenantAccessApiMock.listRoles).toHaveBeenCalledWith(TENANT_ID);
    expect(screen.queryByDisplayValue(TENANT_ID)).not.toBeInTheDocument();
  });

  it("updates identity only inside the authenticated tenant", async () => {
    const user = userEvent.setup();
    render(<TenantUserDetailPage />);
    await screen.findByText("سالم العتيبي");

    await user.clear(screen.getByLabelText("البريد الإلكتروني"));
    await user.type(screen.getByLabelText("البريد الإلكتروني"), "updated@example.com");
    await user.click(screen.getByRole("button", { name: "حفظ التعديلات" }));

    await waitFor(() => expect(usersApiMock.update).toHaveBeenCalledWith(TENANT_ID, USER_ID, {
      email: "updated@example.com",
      displayName: "سالم العتيبي",
    }));
  });

  it("grants and revokes canonical role links using role and grant identifiers", async () => {
    const user = userEvent.setup();
    tenantAccessApiMock.grantUserRole.mockResolvedValue({ id: GRANT_ID });
    tenantAccessApiMock.revokeUserRole.mockResolvedValue({ id: GRANT_ID, status: "REVOKED" });

    render(<TenantUserDetailPage />);
    await screen.findByRole("button", { name: "سحب الدور" });

    await user.selectOptions(screen.getByLabelText("الدور"), ROLE_ID);
    await user.click(screen.getByRole("button", { name: "إسناد الدور" }));
    await waitFor(() => expect(tenantAccessApiMock.grantUserRole).toHaveBeenCalledWith(TENANT_ID, USER_ID, ROLE_ID, undefined));

    await user.click(screen.getByRole("button", { name: "سحب الدور" }));
    await waitFor(() => expect(tenantAccessApiMock.revokeUserRole).toHaveBeenCalledWith(TENANT_ID, GRANT_ID));
  });

  it("fails closed by hiding mutations when capabilities are absent", async () => {
    authMock.capabilities = ["USER.READ", "MEMBERSHIP.READ", "ROLE.READ"];
    render(<TenantUserDetailPage />);

    expect(await screen.findByText("سالم العتيبي")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "حفظ التعديلات" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "إسناد الدور" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "سحب الدور" })).not.toBeInTheDocument();
  });
});
