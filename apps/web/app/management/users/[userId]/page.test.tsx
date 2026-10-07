// @vitest-environment jsdom
import "@testing-library/jest-dom/vitest";

import { cleanup, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const { TENANT_ID, USER_ID, ROLE_ID, GRANT_ID, ORGANIZATION_ID, OTHER_TENANT_ID, usersApiMock, tenantAccessApiMock, credentialApiMock, accessApiMock, userFacingErrorMock, authMock, translate } = vi.hoisted(() => {
  const TENANT_ID = "11111111-1111-1111-1111-111111111111";
  const USER_ID = "22222222-2222-2222-2222-222222222222";
  const ROLE_ID = "33333333-3333-3333-3333-333333333333";
  const GRANT_ID = "44444444-4444-4444-4444-444444444444";
  const ORGANIZATION_ID = "66666666-6666-6666-6666-666666666666";
  const OTHER_TENANT_ID = "77777777-7777-7777-7777-777777777777";
  const messages: Record<string, string> = {
    "users.email": "البريد الإلكتروني",
    "users.displayName": "الاسم المعروض",
    "users.mobileNumber": "رقم الجوال",
    "users.mobileRegion": "رمز المنطقة",
    "users.forbidden": "لا تملك صلاحية عرض المستخدمين",
    "users.activate": "تفعيل",
    "users.deactivate": "تعطيل",
    "users.suspend": "إيقاف مؤقت",
    "users.archive": "أرشفة",
    "users.actions": "الإجراءات",
    "users.status": "الحالة",
    "users.status.ACTIVE": "نشط",
    "users.status.INACTIVE": "غير نشط",
    "users.status.INVITED": "مدعو",
    "users.status.SUSPENDED": "موقوف",
    "users.status.ARCHIVED": "مؤرشف",
    "users.close": "إغلاق",
    "management.users.detail.title": "تفاصيل المستخدم",
    "management.users.detail.save": "حفظ التعديلات",
    "management.users.detail.memberships": "عضويات المؤسسات",
    "management.users.detail.roles": "الأدوار المسندة",
    "management.users.detail.role": "الدور",
    "management.users.detail.grant": "إسناد الدور",
    "management.users.detail.revoke": "سحب الدور",
    "management.users.detail.revokeConfirm": "تأكيد السحب",
    "management.users.detail.revokeConfirmTitle": "تأكيد سحب الدور",
    "management.users.detail.revokeConfirmMessage": "هل تريد سحب هذا الدور من المستخدم؟",
    "management.users.detail.revokeConfirmApply": "نعم، سحب الدور",
    "management.users.detail.revokeConfirmCancel": "إلغاء",
    "management.users.detail.scope": "النطاق",
    "management.users.detail.scopeTenant": "نطاق المستأجر",
    "management.users.detail.scopeOrganization": "نطاق مؤسسة",
    "management.users.detail.scopeOrganizationPlaceholder": "اختر المؤسسة",
    "management.users.detail.noMemberships": "لا توجد عضويات",
    "management.users.detail.noRoles": "لا توجد أدوار مسندة",
    "management.users.detail.loading": "جارٍ تحميل بيانات المستخدم",
    "management.users.detail.error": "تعذر تحميل بيانات المستخدم",
    "management.users.detail.effectiveAccess": "الصلاحيات الفعّالة",
    "management.users.detail.effectiveAccessEmpty": "لا توجد صلاحيات فعّالة",
    "management.users.detail.effectiveAccessCount": "عدد الصلاحيات الفعّالة",
    "management.users.detail.scopeTenantLabel": "على مستوى المستأجر",
    "management.users.detail.scopeOrganizationLabel": "على مستوى المؤسسة",
    "users.username": "اسم المستخدم",
    "management.users.credentials.title": "بيانات الدخول",
    "management.users.credentials.reset": "إرسال رابط تعيين كلمة المرور",
    "management.users.credentials.resetSuccess": "تم إرسال رابط تعيين كلمة المرور",
    "management.users.credentials.help": "لا يتم عرض أو تخزين كلمة المرور الحالية في الواجهة",
    "management.users.credentials.initialized": "بيانات الدخول مهيأة",
    "management.users.credentials.rotation": "يتطلب تغيير كلمة المرور",
    "management.users.credentials.lastLogin": "آخر تسجيل دخول",
    "management.users.credentials.yes": "نعم",
    "management.users.credentials.no": "لا",
    "management.users.credentials.never": "لم يسجل الدخول بعد",
    "management.users.credentials.initialize": "تهيئة بيانات الدخول الأولى",
    "management.users.credentials.initial": "كلمة المرور المؤقتة",
    "management.users.credentials.confirm": "تأكيد كلمة المرور المؤقتة",
    "management.users.credentials.initializeSuccess": "تمت تهيئة بيانات الدخول",
    "management.users.credentials.mismatch": "كلمتا المرور غير متطابقتين",
  };
  return {
    TENANT_ID, USER_ID, ROLE_ID, GRANT_ID, ORGANIZATION_ID, OTHER_TENANT_ID,
    translate: (key: string) => messages[key] ?? key,
    usersApiMock: { get: vi.fn(), update: vi.fn(), transition: vi.fn() },
    credentialApiMock: { adminInitializeCredential: vi.fn(), adminResetPassword: vi.fn() },
    tenantAccessApiMock: { listUserMemberships: vi.fn(), listUserRoleLinks: vi.fn(), listRoles: vi.fn(), grantUserRole: vi.fn(), revokeUserRole: vi.fn() },
    accessApiMock: { effectivePermissions: vi.fn(), resync: vi.fn() },
    userFacingErrorMock: { toUserFacingMessage: vi.fn(), toUserFacingError: vi.fn() },
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
vi.mock("@/lib/api/access-api", () => ({ effectivePermissions: accessApiMock.effectivePermissions, resync: accessApiMock.resync }));
vi.mock("@/lib/auth/auth-provider", () => ({ useAuth: () => ({ state: authMock.state, user: authMock.user, me: { capabilities: authMock.capabilities } }) }));
vi.mock("@/components/shell", () => ({ ExecutiveShell: ({ children }: { children: React.ReactNode }) => <>{children}</> }));
vi.mock("next/navigation", () => ({ useParams: () => ({ userId: USER_ID }), useRouter: () => ({ replace: vi.fn(), push: vi.fn() }) }));
vi.mock("@/lib/i18n/I18nProvider", () => ({ useI18n: () => ({ t: translate }) }));
vi.mock("@/lib/api/user-facing-errors", () => ({
  toUserFacingMessage: (err: unknown) => userFacingErrorMock.toUserFacingMessage(err),
  toUserFacingError: (err: unknown) => userFacingErrorMock.toUserFacingError(err),
}));

import TenantUserDetailPage from "./page";

const USER = { id: USER_ID, tenantId: TENANT_ID, email: "salem@example.com", username: "salem", displayName: "سالم العتيبي", mobileNumber: "+966500000000", mobileRegion: "SA", status: "ACTIVE", lastLoginAt: "2026-10-01T10:00:00Z", credentialInitialized: true, credentialRotationRequired: false, createdAt: "2026-01-01T00:00:00Z", updatedAt: "2026-01-01T00:00:00Z" };

beforeEach(() => {
  usersApiMock.get.mockReset(); usersApiMock.update.mockReset(); usersApiMock.transition.mockReset(); credentialApiMock.adminInitializeCredential.mockReset(); credentialApiMock.adminResetPassword.mockReset();
  tenantAccessApiMock.listUserMemberships.mockReset(); tenantAccessApiMock.listUserRoleLinks.mockReset(); tenantAccessApiMock.listRoles.mockReset(); tenantAccessApiMock.grantUserRole.mockReset(); tenantAccessApiMock.revokeUserRole.mockReset();
  accessApiMock.effectivePermissions.mockReset(); accessApiMock.resync.mockReset();
  userFacingErrorMock.toUserFacingMessage.mockReset(); userFacingErrorMock.toUserFacingError.mockReset();
  usersApiMock.get.mockResolvedValue(USER); usersApiMock.update.mockResolvedValue(USER); usersApiMock.transition.mockResolvedValue(USER); credentialApiMock.adminInitializeCredential.mockResolvedValue(undefined); credentialApiMock.adminResetPassword.mockResolvedValue({ message: "تم إرسال رابط أحادي الاستخدام لإعداد كلمة مرور جديدة." });
  tenantAccessApiMock.listUserMemberships.mockResolvedValue([{ id: "55555555-5555-5555-5555-555555555555", tenantId: TENANT_ID, organizationId: ORGANIZATION_ID, userId: USER_ID, email: USER.email, displayName: USER.displayName, status: "ACTIVE", createdAt: USER.createdAt, updatedAt: USER.updatedAt }]);
  tenantAccessApiMock.listUserRoleLinks.mockResolvedValue([{ id: GRANT_ID, tenantId: TENANT_ID, userId: USER_ID, roleId: ROLE_ID, roleCode: "TENANT_ADMIN", organizationId: null, status: "ACTIVE", createdAt: USER.createdAt, updatedAt: USER.updatedAt }]);
  tenantAccessApiMock.listRoles.mockResolvedValue([{ id: ROLE_ID, tenantId: TENANT_ID, code: "TENANT_ADMIN", name: "Tenant Admin", description: null, status: "ACTIVE", createdAt: USER.createdAt, updatedAt: USER.updatedAt }]);
  accessApiMock.effectivePermissions.mockResolvedValue([{ capabilityId: "cap-1", scopeType: "TENANT", scopeReference: null, source: "ROLE", matchedRoleId: ROLE_ID, authorizationVersion: 1, computedAt: "2026-10-01T00:00:00Z" }]);
  accessApiMock.resync.mockResolvedValue([]);
  userFacingErrorMock.toUserFacingMessage.mockImplementation(() => "تعذر تحميل بيانات المستخدم");
  userFacingErrorMock.toUserFacingError.mockImplementation(() => ({ title: "خطأ", message: "تعذر تحميل بيانات المستخدم", kind: "unknown" as const }));
  authMock.state = "AUTHENTICATED";
  authMock.user = { id: "actor-1", tenantId: TENANT_ID, email: "admin@example.com", displayName: "Admin", status: "ACTIVE" };
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

  it("fails closed without USER.READ and does not fetch user data", async () => {
    authMock.capabilities = ["USER.WRITE", "USER.DELETE"];
    render(<TenantUserDetailPage />);
    expect(await screen.findByRole("alert")).toHaveTextContent("لا تملك صلاحية عرض المستخدمين");
    expect(usersApiMock.get).not.toHaveBeenCalled();
    expect(screen.queryByTestId("management-user-identity")).not.toBeInTheDocument();
  });

  it("executes lifecycle transitions from detail in the authenticated tenant", async () => {
    const user = userEvent.setup();
    usersApiMock.transition.mockResolvedValue({ ...USER, status: "SUSPENDED" });
    render(<TenantUserDetailPage />);
    expect(await screen.findByText("سالم العتيبي")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "إيقاف مؤقت" }));
    await waitFor(() => expect(usersApiMock.transition).toHaveBeenCalledWith(TENANT_ID, USER_ID, "suspend"));
    await waitFor(() => expect(usersApiMock.get).toHaveBeenCalledTimes(2));
  });

  it("gates archive independently from write lifecycle mutations", async () => {
    authMock.capabilities = ["USER.READ", "USER.WRITE"];
    render(<TenantUserDetailPage />);
    expect(await screen.findByText("سالم العتيبي")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "إيقاف مؤقت" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "أرشفة" })).not.toBeInTheDocument();
  });

  it("clears prior tenant data while a new tenant-scoped request is pending", async () => {
    const { rerender } = render(<TenantUserDetailPage />);
    expect(await screen.findByText("سالم العتيبي")).toBeInTheDocument();

    usersApiMock.get.mockImplementationOnce(() => new Promise(() => undefined));
    authMock.user = { ...authMock.user, tenantId: OTHER_TENANT_ID };
    rerender(<TenantUserDetailPage />);

    await waitFor(() => {
      expect(screen.queryByText("سالم العتيبي")).not.toBeInTheDocument();
      expect(screen.getByRole("status")).toHaveTextContent("جارٍ تحميل بيانات المستخدم");
    });
  });

  it("does not misrepresent unread memberships or roles as empty data", async () => {
    authMock.capabilities = ["USER.READ"];
    render(<TenantUserDetailPage />);
    expect(await screen.findByText("سالم العتيبي")).toBeInTheDocument();
    expect(screen.queryByTestId("management-user-memberships")).not.toBeInTheDocument();
    expect(screen.queryByTestId("management-user-roles")).not.toBeInTheDocument();
    expect(screen.queryByTestId("management-user-effective-access")).not.toBeInTheDocument();
    expect(tenantAccessApiMock.listUserMemberships).not.toHaveBeenCalled();
    expect(tenantAccessApiMock.listUserRoleLinks).not.toHaveBeenCalled();
    expect(accessApiMock.effectivePermissions).not.toHaveBeenCalled();
  });

  it("ignores a late response from a previous tenant scope", async () => {
    const { rerender } = render(<TenantUserDetailPage />);
    expect(await screen.findByText("سالم العتيبي")).toBeInTheDocument();

    let resolveOther!: (value: typeof USER) => void;
    usersApiMock.get.mockImplementationOnce(() => new Promise((resolve) => { resolveOther = resolve; }));
    authMock.user = { ...authMock.user, tenantId: OTHER_TENANT_ID };
    rerender(<TenantUserDetailPage />);
    await waitFor(() => expect(screen.getByRole("status")).toHaveTextContent("جارٍ تحميل بيانات المستخدم"));

    usersApiMock.get.mockResolvedValueOnce(USER);
    authMock.user = { ...authMock.user, tenantId: TENANT_ID };
    rerender(<TenantUserDetailPage />);
    expect(await screen.findByText("سالم العتيبي")).toBeInTheDocument();

    resolveOther({ ...USER, tenantId: OTHER_TENANT_ID, email: "other@example.com", displayName: "مستخدم مستأجر آخر" });
    await waitFor(() => expect(screen.queryByText("مستخدم مستأجر آخر")).not.toBeInTheDocument());
    expect(screen.getByText("سالم العتيبي")).toBeInTheDocument();
  });

  it("updates identity only inside the authenticated tenant", async () => {
    const user = userEvent.setup(); render(<TenantUserDetailPage />); await screen.findByText("سالم العتيبي");
    await user.clear(screen.getByLabelText("البريد الإلكتروني")); await user.type(screen.getByLabelText("البريد الإلكتروني"), "updated@example.com"); await user.click(screen.getByRole("button", { name: "حفظ التعديلات" }));
    await waitFor(() => expect(usersApiMock.update).toHaveBeenCalledWith(TENANT_ID, USER_ID, { email: "updated@example.com", username: "salem", displayName: "سالم العتيبي", mobileNumber: "+966500000000", mobileRegion: "SA" }));
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

  it("initializes the first credential only for an eligible passwordless active user", async () => {
    const user = userEvent.setup();
    usersApiMock.get.mockResolvedValue({ ...USER, credentialInitialized: false, credentialRotationRequired: false, lastLoginAt: null });
    render(<TenantUserDetailPage />);
    expect(await screen.findByText("سالم العتيبي")).toBeInTheDocument();
    await user.type(screen.getByLabelText("كلمة المرور المؤقتة"), "TempPass123!");
    await user.type(screen.getByLabelText("تأكيد كلمة المرور المؤقتة"), "TempPass123!");
    await user.click(screen.getByRole("button", { name: "تهيئة بيانات الدخول الأولى" }));
    await waitFor(() => expect(credentialApiMock.adminInitializeCredential).toHaveBeenCalledWith(USER_ID, { initialCredential: "TempPass123!" }));
    expect(screen.getByRole("status")).toHaveTextContent("تمت تهيئة بيانات الدخول");
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
    await user.click(screen.getByRole("button", { name: "سحب الدور" }));
    await screen.findByRole("dialog", { name: "تأكيد سحب الدور" });
    await user.click(screen.getByRole("button", { name: "نعم، سحب الدور" }));
    await waitFor(() => expect(tenantAccessApiMock.revokeUserRole).toHaveBeenCalledWith(TENANT_ID, GRANT_ID));
  });

  it("fails closed by hiding mutations when capabilities are absent", async () => {
    authMock.capabilities = ["USER.READ", "MEMBERSHIP.READ", "ROLE.READ"]; render(<TenantUserDetailPage />);
    expect(await screen.findByText("سالم العتيبي")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "حفظ التعديلات" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "إسناد الدور" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "سحب الدور" })).not.toBeInTheDocument();
  });
});

