// @vitest-environment jsdom
import "@testing-library/jest-dom/vitest";

import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const { authMock, usersApiMock, tenantAccessApiMock, accessApiMock } = vi.hoisted(() => ({
  authMock: {
    state: "AUTHENTICATED",
    user: {
      id: "actor-1",
      tenantId: "11111111-1111-1111-1111-111111111111",
      email: "admin@example.com",
      displayName: "Admin",
      status: "ACTIVE",
    },
    capabilities: ["USER.READ", "USER.CREATE", "USER.GRANT_ROLE", "AUTHORIZATION.OVERRIDE.MANAGE"] as string[],
  },
  usersApiMock: {
    list: vi.fn(),
    create: vi.fn(),
    moduleProvisioningContext: vi.fn(),
  },
  tenantAccessApiMock: {
    grantUserRole: vi.fn(),
  },
  accessApiMock: {
    listOverrides: vi.fn(),
    createOverride: vi.fn(),
  },
}));

vi.mock("@/lib/auth/auth-provider", () => ({
  useAuth: () => ({
    state: authMock.state,
    user: authMock.user,
    me: { capabilities: authMock.capabilities },
  }),
}));

vi.mock("@/lib/api/users", () => ({ usersApi: usersApiMock }));
vi.mock("@/lib/api/tenant-access", () => ({ tenantAccessApi: tenantAccessApiMock }));
vi.mock("@/lib/api/access-api", () => ({
  listOverrides: accessApiMock.listOverrides,
  createOverride: accessApiMock.createOverride,
}));
vi.mock("@/lib/api/user-facing-errors", () => ({
  toUserFacingMessage: () => "تعذر إنشاء المستخدم",
}));

vi.mock("@/lib/i18n/I18nProvider", () => ({
  useI18n: () => ({
    t: (key: string) => ({
      "users.title": "المستخدمون",
      "users.subtitle": "إدارة مستخدمي مساحة العمل",
      "users.search": "بحث",
      "users.statusFilter": "الحالة",
      "users.allStatuses": "الكل",
      "users.create": "إضافة مستخدم",
      "users.createTitle": "إضافة مستخدم جديد",
      "users.email": "البريد الإلكتروني",
      "users.username": "اسم المستخدم",
      "users.displayName": "الاسم المعروض",
      "users.mobileNumber": "رقم الجوال",
      "users.mobileRegion": "رمز المنطقة",
      "users.initialCredential": "كلمة المرور المؤقتة",
      "users.initialCredentialHelp": "يجب تغييرها عند أول تسجيل دخول.",
      "users.submitCreate": "إنشاء المستخدم",
      "users.cancel": "إلغاء",
      "users.close": "إغلاق",
    } as Record<string, string>)[key] ?? key,
  }),
}));

import {
  GlobalUserProvisioningLauncher,
  moduleContextFromLocation,
  moduleContextFromPathname,
} from "./GlobalUserProvisioningLauncher";

const TENANT_ID = "11111111-1111-1111-1111-111111111111";
const EXISTING_ID = "22222222-2222-2222-2222-222222222222";
const CREATED_ID = "33333333-3333-3333-3333-333333333333";

const userRecord = (id: string, email: string) => ({
  id,
  tenantId: TENANT_ID,
  email,
  username: "user",
  displayName: "User",
  mobileNumber: null,
  mobileRegion: null,
  status: "ACTIVE",
  lastLoginAt: null,
  credentialInitialized: true,
  credentialRotationRequired: false,
  createdAt: "2026-01-01T00:00:00Z",
  updatedAt: "2026-01-01T00:00:00Z",
});

