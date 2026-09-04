// @vitest-environment jsdom

/**
 * WS5 Task 9 — Employee 360 permission and lifecycle contract tests.
 *
 * Pins the capability-driven tab matrix (UX-only — backend 403 stays
 * authoritative), the audited PII read path, the compensation fetch gate,
 * lifecycle action semantics (effective date + expectedVersion +
 * Idempotency-Key, termination never labeled delete), and the compliance
 * badge rendering.
 */
import "@testing-library/jest-dom/vitest";

import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const { hrmV2ApiMock, authMock } = vi.hoisted(() => ({
  hrmV2ApiMock: {
    getEmployment: vi.fn(),
    getPerson: vi.fn(),
    listAssignments: vi.fn(),
    listContracts: vi.fn(),
    listCompensationPackages: vi.fn(),
    getPersonPrivate: vi.fn(),
    listAudit: vi.fn(),
    getComplianceContext: vi.fn(),
    employmentLifecycle: vi.fn(),
  },
  authMock: { state: "AUTHENTICATED", capabilities: [] as string[] },
}));

vi.mock("@/lib/api/hr-v2-api", () => ({
  hrmV2Api: hrmV2ApiMock,
  newIdempotencyKey: vi.fn(() => "generated-key-1"),
  // Mirror the real parser contract: parse structured HRM_* envelopes into a
  // plain {code, status, violations, requestId} object (like the real one).
  parseHrmV2Error: vi.fn((err: unknown) => {
    const details = (err as { details?: { status?: number; body?: { code?: unknown; message?: unknown; violations?: unknown } } })?.details;
    const code = details?.body?.code;
    if (typeof code === "string" && code.startsWith("HRM_")) {
      return Object.assign(new Error(typeof details?.body?.message === "string" ? details.body.message : code), {
        code,
        status: typeof details?.status === "number" ? details.status : 0,
        violations: Array.isArray(details?.body?.violations) ? details?.body?.violations : null,
        requestId: null,
      });
    }
    return null;
  }),
  HrmV2ApiError: class HrmV2ApiError extends Error {},
}));

vi.mock("@/lib/auth/auth-provider", () => ({
  useAuth: () => ({ state: authMock.state, me: { capabilities: authMock.capabilities } }),
}));

vi.mock("next/navigation", () => ({
  useParams: vi.fn(() => ({ employmentId: "e-1" })),
  useRouter: vi.fn(() => ({ replace: vi.fn(), push: vi.fn() })),
  usePathname: () => "/hr/employees/e-1",
}));

import Employee360Page from "./page";

const EMPLOYMENT = {
  employmentId: "e-1",
  personId: "p-1",
  legalEntityId: "le-1",
  employeeNumber: "EMP-0001",
  workerClassificationCode: "FULL_TIME",
  currentStatus: "ACTIVE",
  employmentStartDate: "2025-01-01",
  terminationDate: null,
  rehireOfEmployeeId: null,
  version: 7,
};

const PERSON = {
  personId: "p-1",
  userId: "u-1",
  firstName: "سالم",
  middleName: null,
  lastName: "العتيبي",
  displayName: "سالم العتيبي",
  version: 5,
};

beforeEach(() => {
  for (const fn of Object.values(hrmV2ApiMock)) fn.mockReset();
  authMock.state = "AUTHENTICATED";
  hrmV2ApiMock.getEmployment.mockResolvedValue(EMPLOYMENT);
  hrmV2ApiMock.getPerson.mockResolvedValue(PERSON);
  hrmV2ApiMock.listAssignments.mockResolvedValue([]);
  hrmV2ApiMock.listContracts.mockResolvedValue([]);
  hrmV2ApiMock.getComplianceContext.mockResolvedValue({
    laborJurisdiction: "SA", mode: "GLOBAL_MODE", packCode: null, packVersion: null,
    workerClassification: "FULL_TIME", effectiveDate: "2026-01-01",
  });
  hrmV2ApiMock.listCompensationPackages.mockResolvedValue([]);
  hrmV2ApiMock.getPersonPrivate.mockResolvedValue({
    personId: "p-1", dateOfBirth: "1990-05-05", nationalityCountryCode: "SA",
    maritalStatus: "MARRIED", version: 2,
  });
  hrmV2ApiMock.listAudit.mockResolvedValue([]);
});

afterEach(() => cleanup());

async function render360(capabilities: string[]) {
  authMock.capabilities = capabilities;
  render(<Employee360Page />);
  await screen.findByRole("heading", { name: "سالم العتيبي" });
}

