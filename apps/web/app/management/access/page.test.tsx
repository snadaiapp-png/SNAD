// @vitest-environment jsdom
import "@testing-library/jest-dom/vitest";

import { cleanup, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const { TENANT_ID, ROLE_ID, CAPABILITY_ID, accessApiMock, authMock } = vi.hoisted(() => {
  const TENANT_ID = "11111111-1111-1111-1111-111111111111";
  const ROLE_ID = "22222222-2222-2222-2222-222222222222";
  const CAPABILITY_ID = "33333333-3333-3333-3333-333333333333";
  return {
    TENANT_ID,
    ROLE_ID,
    CAPABILITY_ID,
    accessApiMock: {
      listRoles: vi.fn(),
      createRole: vi.fn(),
      updateRole: vi.fn(),
      transitionRole: vi.fn(),
      listCapabilities: vi.fn(),
      createCapability: vi.fn(),
      updateCapability: vi.fn(),
      transitionCapability: vi.fn(),
      listRoleCapabilities: vi.fn(),
      attachRoleCapability: vi.fn(),
      detachRoleCapability: vi.fn(),
    },
    authMock: {
      state: "AUTHENTICATED",
      user: { id: "actor-1", tenantId: TENANT_ID, email: "admin@example.com", displayName: "Admin", status: "ACTIVE" },
      capabilities: ["ROLE.READ", "ROLE.MANAGE", "CAPABILITY.READ", "CAPABILITY.MANAGE"] as string[],
    },
  };
});

vi.mock("@/lib/api/tenant-access", () => ({ tenantAccessApi: accessApiMock }));
vi.mock("@/lib/auth/auth-provider", () => ({
  useAuth: () => ({
    state: authMock.state,
    user: authMock.user,
    me: { capabilities: authMock.capabilities },
  }),
}));
vi.mock("@/components/shell", () => ({ ExecutiveShell: ({ children }: { children: React.ReactNode }) => <>{children}</> }));
vi.mock("next/navigation", () => ({
  useRouter: () => ({ replace: vi.fn(), push: vi.fn() }),
  usePathname: () => "/management/access",
}));
vi.mock("@/lib/i18n/I18nProvider", () => ({
  useI18n: () => ({
    locale: "ar",
    t: (key: string) => ({
      "management.access.title": "إدارة الوصول",
      "management.access.subtitle": "إدارة الأدوار والصلاحيات للمستأجر الحالي",
      "management.access.roles": "الأدوار",
      "management.access.capabilities": "الصلاحيات",
      "management.access.createRole": "إنشاء دور",
      "management.access.roleCode": "رمز الدور",
      "management.access.roleName": "اسم الدور",
      "management.access.create": "إنشاء",
      "management.access.archiveRole": "أرشفة الدور",
      "management.access.activateRole": "تفعيل الدور",
      "management.access.deactivateRole": "تعطيل الدور",
      "management.access.attachCapability": "إرفاق الصلاحية",
      "management.access.detachCapability": "فصل الصلاحية",
      "management.access.createCapability": "إنشاء صلاحية",
      "management.access.capabilityCode": "رمز الصلاحية",
      "management.access.capabilityName": "اسم الصلاحية",
      "management.access.forbidden": "لا تملك صلاحية عرض إدارة الوصول",
      "management.access.loading": "جارٍ تحميل إدارة الوصول",
      "management.access.emptyRoles": "لا توجد أدوار",
      "management.access.emptyCapabilities": "لا توجد صلاحيات",
    } as Record<string, string>)[key] ?? key,
  }),
}));
vi.mock("@/lib/api/user-facing-errors", () => ({
  toUserFacingMessage: (error: { status?: number }) => ({
    403: "غير مسموح بتنفيذ هذا الإجراء",
    404: "العنصر المطلوب غير موجود",
    409: "يتعارض الإجراء مع الحالة الحالية",
  } as Record<number, string>)[error?.status ?? 0] ?? "تعذر إكمال العملية",
}));

import TenantAccessPage from "./page";

const ROLES = [{
  id: ROLE_ID,
  tenantId: TENANT_ID,
  code: "TENANT_ADMIN",
  name: "Tenant Admin",
  description: "Tenant administrators",
  status: "ACTIVE",
  createdAt: "2026-01-01T00:00:00Z",
  updatedAt: "2026-01-01T00:00:00Z",
}];

const CAPABILITIES = [{
  id: CAPABILITY_ID,
  code: "USER.READ",
  name: "Read users",
  description: "Read tenant users",
  status: "ACTIVE",
  createdAt: "2026-01-01T00:00:00Z",
  updatedAt: "2026-01-01T00:00:00Z",
}];

beforeEach(() => {
  Object.values(accessApiMock).forEach((mock) => mock.mockReset());
  accessApiMock.listRoles.mockResolvedValue(ROLES);
  accessApiMock.listCapabilities.mockResolvedValue(CAPABILITIES);
  accessApiMock.listRoleCapabilities.mockResolvedValue([]);
  authMock.state = "AUTHENTICATED";
  authMock.user = { id: "actor-1", tenantId: TENANT_ID, email: "admin@example.com", displayName: "Admin", status: "ACTIVE" };
  authMock.capabilities = ["ROLE.READ", "ROLE.MANAGE", "CAPABILITY.READ", "CAPABILITY.MANAGE"];
  vi.spyOn(window, "confirm").mockReturnValue(true);
});

afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
});

