// @vitest-environment jsdom
import "@testing-library/jest-dom/vitest";

import { cleanup, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const { TENANT_ID, usersApiMock, authMock } = vi.hoisted(() => {
  const TENANT_ID = "11111111-1111-1111-1111-111111111111";
  return {
    TENANT_ID,
    usersApiMock: {
      list: vi.fn(),
      create: vi.fn(),
      transition: vi.fn(),
      moduleContext: vi.fn(),
      listModuleAccessUsers: vi.fn(),
    },
    authMock: {
      state: "AUTHENTICATED",
      user: { id: "actor-1", tenantId: TENANT_ID, email: "admin@example.com", displayName: "Admin", status: "ACTIVE" },
      capabilities: ["USER.READ", "USER.CREATE", "USER.WRITE", "USER.DELETE"] as string[],
    },
  };
});

vi.mock("@/lib/api/users", () => ({ usersApi: usersApiMock }));
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
  usePathname: () => "/management/users",
}));
vi.mock("@/lib/i18n/I18nProvider", () => ({
  useI18n: () => ({
    locale: "ar",
    t: (key: string) => ({
      "users.title": "المستخدمون",
      "users.subtitle": "إدارة مستخدمي مساحة العمل",
      "users.search": "بحث في المستخدمين",
      "users.statusFilter": "حالة المستخدم",
      "users.allStatuses": "كل الحالات",
      "users.create": "إضافة مستخدم",
      "users.createTitle": "إضافة مستخدم جديد",
      "users.email": "البريد الإلكتروني",
      "users.username": "اسم المستخدم",
      "users.displayName": "الاسم المعروض",
      "users.mobileNumber": "رقم الجوال",
      "users.mobileRegion": "رمز المنطقة",
      "users.initialCredential": "كلمة المرور المؤقتة",
      "users.initialCredentialHelp": "يستطيع المستخدم تسجيل الدخول بها مرة أولى ثم يجب تغييرها قبل استخدام المنصة.",
      "users.submitCreate": "إنشاء المستخدم",
      "users.empty": "لا يوجد مستخدمون بعد",
      "users.noMatches": "لا توجد نتائج مطابقة",
      "users.loading": "جارٍ تحميل المستخدمين",
      "users.forbidden": "لا تملك صلاحية عرض المستخدمين",
      "users.error": "تعذر تحميل المستخدمين",
      "users.open": "فتح التفاصيل",
      "users.activate": "تفعيل",
      "users.deactivate": "تعطيل",
      "users.suspend": "إيقاف مؤقت",
      "users.archive": "أرشفة",
      "users.status.ACTIVE": "نشط",
      "users.status.INACTIVE": "غير نشط",
      "users.status.INVITED": "مدعو",
      "users.status.SUSPENDED": "موقوف",
      "users.status.ARCHIVED": "مؤرشف",
    } as Record<string, string>)[key] ?? key,
  }),
}));
vi.mock("@/lib/api/user-facing-errors", () => ({
  toUserFacingMessage: () => "تعذر تحميل المستخدمين",
}));

import TenantUsersPage from "./page";

const USERS = [
  {
    id: "22222222-2222-2222-2222-222222222222",
    tenantId: TENANT_ID,
    email: "salem@example.com",
    displayName: "سالم العتيبي",
    status: "ACTIVE",
    createdAt: "2026-01-01T00:00:00Z",
    updatedAt: "2026-01-01T00:00:00Z",
  },
  {
    id: "33333333-3333-3333-3333-333333333333",
    tenantId: TENANT_ID,
    email: "noura@example.com",
    displayName: "نورة القحطاني",
    status: "INVITED",
    createdAt: "2026-01-02T00:00:00Z",
    updatedAt: "2026-01-02T00:00:00Z",
  },
];