describe("Employee 360 — capability matrix (UX-only)", () => {
  it("shows overview and employment tabs for EMPLOYEE_VIEW and hides PII/compensation/audit", async () => {
    await render360(["HRM.EMPLOYEE.VIEW"]);

    expect(screen.getByRole("tab", { name: "نظرة عامة" })).toBeInTheDocument();
    expect(screen.getByRole("tab", { name: "التوظيف" })).toBeInTheDocument();
    expect(screen.getByRole("tab", { name: "الإسنادات" })).toBeInTheDocument();
    expect(screen.getByRole("tab", { name: "الالتزام" })).toBeInTheDocument();
    expect(screen.queryByRole("tab", { name: "المعلومات الخاصة" })).not.toBeInTheDocument();
    expect(screen.queryByRole("tab", { name: "التعويضات" })).not.toBeInTheDocument();
    expect(screen.queryByRole("tab", { name: "التدقيق" })).not.toBeInTheDocument();
  });

  it("reveals the private tab only with HRM.PII.VIEW and fetches the audited read on open", async () => {
    const user = userEvent.setup();
    await render360(["HRM.EMPLOYEE.VIEW", "HRM.PII.VIEW"]);

    const tab = screen.getByRole("tab", { name: "المعلومات الخاصة" });
    expect(hrmV2ApiMock.getPersonPrivate).not.toHaveBeenCalled();
    await user.click(tab);
    await waitFor(() => expect(hrmV2ApiMock.getPersonPrivate).toHaveBeenCalledWith("p-1"));
  });

  it("reveals the compensation tab only with HRM.COMPENSATION.VIEW and fetches scoped packages", async () => {
    const user = userEvent.setup();
    await render360(["HRM.EMPLOYEE.VIEW", "HRM.COMPENSATION.VIEW"]);

    const tab = screen.getByRole("tab", { name: "التعويضات" });
    expect(hrmV2ApiMock.listCompensationPackages).not.toHaveBeenCalled();
    await user.click(tab);
    await waitFor(() => expect(hrmV2ApiMock.listCompensationPackages).toHaveBeenCalledWith("e-1"));
  });

  it("reveals the audit tab only with HRM.AUDIT.VIEW", async () => {
    await render360(["HRM.EMPLOYEE.VIEW", "HRM.AUDIT.VIEW"]);
    expect(screen.getByRole("tab", { name: "التدقيق" })).toBeInTheDocument();
  });

  it("never fetches compensation or private data for users without the capability", async () => {
    await render360(["HRM.EMPLOYEE.VIEW"]);
    // User clicks around the permitted tabs only.
    expect(hrmV2ApiMock.listCompensationPackages).not.toHaveBeenCalled();
    expect(hrmV2ApiMock.getPersonPrivate).not.toHaveBeenCalled();
  });
});

describe("Employee 360 — compliance status", () => {
  it("renders Global Mode as a warning badge, never compliant", async () => {
    await render360(["HRM.EMPLOYEE.VIEW"]);
    const badge = await screen.findByRole("status");
    expect(badge).toHaveTextContent("الالتزام المحلي غير معتمد");
  });
});

describe("Employee 360 — lifecycle actions", () => {
  it("offers status-appropriate commands and labels termination as إنهاء خدمة, never حذف", async () => {
    await render360(["HRM.EMPLOYEE.VIEW", "HRM.EMPLOYEE.TERMINATE", "HRM.EMPLOYEE.UPDATE"]);
    await userEvent.setup().click(screen.getByRole("tab", { name: "التوظيف" }));

    expect(await screen.findByRole("button", { name: "بدء إجازة" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "إيقاف مؤقت" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "إنهاء خدمة" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "حذف" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /حذف/ })).not.toBeInTheDocument();
  });

  it("hides lifecycle commands without the update/terminate capabilities", async () => {
    await render360(["HRM.EMPLOYEE.VIEW"]);
    await userEvent.setup().click(screen.getByRole("tab", { name: "التوظيف" }));
    await waitFor(() => expect(screen.getByText("سجل التوظيف")).toBeInTheDocument());
    expect(screen.queryByRole("button", { name: "إنهاء خدمة" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "بدء إجازة" })).not.toBeInTheDocument();
  });

  it("sends effectiveDate + expectedVersion + generated Idempotency-Key on confirmed activation", async () => {
    hrmV2ApiMock.getEmployment.mockResolvedValue({ ...EMPLOYMENT, currentStatus: "ONBOARDING", version: 4 });
    const user = userEvent.setup();
    await render360(["HRM.EMPLOYEE.VIEW", "HRM.EMPLOYEE.UPDATE"]);
    await user.click(screen.getByRole("tab", { name: "التوظيف" }));

    await user.click(await screen.findByRole("button", { name: "تنشيط" }));
    const dialog = screen.getByRole("dialog");
    expect(dialog).toBeInTheDocument();
    // The dialog defaults the effective date to today (ISO shape verified in
    // the call assertion below); the operator may adjust it before confirming.
    expect(within(dialog).getByLabelText("التاريخ الفعلي")).toBeInTheDocument();
    await user.click(within_dialog_confirm(dialog));

    await waitFor(() =>
      expect(hrmV2ApiMock.employmentLifecycle).toHaveBeenCalledWith(
        "e-1",
        "activate",
        expect.objectContaining({ expectedVersion: 4 }),
        "generated-key-1",
      ),
    );
    const call = hrmV2ApiMock.employmentLifecycle.mock.calls[0][2];
    expect(call.effectiveDate).toMatch(/^\d{4}-\d{2}-\d{2}$/);
  });

  it("surfaces the stale-version conflict safely (409) without overwriting", async () => {
    hrmV2ApiMock.getEmployment.mockResolvedValue({ ...EMPLOYMENT, currentStatus: "ONBOARDING", version: 4 });
    hrmV2ApiMock.employmentLifecycle.mockRejectedValue({
      details: { status: 409, body: { code: "HRM_CONCURRENCY_CONFLICT", message: "stale" } },
    });
    const user = userEvent.setup();
    await render360(["HRM.EMPLOYEE.VIEW", "HRM.EMPLOYEE.UPDATE"]);
    await user.click(screen.getByRole("tab", { name: "التوظيف" }));
    await user.click(await screen.findByRole("button", { name: "تنشيط" }));
    await user.click(within_dialog_confirm(screen.getByRole("dialog")));

    expect(await screen.findByRole("alert")).toHaveTextContent("مستخدم آخر");
    // The conflict message must ask for refresh, not claim success.
    expect(screen.queryByText("تم تنفيذ العملية بنجاح")).not.toBeInTheDocument();
  });
});

// Helpers to scope queries inside the dialog (kept local to avoid extra imports).
import { within } from "@testing-library/react";
function within_dialog_confirm(dialog: HTMLElement): HTMLElement {
  return within(dialog).getByRole("button", { name: "تأكيد" });
}