describe("Tenant Roles and Capabilities", () => {
  it("loads access data only for the authenticated tenant and exposes no tenant selector", async () => {
    render(<TenantAccessPage />);

    expect(await screen.findByRole("heading", { name: "إدارة الوصول" })).toBeInTheDocument();
    expect(accessApiMock.listRoles).toHaveBeenCalledWith(TENANT_ID);
    expect(accessApiMock.listCapabilities).toHaveBeenCalledWith();
    expect(screen.getByText("Tenant Admin")).toBeInTheDocument();
    expect(screen.getByText("USER.READ")).toBeInTheDocument();
    expect(screen.queryByLabelText(/tenant|المستأجر|معرف المستأجر/i, { selector: "input, select, textarea" })).not.toBeInTheDocument();
    expect(screen.queryByDisplayValue(TENANT_ID)).not.toBeInTheDocument();
  });

  it("fails closed for missing read capabilities and keeps manage controls hidden for read-only users", async () => {
    authMock.capabilities = [];
    render(<TenantAccessPage />);
    expect(await screen.findByRole("alert")).toHaveTextContent("لا تملك صلاحية عرض إدارة الوصول");
    expect(accessApiMock.listRoles).not.toHaveBeenCalled();
    expect(accessApiMock.listCapabilities).not.toHaveBeenCalled();

    cleanup();
    authMock.capabilities = ["ROLE.READ", "CAPABILITY.READ"];
    render(<TenantAccessPage />);
    expect(await screen.findByText("Tenant Admin")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "إنشاء دور" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "أرشفة الدور" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "إنشاء صلاحية" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "إرفاق الصلاحية" })).not.toBeInTheDocument();
  });

  it("creates roles in the authenticated tenant and requires confirmation before archive", async () => {
    const user = userEvent.setup();
    accessApiMock.createRole.mockResolvedValue({ ...ROLES[0], id: "44444444-4444-4444-4444-444444444444", code: "SUPPORT", name: "Support" });
    accessApiMock.transitionRole.mockResolvedValue({ ...ROLES[0], status: "ARCHIVED" });

    render(<TenantAccessPage />);
    await screen.findByText("Tenant Admin");
    await user.click(screen.getByRole("button", { name: "إنشاء دور" }));
    await user.type(screen.getByLabelText("رمز الدور"), "SUPPORT");
    await user.type(screen.getByLabelText("اسم الدور"), "Support");
    await user.click(screen.getByRole("button", { name: "إنشاء" }));

    await waitFor(() => expect(accessApiMock.createRole).toHaveBeenCalledWith(TENANT_ID, {
      code: "SUPPORT",
      name: "Support",
      description: null,
    }));

    const roleRow = within(screen.getByText("Tenant Admin").closest("tr") as HTMLElement);
    await user.click(roleRow.getByRole("button", { name: "أرشفة الدور" }));
    expect(window.confirm).toHaveBeenCalled();
    await waitFor(() => expect(accessApiMock.transitionRole).toHaveBeenCalledWith(TENANT_ID, ROLE_ID, "archive"));
  });

  it("attaches and detaches capabilities through the canonical role-access API", async () => {
    const user = userEvent.setup();
    accessApiMock.attachRoleCapability.mockResolvedValue({
      id: "55555555-5555-5555-5555-555555555555",
      tenantId: TENANT_ID,
      roleId: ROLE_ID,
      capabilityId: CAPABILITY_ID,
      capabilityCode: "USER.READ",
      createdAt: "2026-01-01T00:00:00Z",
    });
    accessApiMock.listRoleCapabilities
      .mockResolvedValueOnce([])
      .mockResolvedValueOnce([{ id: "grant-1", tenantId: TENANT_ID, roleId: ROLE_ID, capabilityId: CAPABILITY_ID, capabilityCode: "USER.READ", createdAt: "2026-01-01T00:00:00Z" }]);

    render(<TenantAccessPage />);
    const roleRow = within((await screen.findByText("Tenant Admin")).closest("tr") as HTMLElement);
    await user.click(roleRow.getByRole("button", { name: /Tenant Admin|إدارة صلاحيات الدور/i }));

    await user.click(await screen.findByRole("button", { name: "إرفاق الصلاحية" }));
    await waitFor(() => expect(accessApiMock.attachRoleCapability).toHaveBeenCalledWith(TENANT_ID, ROLE_ID, CAPABILITY_ID));

    await user.click(await screen.findByRole("button", { name: "فصل الصلاحية" }));
    expect(window.confirm).toHaveBeenCalled();
    await waitFor(() => expect(accessApiMock.detachRoleCapability).toHaveBeenCalledWith(TENANT_ID, ROLE_ID, CAPABILITY_ID));
  });

  it.each([
    [403, "غير مسموح بتنفيذ هذا الإجراء"],
    [404, "العنصر المطلوب غير موجود"],
    [409, "يتعارض الإجراء مع الحالة الحالية"],
  ])("surfaces stable %s errors without raw backend details", async (status, message) => {
    accessApiMock.listRoles.mockRejectedValue({ status, message: "raw backend internals" });
    render(<TenantAccessPage />);
    expect(await screen.findByRole("alert")).toHaveTextContent(message);
    expect(screen.queryByText(/raw backend internals/i)).not.toBeInTheDocument();
  });
});
