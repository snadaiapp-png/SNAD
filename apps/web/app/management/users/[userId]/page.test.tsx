// @vitest-environment jsdom
import "@testing-library/jest-dom/vitest";

import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const { TENANT_ID, USER_ID, ROLE_ID, GRANT_ID, usersApiMock, tenantAccessApiMock, credentialApiMock, authMock, translate } = vi.hoisted(() => {
  const TENANT_ID = "11111111-1111-1111-1111-111111111111";
  const USER_ID = "22222222-2222-2222-2222-222222222222";
  const ROLE_ID = "33333333-3333-3333-3333-333333333333";
  const GRANT_ID = "44444444-4444-4444-4444-444444444444";
  const messages: Record<string, string> = {
    "users.email": "البريد الإلكتروني",
    "users.displayName": "الاسم المعروض",
    "management.users.detail.title": "تفاصيل المستخدم",
    "management.users.detail.save": "حفظ التعديلات",
    "management.users.detail.memberships": "عضويات المؤسسات",
    "management.users.detail.roles": "الأدوار المسندة",
    "management.users.detail.role": "الدور",
    "management.users.detail.grant": "إسناد الدور",
    "management.users.detail.revoke": "سحب الدور",
    "management.users.detail.noMemberships": "لا توجد عضويات",
    "management.users.detail.noRoles": "لا توجد أدوار مسندة",
    "management.users.detail.loading": "جارٍ تحميل بيانات المستخدم",
    "management.users.detail.error": "تعذر تحميل بيانات المستخدم",
    "users.username": "اسم المستخدم",
    "management.users.credentials.title": "بيانات الدخول",
    "management.users.credentials.reset": "إرسال رابط تعيين كلمة المرور",
    "management.users.credentials.resetSuccess": "تم إرسال رابط تعيين كلمة المرور",
    "management.users.credentials.help": "لا يتم عرض أو تخزين كلمة المرور في الواجهة",
  };
  return {
    TENANT_ID, USER_ID, ROLE_ID, GRANT_ID,
    translate: (key: string) => messages[key] ?? key,
    usersApiMock: { get: vi.fn(), update: vi.fn(), transition: vi.fn() },
    credentialApiMock: { adminResetPassword: vi.fn() },
    tenantAccessApiMock: { listUserMemberships: vi.fn(), listUserRoleLinks: vi.fn(), listRoles: vi.fn(), grantUserRole: vi.fn(), revokeUserRole: vi.fn() },
    authMock: {
      state: "AUTHENTICATED",
      user: { id: "actor-1", tenantId: TENANT_ID, email: "admin@example.com", displayName: "Admin", status: "ACTIVE" },
      capabilities: ["USER.READ", "USER.WRITE", "USER.DELETE", "MEMBERSHIP.READ", "ROLE.READ", "USER.GRANT_ROLE", "USER.REVOKE_ROLE"] as string[],
    },
  };
});

vi.mock("@/lib/api/users", () => ({ usersApi: usersApiMock }));
vi.mock("@/lib/api/auth", () => ({ createTenantAuthApi: () => credentialApiMock }));
vi.mock("@/lib/api/tenant-access", () => ({ tenantAccessApi: tenantAccessApiMock }));
vi.mock("@/lib/auth/auth-provider", () => ({ useAuth: () => ({ state: authMock.state, user: authMock.user, me: { capabilities: authMock.capabilities } }) }));
vi.mock("@/components/shell", () => ({ ExecutiveShell: ({ children }: { children: React.ReactNode }) => <>{children}</> }));
vi.mock("next/navigation", () => ({ useParams: () => ({ userId: USER_ID }), useRouter: () => ({ replace: vi.fn(), push: vi.fn() }) }));
vi.mock("@/lib/i18n/I18nProvider", () => ({ useI18n: () => ({ t: translate }) }));
vi.mock("@/lib/api/user-facing-errors", () => ({ toUserFacingMessage: () => "تعذر تحميل بيانات المستخدم" }));

import TenantUserDetailPage from "./page";

const USER = { id: USER_ID, tenantId: TENANT_ID, email: "salem@example.com", username: "salem", displayName: "سالم العتيبي", status: "ACTIVE", createdAt: "2026-01-01T00:00:00Z", updatedAt: "2026-01-01T00:00:00Z" };