beforeEach(() => {
  authMock.state = "AUTHENTICATED";
  authMock.user = {
    id: "actor-1",
    tenantId: TENANT_ID,
    email: "admin@example.com",
    displayName: "Admin",
    status: "ACTIVE",
  };
  authMock.capabilities = ["USER.READ", "USER.CREATE", "USER.GRANT_ROLE", "AUTHORIZATION.OVERRIDE.MANAGE"];
  window.history.replaceState({}, "", "/hr/employees");
  usersApiMock.list.mockReset();
  usersApiMock.create.mockReset();
  usersApiMock.moduleProvisioningContext.mockReset();
  tenantAccessApiMock.grantUserRole.mockReset();
  accessApiMock.listOverrides.mockReset();
  accessApiMock.createOverride.mockReset();
  usersApiMock.list.mockResolvedValue([]);
  usersApiMock.create.mockResolvedValue(userRecord(CREATED_ID, "new@example.com"));
  usersApiMock.moduleProvisioningContext.mockResolvedValue({
    applicationCode: "HRM",
    name: "HRM",
    localizedName: "الموارد البشرية",
    capabilityNamespaces: ["HRM", "HR"],
    declaredCapabilities: ["HRM.EMPLOYEE.VIEW"],
    supportedScopes: ["TENANT", "ORGANIZATION"],
    roles: [{
      roleId: "44444444-4444-4444-8444-444444444444",
      roleCode: "HR_SPECIALIST",
      roleName: "HR Specialist",
      capabilities: ["HRM.EMPLOYEE.VIEW"],
    }],
  });
  tenantAccessApiMock.grantUserRole.mockResolvedValue({});
  accessApiMock.listOverrides.mockResolvedValue([]);
  accessApiMock.createOverride.mockResolvedValue({});
});

afterEach(() => cleanup());