describe("Tenant User Detail — Phase 6: Role and scope mutation UX", () => {
  it("exposes a scope selector with tenant-wide and per-organization options before grant", async () => {
    render(<TenantUserDetailPage />);
    await screen.findByText("سالم العتيبي");
    // Scope selector must exist and offer both tenant-wide and organization choices.
    const scopeSelector = screen.getByLabelText("النطاق");
    expect(scopeSelector).toBeInTheDocument();
    const options = within(scopeSelector).getAllByRole("option");
    const optionTexts = options.map((o) => o.textContent ?? "");
    expect(optionTexts).toContain("نطاق المستأجر");
    expect(optionTexts).toContain("نطاق مؤسسة");
  });

  it("defaults the scope to tenant-wide and grants without organizationId", async () => {
    const user = userEvent.setup();
    tenantAccessApiMock.grantUserRole.mockResolvedValue({ id: GRANT_ID });
    render(<TenantUserDetailPage />);
    await screen.findByText("سالم العتيبي");
    await user.selectOptions(screen.getByLabelText("الدور"), ROLE_ID);
    await user.click(screen.getByRole("button", { name: "إسناد الدور" }));
    await waitFor(() => expect(tenantAccessApiMock.grantUserRole).toHaveBeenCalledWith(TENANT_ID, USER_ID, ROLE_ID, undefined));
  });

  it("grants with the selected organizationId when organization scope is chosen", async () => {
    const user = userEvent.setup();
    tenantAccessApiMock.grantUserRole.mockResolvedValue({ id: GRANT_ID });
    render(<TenantUserDetailPage />);
    await screen.findByText("سالم العتيبي");
    await user.selectOptions(screen.getByLabelText("الدور"), ROLE_ID);
    await user.selectOptions(screen.getByLabelText("النطاق"), "ORGANIZATION");
    // Organization picker must appear and offer the user's existing memberships.
    const orgPicker = await screen.findByLabelText("اختر المؤسسة");
    await user.selectOptions(orgPicker, ORGANIZATION_ID);
    await user.click(screen.getByRole("button", { name: "إسناد الدور" }));
    await waitFor(() => expect(tenantAccessApiMock.grantUserRole).toHaveBeenCalledWith(TENANT_ID, USER_ID, ROLE_ID, ORGANIZATION_ID));
  });

  it("shows whether each existing role link is tenant-wide or organization-scoped", async () => {
    tenantAccessApiMock.listUserRoleLinks.mockResolvedValue([
      { id: GRANT_ID, tenantId: TENANT_ID, userId: USER_ID, roleId: ROLE_ID, roleCode: "TENANT_ADMIN", organizationId: null, status: "ACTIVE", createdAt: "2026-01-01T00:00:00Z", updatedAt: "2026-01-01T00:00:00Z" },
      { id: "44444444-4444-4444-4444-444444444445", tenantId: TENANT_ID, userId: USER_ID, roleId: "33333333-3333-3333-3333-333333333334", roleCode: "ORG_ADMIN", organizationId: ORGANIZATION_ID, status: "ACTIVE", createdAt: "2026-01-01T00:00:00Z", updatedAt: "2026-01-01T00:00:00Z" },
    ]);
    render(<TenantUserDetailPage />);
    await screen.findByText("سالم العتيبي");
    // Scope assertions to the role-links list (<ul>) so the role selector
    // dropdown options do not produce ambiguous matches.
    const rolesHeading = screen.getByRole("heading", { name: "الأدوار المسندة" });
    const rolesSection = rolesHeading.closest("section") as HTMLElement;
    const roleList = within(rolesSection).getByRole("list");
    expect(within(roleList).getByText("TENANT_ADMIN")).toBeInTheDocument();
    expect(within(roleList).getByText("على مستوى المستأجر")).toBeInTheDocument();
    expect(within(roleList).getByText("ORG_ADMIN")).toBeInTheDocument();
    expect(within(roleList).getByText("على مستوى المؤسسة")).toBeInTheDocument();
  });

  it("refreshes backend-authoritative effective permissions after grant", async () => {
    const user = userEvent.setup();
    tenantAccessApiMock.grantUserRole.mockResolvedValue({ id: GRANT_ID });
    accessApiMock.effectivePermissions.mockClear();
    render(<TenantUserDetailPage />);
    await screen.findByText("سالم العتيبي");
    // Initial load calls effectivePermissions once.
    await waitFor(() => expect(accessApiMock.effectivePermissions).toHaveBeenCalledWith(USER_ID));
    accessApiMock.effectivePermissions.mockClear();
    await user.selectOptions(screen.getByLabelText("الدور"), ROLE_ID);
    await user.click(screen.getByRole("button", { name: "إسناد الدور" }));
    // After grant, effectivePermissions must be re-fetched from the backend.
    await waitFor(() => expect(accessApiMock.effectivePermissions).toHaveBeenCalledWith(USER_ID));
  });

  it("refreshes backend-authoritative effective permissions after revoke", async () => {
    const user = userEvent.setup();
    tenantAccessApiMock.revokeUserRole.mockResolvedValue({ id: GRANT_ID, status: "REVOKED" });
    accessApiMock.effectivePermissions.mockClear();
    render(<TenantUserDetailPage />);
    await screen.findByRole("button", { name: "سحب الدور" });
    accessApiMock.effectivePermissions.mockClear();
    await user.click(screen.getByRole("button", { name: "سحب الدور" }));
    await screen.findByRole("dialog", { name: "تأكيد سحب الدور" });
    await user.click(screen.getByRole("button", { name: "نعم، سحب الدور" }));
    await waitFor(() => expect(accessApiMock.effectivePermissions).toHaveBeenCalledWith(USER_ID));
  });

  it("requires an explicit confirmation step before revoking a role", async () => {
    const user = userEvent.setup();
    tenantAccessApiMock.revokeUserRole.mockResolvedValue({ id: GRANT_ID, status: "REVOKED" });
    render(<TenantUserDetailPage />);
    await screen.findByRole("button", { name: "سحب الدور" });
    expect(screen.queryByRole("dialog", { name: "تأكيد سحب الدور" })).not.toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "سحب الدور" }));
    // Confirmation dialog must appear and the revoke must NOT have been called yet.
    await screen.findByRole("dialog", { name: "تأكيد سحب الدور" });
    expect(tenantAccessApiMock.revokeUserRole).not.toHaveBeenCalled();
    // Cancelling dismisses the dialog without revoking.
    await user.click(screen.getByRole("button", { name: "إلغاء" }));
    await waitFor(() => expect(screen.queryByRole("dialog", { name: "تأكيد سحب الدور" })).not.toBeInTheDocument());
    expect(tenantAccessApiMock.revokeUserRole).not.toHaveBeenCalled();
  });

  it("renders a stable 403 title and message when grant is denied", async () => {
    const user = userEvent.setup();
    const denied = new Error("forbidden");
    tenantAccessApiMock.grantUserRole.mockRejectedValueOnce(denied);
    userFacingErrorMock.toUserFacingError.mockReturnValueOnce({
      title: "الوصول مرفوض",
      message: "لا تملك الصلاحية المطلوبة لتنفيذ هذه العملية.",
      kind: "validation",
    });
    userFacingErrorMock.toUserFacingMessage.mockReturnValueOnce("لا تملك الصلاحية المطلوبة لتنفيذ هذه العملية.");
    render(<TenantUserDetailPage />);
    await screen.findByText("سالم العتيبي");
    await user.selectOptions(screen.getByLabelText("الدور"), ROLE_ID);
    await user.click(screen.getByRole("button", { name: "إسناد الدور" }));
    await waitFor(() => expect(screen.getByRole("alert")).toBeInTheDocument());
    // The title must be rendered as a distinct heading inside the alert.
    const alert = screen.getByRole("alert");
    expect(within(alert).getByRole("heading", { name: "الوصول مرفوض" })).toBeInTheDocument();
    expect(alert).toHaveTextContent("لا تملك الصلاحية المطلوبة");
  });

  it("renders a stable 404 title and message when the target user is not found", async () => {
    const notFound = new Error("not found");
    usersApiMock.get.mockRejectedValueOnce(notFound);
    userFacingErrorMock.toUserFacingError.mockReturnValueOnce({
      title: "المورد غير موجود",
      message: "المورد المطلوب غير موجود أو لم يعد متاحًا.",
      kind: "not-found",
    });
    userFacingErrorMock.toUserFacingMessage.mockReturnValueOnce("المورد المطلوب غير موجود أو لم يعد متاحًا.");
    render(<TenantUserDetailPage />);
    await waitFor(() => expect(screen.getByRole("alert")).toBeInTheDocument());
    const alert = screen.getByRole("alert");
    // The title must be rendered as a distinct heading inside the alert.
    expect(within(alert).getByRole("heading", { name: "المورد غير موجود" })).toBeInTheDocument();
    expect(alert).toHaveTextContent("المورد المطلوب غير موجود");
  });

  it("renders a stable 409 title and message when grant conflicts with an existing grant", async () => {
    const user = userEvent.setup();
    const conflict = new Error("conflict");
    tenantAccessApiMock.grantUserRole.mockRejectedValueOnce(conflict);
    userFacingErrorMock.toUserFacingError.mockReturnValueOnce({
      title: "تعارض في البيانات",
      message: "تتعارض العملية مع بيانات موجودة حاليًا.",
      kind: "conflict",
    });
    userFacingErrorMock.toUserFacingMessage.mockReturnValueOnce("تتعارض العملية مع بيانات موجودة حاليًا.");
    render(<TenantUserDetailPage />);
    await screen.findByText("سالم العتيبي");
    await user.selectOptions(screen.getByLabelText("الدور"), ROLE_ID);
    await user.click(screen.getByRole("button", { name: "إسناد الدور" }));
    await waitFor(() => expect(screen.getByRole("alert")).toBeInTheDocument());
    const alert = screen.getByRole("alert");
    // The title must be rendered as a distinct heading inside the alert.
    expect(within(alert).getByRole("heading", { name: "تعارض في البيانات" })).toBeInTheDocument();
    expect(alert).toHaveTextContent("تتعارض العملية مع بيانات موجودة");
  });

  it("resets selected role and scope when the actor's tenant changes", async () => {
    const { rerender } = render(<TenantUserDetailPage />);
    await screen.findByText("سالم العتيبي");
    await userEvent.setup().selectOptions(screen.getByLabelText("الدور"), ROLE_ID);
    expect((screen.getByLabelText("الدور") as HTMLSelectElement).value).toBe(ROLE_ID);
    // Simulate tenant switch with a different canonical role set.
    const otherRoleId = "99999999-9999-4999-8999-999999999999";
    tenantAccessApiMock.listRoles.mockResolvedValueOnce([
      { id: otherRoleId, tenantId: OTHER_TENANT_ID, code: "OTHER_TENANT_ADMIN", name: "Other Tenant Admin", description: null, status: "ACTIVE", createdAt: USER.createdAt, updatedAt: USER.updatedAt },
    ]);
    authMock.user = { ...authMock.user, tenantId: OTHER_TENANT_ID };
    rerender(<TenantUserDetailPage />);
    await waitFor(() => expect(screen.getByLabelText("الدور")).toHaveValue(otherRoleId));
    expect(screen.getByLabelText("الدور")).not.toHaveValue(ROLE_ID);
  });

  it("does not render any application name in the mutation surface", async () => {
    render(<TenantUserDetailPage />);
    await screen.findByText("سالم العتيبي");
    // The mutation surface must not contain any application-name branching text.
    const mutationSurface = screen.getByTestId("management-user-detail-ready");
    const text = mutationSurface.textContent ?? "";
    for (const forbidden of ["CRM", "HRM", "Workflow", "ERP", "Finance", "Ecommerce", "POS"]) {
      expect(text).not.toContain(forbidden);
    }
  });
});