beforeEach(() => {
  usersApiMock.get.mockReset(); usersApiMock.update.mockReset(); usersApiMock.transition.mockReset(); credentialApiMock.adminResetPassword.mockReset();
  tenantAccessApiMock.listUserMemberships.mockReset(); tenantAccessApiMock.listUserRoleLinks.mockReset(); tenantAccessApiMock.listRoles.mockReset(); tenantAccessApiMock.grantUserRole.mockReset(); tenantAccessApiMock.revokeUserRole.mockReset();
  usersApiMock.get.mockResolvedValue(USER); usersApiMock.update.mockResolvedValue(USER); usersApiMock.transition.mockResolvedValue(USER); credentialApiMock.adminResetPassword.mockResolvedValue({ message: "تم إرسال رابط أحادي الاستخدام لإعداد كلمة مرور جديدة." });
  tenantAccessApiMock.listUserMemberships.mockResolvedValue([{ id: "55555555-5555-5555-5555-555555555555", tenantId: TENANT_ID, organizationId: "66666666-6666-6666-6666-666666666666", userId: USER_ID, email: USER.email, displayName: USER.displayName, status: "ACTIVE", createdAt: USER.createdAt, updatedAt: USER.updatedAt }]);
  tenantAccessApiMock.listUserRoleLinks.mockResolvedValue([{ id: GRANT_ID, tenantId: TENANT_ID, userId: USER_ID, roleId: ROLE_ID, roleCode: "TENANT_ADMIN", organizationId: null, status: "ACTIVE", createdAt: USER.createdAt, updatedAt: USER.updatedAt }]);
  tenantAccessApiMock.listRoles.mockResolvedValue([{ id: ROLE_ID, tenantId: TENANT_ID, code: "TENANT_ADMIN", name: "Tenant Admin", description: null, status: "ACTIVE", createdAt: USER.createdAt, updatedAt: USER.updatedAt }]);
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
    const user = userEvent.setup(); render(<TenantUserDetailPage />); await screen.findByText("سالم العتيبي");
    await user.clear(screen.getByLabelText("البريد الإلكتروني")); await user.type(screen.getByLabelText("البريد الإلكتروني"), "updated@example.com"); await user.click(screen.getByRole("button", { name: "حفظ التعديلات" }));
    await waitFor(() => expect(usersApiMock.update).toHaveBeenCalledWith(TENANT_ID, USER_ID, { email: "updated@example.com", displayName: "سالم العتيبي" }));
  });

  it("renders username and sends a governed set-password link without exposing credentials", async () => {
    const user = userEvent.setup();
    render(<TenantUserDetailPage />);
    expect(await screen.findByText("سالم العتيبي")).toBeInTheDocument();
    expect(screen.getByLabelText("اسم المستخدم")).toHaveValue("salem");
    expect(screen.queryByLabelText(/كلمة المرور/i)).not.toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "إرسال رابط تعيين كلمة المرور" }));
    await waitFor(() => expect(credentialApiMock.adminResetPassword).toHaveBeenCalledWith(USER_ID, { locale: "ar" }));
    expect(screen.getByRole("status")).toHaveTextContent("تم إرسال رابط تعيين كلمة المرور");
  });

  it("hides credential mutation when USER.WRITE is absent", async () => {
    authMock.capabilities = ["USER.READ"];
    render(<TenantUserDetailPage />);
    expect(await screen.findByText("سالم العتيبي")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "إرسال رابط تعيين كلمة المرور" })).not.toBeInTheDocument();
  });

  it("grants and revokes canonical role links using role and grant identifiers", async () => {
    const user = userEvent.setup(); tenantAccessApiMock.grantUserRole.mockResolvedValue({ id: GRANT_ID }); tenantAccessApiMock.revokeUserRole.mockResolvedValue({ id: GRANT_ID, status: "REVOKED" });
    render(<TenantUserDetailPage />); await screen.findByRole("button", { name: "سحب الدور" });
    await user.selectOptions(screen.getByLabelText("الدور"), ROLE_ID); await user.click(screen.getByRole("button", { name: "إسناد الدور" }));
    await waitFor(() => expect(tenantAccessApiMock.grantUserRole).toHaveBeenCalledWith(TENANT_ID, USER_ID, ROLE_ID, undefined));
    await user.click(screen.getByRole("button", { name: "سحب الدور" })); await waitFor(() => expect(tenantAccessApiMock.revokeUserRole).toHaveBeenCalledWith(TENANT_ID, GRANT_ID));
  });

  it("fails closed by hiding mutations when capabilities are absent", async () => {
    authMock.capabilities = ["USER.READ", "MEMBERSHIP.READ", "ROLE.READ"]; render(<TenantUserDetailPage />);
    expect(await screen.findByText("سالم العتيبي")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "حفظ التعديلات" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "إسناد الدور" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "سحب الدور" })).not.toBeInTheDocument();
  });
});