describe("GlobalUserProvisioningLauncher", () => {
  it("derives module context generically, including future module roots", () => {
    expect(moduleContextFromPathname("/hr/employees")).toBe("hr");
    expect(moduleContextFromPathname("/crm/leads")).toBe("crm");
    expect(moduleContextFromPathname("/future-module/records/1")).toBe("future-module");
    expect(moduleContextFromPathname("/")).toBe("workspace");
    expect(moduleContextFromLocation("/management/users", "?module=crm")).toBe("crm");
  });

  it("keeps module provisioning available on the module-scoped central users route", () => {
    window.history.replaceState({}, "", "/management/users?module=crm&returnTo=%2Fcrm%2Foverview");
    render(<GlobalUserProvisioningLauncher presentation="header" />);
    expect(screen.getByTestId("global-user-provisioning")).toHaveAttribute(
      "data-module-context",
      "crm",
    );
  });

  it("is automatically available inside an authenticated module with USER.CREATE", () => {
    render(<GlobalUserProvisioningLauncher />);
    const launcher = screen.getByTestId("global-user-provisioning");
    expect(launcher).toHaveAttribute("data-module-context", "hr");
    expect(screen.getByRole("button", { name: "إضافة مستخدم" })).toBeInTheDocument();
  });

  it("also appears for a newly introduced module route without module-specific registration", () => {
    window.history.replaceState({}, "", "/future-module/dashboard");
    render(<GlobalUserProvisioningLauncher />);
    expect(screen.getByTestId("global-user-provisioning")).toHaveAttribute(
      "data-module-context",
      "future-module",
    );
  });

  it("renders as a module-header action with a visible user-plus affordance", () => {
    window.history.replaceState({}, "", "/crm/overview");
    render(<GlobalUserProvisioningLauncher presentation="header" />);
    const launcher = screen.getByTestId("global-user-provisioning");
    expect(launcher).toHaveAttribute("data-presentation", "header");
    expect(launcher).toHaveAttribute("data-module-context", "crm");
    const button = screen.getByRole("button", { name: "إضافة مستخدم" });
    expect(button).toBeInTheDocument();
    expect(button.querySelector("svg")).not.toBeNull();
  });

  it("fails closed when unauthenticated or USER.CREATE is absent", () => {
    authMock.state = "ANONYMOUS";
    const { rerender } = render(<GlobalUserProvisioningLauncher />);
    expect(screen.queryByTestId("global-user-provisioning")).not.toBeInTheDocument();

    authMock.state = "AUTHENTICATED";
    authMock.capabilities = ["USER.READ"];
    rerender(<GlobalUserProvisioningLauncher />);
    expect(screen.queryByTestId("global-user-provisioning")).not.toBeInTheDocument();
  });

  it("avoids duplicating the native Users workspace create surface", () => {
    window.history.replaceState({}, "", "/management/users");
    render(<GlobalUserProvisioningLauncher />);
    expect(screen.queryByTestId("global-user-provisioning")).not.toBeInTheDocument();
  });

  it("shows every declared capability for the current module instead of only module-only roles", async () => {
    usersApiMock.moduleProvisioningContext.mockResolvedValue({
      applicationCode: "CRM",
      name: "CRM",
      localizedName: "إدارة علاقات العملاء",
      capabilityNamespaces: ["CRM"],
      declaredCapabilities: ["CRM.ACCOUNT.READ", "CRM.LEAD.WRITE", "CRM.OPPORTUNITY.READ"],
      supportedScopes: ["TENANT"],
      roles: [],
    });
    window.history.replaceState({}, "", "/crm/overview");
    const user = userEvent.setup();
    render(<GlobalUserProvisioningLauncher />);
    await user.click(screen.getByRole("button", { name: "إضافة مستخدم" }));

    expect(await screen.findByText("CRM.ACCOUNT.READ")).toBeInTheDocument();
    expect(screen.getByText("CRM.LEAD.WRITE")).toBeInTheDocument();
    expect(screen.getByText("CRM.OPPORTUNITY.READ")).toBeInTheDocument();
    expect(screen.queryByText("HRM.EMPLOYEE.VIEW")).not.toBeInTheDocument();
  });

  it("reuses an existing tenant user with the same email instead of creating a duplicate", async () => {
    const user = userEvent.setup();
    usersApiMock.list.mockResolvedValue([userRecord(EXISTING_ID, "existing@example.com")]);

    render(<GlobalUserProvisioningLauncher />);
    await user.click(screen.getByRole("button", { name: "إضافة مستخدم" }));
    await user.type(screen.getByLabelText("البريد الإلكتروني"), "Existing@Example.com");
    await user.type(screen.getByLabelText("اسم المستخدم"), "existing");
    await user.click(await screen.findByRole("checkbox"));
    await user.click(screen.getByRole("button", { name: "إنشاء المستخدم" }));

    await waitFor(() => expect(usersApiMock.list).toHaveBeenCalledWith(TENANT_ID));
    expect(usersApiMock.create).not.toHaveBeenCalled();
    expect(accessApiMock.createOverride).toHaveBeenCalledWith({
      targetUserId: EXISTING_ID,
      capabilityCode: "HRM.EMPLOYEE.VIEW",
      effect: "ALLOW",
      scopeType: "TENANT_ALL",
      scopeReference: null,
      reason: "Module provisioning: hr",
      validFrom: null,
      validUntil: null,
    });
    expect(tenantAccessApiMock.grantUserRole).not.toHaveBeenCalled();
    expect(window.location.pathname).toBe(`/management/users/${EXISTING_ID}`);
    expect(window.location.search).toBe("?returnTo=%2Fhr%2Femployees");
  });

  it("creates through Users Core only and opens the canonical user record", async () => {
    const user = userEvent.setup();

    render(<GlobalUserProvisioningLauncher />);
    await user.click(screen.getByRole("button", { name: "إضافة مستخدم" }));
    await user.type(screen.getByLabelText("البريد الإلكتروني"), "new@example.com");
    await user.type(screen.getByLabelText("اسم المستخدم"), "new.user");
    await user.type(screen.getByLabelText("الاسم المعروض"), "New User");
    await user.click(await screen.findByRole("checkbox"));
    await user.click(screen.getByRole("button", { name: "إنشاء المستخدم" }));

    await waitFor(() =>
      expect(usersApiMock.create).toHaveBeenCalledWith(TENANT_ID, {
        email: "new@example.com",
        username: "new.user",
        displayName: "New User",
        mobileNumber: "",
        mobileRegion: "",
        initialCredential: "12345678",
      }),
    );
    expect(accessApiMock.createOverride).toHaveBeenCalledWith({
      targetUserId: CREATED_ID,
      capabilityCode: "HRM.EMPLOYEE.VIEW",
      effect: "ALLOW",
      scopeType: "TENANT_ALL",
      scopeReference: null,
      reason: "Module provisioning: hr",
      validFrom: null,
      validUntil: null,
    });
    expect(tenantAccessApiMock.grantUserRole).not.toHaveBeenCalled();
    expect(window.location.pathname).toBe(`/management/users/${CREATED_ID}`);
    expect(window.location.search).toBe("?returnTo=%2Fhr%2Femployees");
  });
});