beforeEach(() => {
  usersApiMock.list.mockReset();
  usersApiMock.create.mockReset();
  usersApiMock.transition.mockReset();
  usersApiMock.moduleContext.mockReset();
  usersApiMock.listModuleAccessUsers.mockReset();
  usersApiMock.list.mockResolvedValue(USERS);
  usersApiMock.moduleContext.mockResolvedValue({
    applicationCode: "CRM",
    name: "CRM",
    localizedName: "إدارة علاقات العملاء",
    capabilityNamespaces: ["CRM"],
    declaredCapabilities: ["CRM.ACCOUNT.READ"],
    supportedScopes: ["TENANT"],
    roles: [],
  });
  usersApiMock.listModuleAccessUsers.mockResolvedValue([
    {
      userId: USERS[0].id,
      email: USERS[0].email,
      username: null,
      displayName: USERS[0].displayName,
      status: "ACTIVE",
      effectiveAccess: true,
      assignedRoles: ["CRM_AGENT"],
      effectiveCapabilities: ["CRM.ACCOUNT.READ"],
    },
    {
      userId: USERS[1].id,
      email: USERS[1].email,
      username: null,
      displayName: USERS[1].displayName,
      status: "INVITED",
      effectiveAccess: false,
      assignedRoles: [],
      effectiveCapabilities: [],
    },
  ]);
  window.history.replaceState({}, "", "/management/users");
  authMock.state = "AUTHENTICATED";
  authMock.user = { id: "actor-1", tenantId: TENANT_ID, email: "admin@example.com", displayName: "Admin", status: "ACTIVE" };
  authMock.capabilities = ["USER.READ", "USER.CREATE", "USER.WRITE", "USER.DELETE"];
});

afterEach(() => cleanup());