describe("Tenant User Detail — Phase 7: Effective access explanation", () => {
  const effectiveRowsBase = [
    {
      capabilityId: "11111111-2222-3333-4444-555555555555",
      scopeType: "TENANT_ALL",
      scopeReference: null,
      source: "ROLE",
      matchedRoleId: ROLE_ID,
      authorizationVersion: 4,
      computedAt: "2026-10-06T00:00:00Z",
    },
  ];

  it("renders the localized backend effect and canonical reason on role-derived rows", async () => {
    accessApiMock.effectivePermissions.mockResolvedValue([
      { ...effectiveRowsBase[0], effect: "ALLOW", reason: "ROLE_CAPABILITY_MATCH" },
    ]);
    render(<TenantUserDetailPage />);
    await screen.findByText("سالم العتيبي");
    const surface = await screen.findByTestId("management-user-effective-access");
    // Backend-authoritative effect and reason must be visible without any
    // frontend authorization computation.
    expect(surface).toHaveTextContent("سماح");
    expect(surface).toHaveTextContent("ROLE_CAPABILITY_MATCH");
  });

  it("renders an effective DENY row with its canonical reason from the backend", async () => {
    accessApiMock.effectivePermissions.mockResolvedValue([
      {
        ...effectiveRowsBase[0],
        effect: "DENY",
        source: "OVERRIDE",
        matchedRoleId: null,
        reason: "EXPLICIT_DIRECT_DENY",
      },
    ]);
    render(<TenantUserDetailPage />);
    await screen.findByText("سالم العتيبي");
    const surface = await screen.findByTestId("management-user-effective-access");
    expect(surface).toHaveTextContent("منع");
    expect(surface).toHaveTextContent("EXPLICIT_DIRECT_DENY");
    expect(surface).toHaveTextContent("استثناء مباشر");
  });

  it("renders role origin and break-glass provenance exactly as returned by the backend", async () => {
    accessApiMock.effectivePermissions.mockResolvedValue([
      { ...effectiveRowsBase[0], effect: "ALLOW", reason: "ROLE_CAPABILITY_MATCH" },
      {
        capabilityId: "99999999-8888-7777-6666-555555555555",
        scopeType: "TENANT_ALL",
        scopeReference: null,
        source: "BREAK_GLASS",
        matchedRoleId: null,
        authorizationVersion: 4,
        computedAt: "2026-10-06T00:00:00Z",
        effect: "ALLOW",
        reason: "EXPLICIT_ALLOW_MATCH",
      },
    ]);
    render(<TenantUserDetailPage />);
    await screen.findByText("سالم العتيبي");
    const surface = await screen.findByTestId("management-user-effective-access");
    expect(surface).toHaveTextContent(ROLE_ID);
    expect(surface).toHaveTextContent("وصول طارئ");
    expect(surface).toHaveTextContent("EXPLICIT_ALLOW_MATCH");
  });

  it("keeps the safe empty state when the backend returns no effective rows", async () => {
    accessApiMock.effectivePermissions.mockResolvedValue([]);
    render(<TenantUserDetailPage />);
    await screen.findByText("سالم العتيبي");
    expect(screen.getByTestId("management-user-effective-access")).toHaveTextContent(
      "لا توجد صلاحيات فعّالة",
    );
  });

  it("does not branch on application names anywhere in the effective access surface", async () => {
    accessApiMock.effectivePermissions.mockResolvedValue([
      { ...effectiveRowsBase[0], effect: "ALLOW", reason: "ROLE_CAPABILITY_MATCH" },
    ]);
    render(<TenantUserDetailPage />);
    await screen.findByText("سالم العتيبي");
    const surface = screen.getByTestId("management-user-effective-access");
    const text = surface.textContent ?? "";
    for (const forbidden of ["CRM", "HRM", "Workflow", "ERP", "Ecommerce", "POS"]) {
      expect(text).not.toContain(forbidden);
    }
  });
});