describe("Tenant User Directory", () => {
  it("scopes the directory to users with effective access to the selected module", async () => {
    window.history.replaceState({}, "", "/management/users?module=crm&returnTo=%2Fcrm%2Foverview");
    render(<TenantUsersPage />);

    expect(await screen.findByRole("heading", { name: "المستخدمون — إدارة علاقات العملاء" })).toBeInTheDocument();
    expect(usersApiMock.listModuleAccessUsers).toHaveBeenCalledWith("crm");
    expect(screen.getByText("سالم العتيبي")).toBeInTheDocument();
    expect(screen.queryByText("نورة القحطاني")).not.toBeInTheDocument();
    expect(screen.getByText("CRM_AGENT")).toBeInTheDocument();
  });

  it("loads users only for the authenticated tenant and exposes no tenant UUID field", async () => {
    render(<TenantUsersPage />);

    expect(await screen.findByRole("heading", { name: "المستخدمون" })).toBeInTheDocument();
    expect(usersApiMock.list).toHaveBeenCalledWith(TENANT_ID);
    expect(screen.getByText("سالم العتيبي")).toBeInTheDocument();
    expect(screen.getByText("نورة القحطاني")).toBeInTheDocument();
    expect(screen.queryByLabelText(/tenant|المستأجر|معرف المستأجر/i)).not.toBeInTheDocument();
    expect(screen.queryByDisplayValue(TENANT_ID)).not.toBeInTheDocument();
  });

  it("renders loading and then the empty state", async () => {
    let resolveList!: (value: unknown[]) => void;
    usersApiMock.list.mockImplementation(() => new Promise((resolve) => { resolveList = resolve; }));

    render(<TenantUsersPage />);
    expect(screen.getByRole("status")).toHaveTextContent("جارٍ تحميل المستخدمين");

    resolveList([]);
    expect(await screen.findByText("لا يوجد مستخدمون بعد")).toBeInTheDocument();
  });

  it("fails closed without USER.READ and does not call the users API", async () => {
    authMock.capabilities = ["USER.CREATE"];

    render(<TenantUsersPage />);

    expect(await screen.findByRole("alert")).toHaveTextContent("لا تملك صلاحية عرض المستخدمين");
    expect(usersApiMock.list).not.toHaveBeenCalled();
  });

  it("renders a stable product error when the list request fails", async () => {
    usersApiMock.list.mockRejectedValue(new Error("raw backend details must not render"));

    render(<TenantUsersPage />);

    expect(await screen.findByRole("alert")).toHaveTextContent("تعذر تحميل المستخدمين");
    expect(screen.queryByText(/raw backend details/i)).not.toBeInTheDocument();
  });

  it("filters the loaded list by name, email, and status", async () => {
    const user = userEvent.setup();
    render(<TenantUsersPage />);
    await screen.findByText("سالم العتيبي");

    const search = screen.getByRole("searchbox", { name: "بحث في المستخدمين" });
    await user.type(search, "noura@");
    expect(screen.getByText("نورة القحطاني")).toBeInTheDocument();
    expect(screen.queryByText("سالم العتيبي")).not.toBeInTheDocument();

    await user.clear(search);
    await user.selectOptions(screen.getByRole("combobox", { name: "حالة المستخدم" }), "ACTIVE");
    expect(screen.getByText("سالم العتيبي")).toBeInTheDocument();
    expect(screen.queryByText("نورة القحطاني")).not.toBeInTheDocument();
  });

  it("creates a user in the authenticated tenant and refreshes the directory", async () => {
    const user = userEvent.setup();
    usersApiMock.create.mockResolvedValue({ ...USERS[1], id: "44444444-4444-4444-4444-444444444444" });
    usersApiMock.list
      .mockResolvedValueOnce(USERS)
      .mockResolvedValueOnce([...USERS, { ...USERS[1], id: "44444444-4444-4444-4444-444444444444", email: "new@example.com", displayName: "مستخدم جديد" }]);

    render(<TenantUsersPage />);
    await screen.findByText("سالم العتيبي");
    await user.click(screen.getByRole("button", { name: "إضافة مستخدم" }));

    expect(screen.queryByLabelText(/tenant|المستأجر|معرف المستأجر/i)).not.toBeInTheDocument();
    await user.type(screen.getByLabelText("البريد الإلكتروني"), "new@example.com");
    await user.type(screen.getByLabelText("اسم المستخدم"), "new.user");
    await user.type(screen.getByLabelText("الاسم المعروض"), "مستخدم جديد");
    expect(screen.getByLabelText("كلمة المرور المؤقتة")).toHaveValue("12345678");
    await user.click(screen.getByRole("button", { name: "إنشاء المستخدم" }));

    await waitFor(() => expect(usersApiMock.create).toHaveBeenCalledWith(TENANT_ID, {
      email: "new@example.com",
      username: "new.user",
      displayName: "مستخدم جديد",
      mobileNumber: null,
      mobileRegion: null,
      initialCredential: "12345678",
    }));
    await waitFor(() => expect(usersApiMock.list).toHaveBeenCalledTimes(2));
  });

  it("keeps entered values visible when user creation fails", async () => {
    const user = userEvent.setup();
    usersApiMock.create.mockRejectedValue(new Error("backend failure"));

    render(<TenantUsersPage />);
    await screen.findByText("سالم العتيبي");
    await user.click(screen.getByRole("button", { name: "إضافة مستخدم" }));
    await user.type(screen.getByLabelText("البريد الإلكتروني"), "failed@example.com");
    await user.type(screen.getByLabelText("اسم المستخدم"), "failed.user");
    await user.type(screen.getByLabelText("رقم الجوال"), "0551234567");
    await user.click(screen.getByRole("button", { name: "إنشاء المستخدم" }));

    await waitFor(() => expect(usersApiMock.create).toHaveBeenCalled());
    expect(screen.getByLabelText("البريد الإلكتروني")).toHaveValue("failed@example.com");
    expect(screen.getByLabelText("اسم المستخدم")).toHaveValue("failed.user");
    expect(screen.getByLabelText("رقم الجوال")).toHaveValue("0551234567");
    expect(screen.getByRole("alert")).toHaveTextContent("تعذر تحميل المستخدمين");
  });

  it("gates lifecycle controls by capability and refreshes after a transition", async () => {
    const user = userEvent.setup();
    usersApiMock.transition.mockResolvedValue({ ...USERS[0], status: "SUSPENDED" });

    render(<TenantUsersPage />);
    const row = within((await screen.findByText("سالم العتيبي")).closest("tr") as HTMLElement);
    await user.click(row.getByRole("button", { name: "إيقاف مؤقت" }));

    await waitFor(() => expect(usersApiMock.transition).toHaveBeenCalledWith(
      TENANT_ID,
      USERS[0].id,
      "suspend",
    ));
    await waitFor(() => expect(usersApiMock.list).toHaveBeenCalledTimes(2));

    cleanup();
    authMock.capabilities = ["USER.READ"];
    render(<TenantUsersPage />);
    await screen.findByText("سالم العتيبي");
    expect(screen.queryByRole("button", { name: "إيقاف مؤقت" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "أرشفة" })).not.toBeInTheDocument();
  });
});
